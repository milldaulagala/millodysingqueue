package com.millodysingqueue.database

import com.millodysingqueue.module
import com.millodysingqueue.singer.DuplicateSingerEmailException
import com.millodysingqueue.singer.DuplicateSingerNicknameException
import com.millodysingqueue.singer.ExposedSingerRepository
import com.millodysingqueue.singer.NewSinger
import com.millodysingqueue.singer.SingerResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

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
        assertEquals(2, before.size)
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

    @Test
    fun `Exposed uses the application role persists across pools and handles conflicts`() = runBlocking {
        val config = runtimeConfig()
        ApplicationDatabase(config).use { managed ->
            val repository = ExposedSingerRepository(managed.database)
            val mill = repository.create(NewSinger("Mill", "Host", "Mill@example.com"))
            assertTrue(mill.singerId > 0)
            assertNotNull(mill.createdAt)
            assertEquals("Host", mill.nickName)
            repository.create(NewSinger("Mick", "Work Mick", null))
            repository.create(NewSinger("Mick", null, null))
            assertFailsWith<DuplicateSingerEmailException> {
                repository.create(NewSinger("Another", null, "mill@EXAMPLE.COM"))
            }
            assertEquals(3, repository.list().size)
        }
        ApplicationDatabase(config).use { managed ->
            val saved = ExposedSingerRepository(managed.database).list()
            assertEquals(listOf("Mick", "Mick", "Mill"), saved.map { it.singerName })
            assertTrue(saved[0].singerId < saved[1].singerId)
        }
    }

    @Test
    fun `runtime module serves API through pooled PostgreSQL and closes on shutdown`() {
        testApplication {
            environment { config = runtimeConfig() }
            application { module() }
            val created = client.post("/api/singers") {
                contentType(ContentType.Application.Json)
                setBody("""{"singerName":"  Mill  ","email":"mill@example.com"}""")
            }
            assertEquals(HttpStatusCode.Created, created.status)
            val singer = Json.decodeFromString<SingerResponse>(created.bodyAsText())
            assertEquals("Mill", singer.singerName)
            val duplicate = client.post("/api/singers") {
                contentType(ContentType.Application.Json)
                setBody("""{"singerName":"Another","email":"MILL@example.com"}""")
            }
            assertEquals(HttpStatusCode.Conflict, duplicate.status)
            val directory = client.get("/api/singers")
            assertEquals(listOf(singer), Json.decodeFromString<List<SingerResponse>>(directory.bodyAsText()))
            asApplication { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT singer_name FROM app.singers").use { result ->
                        assertTrue(result.next())
                        assertEquals("Mill", result.getString(1))
                    }
                }
            }
        }
        asMigrator { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE usename = 'millody_app' AND application_name = 'MillodySingQueue'").use { result ->
                    assertTrue(result.next())
                    assertEquals(0, result.getInt(1), "Application shutdown must close pool connections")
                }
            }
        }
    }

    @Test
    fun `database access failures return safe retryable API errors`() = testApplication {
        environment { config = runtimeConfig() }
        application { module() }
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
        asMigrator { connection ->
            connection.createStatement().use { it.execute("REVOKE SELECT ON app.singers FROM millody_app") }
        }
        try {
            val response = client.get("/api/singers")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("STORAGE_UNAVAILABLE", Json.decodeFromString<com.millodysingqueue.ApiError>(response.bodyAsText()).code)
            assertTrue(!response.bodyAsText().contains("SELECT"))
        } finally {
            asMigrator { connection ->
                connection.createStatement().use { it.execute("GRANT SELECT ON app.singers TO millody_app") }
            }
        }
    }

    @Test
    fun `database enforces name nickname uniqueness while allowing distinct pairs and missing nicknames`() {
        asApplication { connection ->
            insertSinger(connection, "Mill", nickname = "Bandara")
            for ((name, nickname) in listOf("Mill" to "Bandara", " mill " to " BANDARA ")) {
                assertEquals("23505", assertFailsWith<SQLException> {
                    insertSinger(connection, name, nickname = nickname)
                }.sqlState)
            }
            insertSinger(connection, "Mill", nickname = "Host")
            insertSinger(connection, "Mick", nickname = "Bandara")
            insertSinger(connection, "Mill")
            insertSinger(connection, "Mill")
        }
    }

    @Test
    fun `concurrent registrations of the same name nickname pair save only one singer`() = runBlocking<Unit> {
        ApplicationDatabase(runtimeConfig()).use { first ->
            ApplicationDatabase(runtimeConfig()).use { second ->
                val start = CompletableDeferred<Unit>()
                val attempts = listOf(first, second).map { managed ->
                    async {
                        start.await()
                        try {
                            ExposedSingerRepository(managed.database).create(NewSinger("Mill", "Bandara", null))
                            "created"
                        } catch (_: DuplicateSingerNicknameException) {
                            "conflict"
                        }
                    }
                }
                start.complete(Unit)
                assertEquals(listOf("conflict", "created"), attempts.awaitAll().sorted())
                val repository = ExposedSingerRepository(first.database)
                assertEquals(1, repository.list().size)
                repository.create(NewSinger("Mill", "Host", null))
                assertEquals(2, repository.list().size)
            }
        }
    }

    @Test
    fun `nickname database conflict reaches API as a nickname field error`() = testApplication {
        environment { config = runtimeConfig() }
        application { module() }
        suspend fun register(nickname: String) = client.post("/api/singers") {
            contentType(ContentType.Application.Json)
            setBody("""{"singerName":"Mill","nickName":"$nickname"}""")
        }
        assertEquals(HttpStatusCode.Created, register("Bandara").status)
        val duplicate = register(" bandara ")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        val error = Json.decodeFromString<com.millodysingqueue.ApiError>(duplicate.bodyAsText())
        assertEquals("NICKNAME_ALREADY_EXISTS", error.code)
        assertEquals(mapOf("nickName" to "nickname already exists. please choose a different nickname"), error.fieldErrors)
        assertEquals(HttpStatusCode.Created, register("Host").status)
    }

    private fun runtimeConfig() = MapApplicationConfig(
        "database.url" to postgres.jdbcUrl,
        "database.user" to "millody_app",
        "database.password" to applicationPassword,
    )

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
