package com.millodysingqueue.database

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseIntegrationTest {
    companion object {
        private val migrationPassword = UUID.randomUUID().toString()
        private val applicationPassword = UUID.randomUUID().toString()

        @Container
        @JvmField
        val postgres = PostgreSQLContainer(
            DockerImageName.parse(
                "postgres:17.11-bookworm@sha256:858bb5e98eed02eb8cafbcdc007f9a65d1b613972028944a8ec2133ccaf80d27",
            ).asCompatibleSubstituteFor("postgres"),
        )
            .withDatabaseName("millodysingqueue_test")
            .withUsername("postgres")
            .withPassword(UUID.randomUUID().toString())
            .withEnv("MIGRATION_DB_PASSWORD", migrationPassword)
            .withEnv("APPLICATION_DB_PASSWORD", applicationPassword)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("database-init/001-roles.sql"),
                "/docker-entrypoint-initdb.d/001-roles.sql",
            )
    }

    @BeforeAll
    fun migrateEmptyDatabase() {
        migrate("validate")
        val preview = ByteArrayOutputStream()
        migrate("update-sql", preview)
        assertContains(preview.toString(Charsets.UTF_8), "CREATE TABLE app.singers")
        asMigrator { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT to_regclass('app.singers')").use { result ->
                    assertTrue(result.next())
                    assertEquals(null, result.getString(1), "Preview must not apply application DDL")
                }
            }
        }
        migrate("update")
    }

    @BeforeEach
    fun clearSingers() {
        asMigrator { it.createStatement().use { statement -> statement.execute("TRUNCATE app.singers RESTART IDENTITY") } }
    }

    @Test
    fun `migrations rerun safely without changing their execution records`() {
        val before = changeRecords()
        assertEquals(1, before.size)
        migrate("update")
        migrate("validate")
        migrate("status")
        assertEquals(before, changeRecords())
    }

    @Test
    fun `application can insert duplicate names with generated IDs and timestamps`() {
        asApplication { connection ->
            val first = insertSinger(connection, "Mick", null, "Mick from work")
            val second = insertSinger(connection, "Mick")
            assertTrue(second > first)
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT nick_name, created_at FROM app.singers WHERE singer_id = $first").use { result ->
                    assertTrue(result.next())
                    assertEquals("Mick from work", result.getString("nick_name"))
                    assertNotNull(result.getObject("created_at", OffsetDateTime::class.java))
                }
            }
        }
    }

    @Test
    fun `missing or whitespace-only names are rejected`() {
        asApplication { connection ->
            for (name in listOf(null, "", "   ", "\t\n")) {
                val failure = assertFailsWith<SQLException> { insertSinger(connection, name) }
                assertTrue(failure.sqlState in setOf("23502", "23514"))
            }
        }
    }

    @Test
    fun `emails are unique ignoring case while multiple missing emails are allowed`() {
        asApplication { connection ->
            insertSinger(connection, "Mill", "Mill@example.com")
            for (email in listOf("Mill@example.com", "mill@EXAMPLE.COM")) {
                assertEquals("23505", assertFailsWith<SQLException> { insertSinger(connection, "Another", email) }.sqlState)
            }
            insertSinger(connection, "Mick")
            insertSinger(connection, "Sarah")
        }
    }

    @Test
    fun `blank or whitespace-containing emails are rejected`() {
        asApplication { connection ->
            for (email in listOf("", " ", " mill@example.com", "mill@example.com ", "\tmill@example.com", "mill@example.com\n")) {
                assertEquals("23514", assertFailsWith<SQLException> { insertSinger(connection, "Mill", email) }.sqlState)
            }
        }
    }

    @Test
    fun `application can update and delete singer data`() {
        asApplication { connection ->
            val id = insertSinger(connection, "Mick")
            connection.prepareStatement("UPDATE app.singers SET nick_name = ? WHERE singer_id = ?").use { statement ->
                statement.setString(1, "Mick from work")
                statement.setLong(2, id)
                assertEquals(1, statement.executeUpdate())
            }
            connection.prepareStatement("DELETE FROM app.singers WHERE singer_id = ?").use { statement ->
                statement.setLong(1, id)
                assertEquals(1, statement.executeUpdate())
            }
        }
    }

    @Test
    fun `application cannot change schemas create databases or read migration metadata`() {
        asApplication { connection ->
            for (sql in listOf(
                "CREATE TABLE app.forbidden (id bigint)",
                "CREATE TABLE public.forbidden (id bigint)",
                "CREATE SCHEMA forbidden",
                "CREATE DATABASE forbidden",
                "DROP TABLE app.singers",
                "ALTER TABLE app.singers ADD COLUMN forbidden text",
                "SELECT * FROM liquibase.databasechangelog",
            )) {
                assertEquals("42501", assertFailsWith<SQLException> {
                    connection.createStatement().use { it.execute(sql) }
                }.sqlState, sql)
            }
        }
    }

    private fun migrate(command: String, output: ByteArrayOutputStream = ByteArrayOutputStream()) =
        DatabaseMigrations.execute(command, postgres.jdbcUrl, "millody_migrator", migrationPassword, output)

    private fun changeRecords(): List<String> = asMigrator { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, author, md5sum, dateexecuted FROM liquibase.databasechangelog ORDER BY orderexecuted").use { result ->
                buildList {
                    while (result.next()) {
                        add((1..4).joinToString("|") { result.getString(it) })
                    }
                }
            }
        }
    }

    private fun insertSinger(connection: Connection, name: String?, email: String? = null, nickname: String? = null): Long =
        connection.prepareStatement(
            "INSERT INTO app.singers (singer_name, email, nick_name) VALUES (?, ?, ?) RETURNING singer_id",
        ).use { statement ->
            statement.setString(1, name)
            statement.setString(2, email)
            statement.setString(3, nickname)
            statement.executeQuery().use { result ->
                assertTrue(result.next())
                result.getLong(1)
            }
        }

    private fun <T> asMigrator(block: (Connection) -> T): T =
        DriverManager.getConnection(postgres.jdbcUrl, "millody_migrator", migrationPassword).use(block)

    private fun <T> asApplication(block: (Connection) -> T): T =
        DriverManager.getConnection(postgres.jdbcUrl, "millody_app", applicationPassword).use(block)
}
