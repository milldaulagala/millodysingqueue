package com.millodysingqueue.database

import com.millodysingqueue.performance.*
import java.time.LocalDate
import com.millodysingqueue.workspace.WorkspaceContext
import com.millodysingqueue.module
import com.millodysingqueue.singer.DuplicateSingerEmailException
import com.millodysingqueue.singer.DuplicateSingerNicknameException
import com.millodysingqueue.singer.ExposedSingerRepository
import com.millodysingqueue.singer.NewSinger
import com.millodysingqueue.singer.SingerResponse
import com.millodysingqueue.song.DuplicateSongVideoException
import com.millodysingqueue.song.ExposedSongRepository
import com.millodysingqueue.song.NewSong
import com.millodysingqueue.song.SaveSongRequest
import com.millodysingqueue.song.SongResponse
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
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
        migrateLegacyDatabase()
        val singerBefore = asMigrator { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SELECT setval('app.singers_singer_id_seq', 5)")
                statement.executeQuery("INSERT INTO app.singers (workspace_id, singer_name, nick_name, email) VALUES (1, 'Mill', 'Bandara', 'mill@example.com') RETURNING singer_id, singer_name, nick_name, email, created_at").use { result ->
                    assertTrue(result.next())
                    (1..5).map { result.getString(it) }
                }
            }
        }
        val songBefore = asMigrator { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("INSERT INTO app.songs (workspace_id, title, artist, youtube_video_id) VALUES (1, 'Country Roads', 'John Denver', '7aNW9hRKYA8') RETURNING song_id, title, artist, youtube_video_id, created_at").use { result ->
                    assertTrue(result.next())
                    (1..5).map { result.getString(it) }
                }
            }
        }
        val previousChanges = changeRecords()
        assertEquals(4, previousChanges.size)
        migrate("update")
        assertEquals(previousChanges, changeRecords().take(4), "Existing migration records must not change")
        asApplication { connection ->
            assertTrue(insertSinger(connection, "After upgrade") > singerBefore.first().toLong(),
                "Upgrade must preserve the identity sequence as well as existing IDs")
            for ((sql, before) in listOf(
                "SELECT singer_id, singer_name, nick_name, email, created_at, workspace_id FROM app.singers WHERE email = 'mill@example.com'" to singerBefore,
                "SELECT song_id, title, artist, youtube_video_id, created_at, workspace_id FROM app.songs" to songBefore,
            )) {
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { result ->
                        assertTrue(result.next())
                        assertEquals(before, (1..5).map { result.getString(it) })
                        assertEquals(1L, result.getLong(6))
                    }
                }
            }
        }
    }

    @BeforeEach
    fun clearData() {
        asMigrator { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE app.performances, app.events, app.singers, app.songs RESTART IDENTITY")
                statement.execute("DELETE FROM app.workspaces WHERE workspace_id <> 1")
            }
        }
    }

    @Test
    fun `migrations rerun safely without changing their execution records`() {
        val before = changeRecords()
        assertEquals(5, before.size)
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
            val repository = ExposedSingerRepository(managed.database, WorkspaceContext(1))
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
            val saved = ExposedSingerRepository(managed.database, WorkspaceContext(1)).list()
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
                            ExposedSingerRepository(managed.database, WorkspaceContext(1)).create(NewSinger("Mill", "Bandara", null))
                            "created"
                        } catch (_: DuplicateSingerNicknameException) {
                            "conflict"
                        }
                    }
                }
                start.complete(Unit)
                assertEquals(listOf("conflict", "created"), attempts.awaitAll().sorted())
                val repository = ExposedSingerRepository(first.database, WorkspaceContext(1))
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

    @Test
    fun `songs persist across pools with stable ordering and distinct video versions`() = runBlocking {
        ApplicationDatabase(runtimeConfig()).use { managed ->
            val repository = ExposedSongRepository(managed.database, WorkspaceContext(1))
            val first = repository.create(NewSong("Country Roads", "John Denver", "7aNW9hRKYA8"))
            assertTrue(first.songId > 0)
            assertNotNull(first.createdAt)
            assertFailsWith<DuplicateSongVideoException> {
                repository.create(NewSong("Other title", "Other artist", "7aNW9hRKYA8"))
            }
            repository.create(NewSong("Country Roads", "John Denver", "_Ab-cD12e34"))
            repository.create(NewSong("ABBA karaoke", "ABBA", "AbC123_-xyz"))
        }
        ApplicationDatabase(runtimeConfig()).use { managed ->
            val songs = ExposedSongRepository(managed.database, WorkspaceContext(1)).list()
            assertEquals(listOf("ABBA karaoke", "Country Roads", "Country Roads"), songs.map { it.title })
            assertTrue(songs[1].songId < songs[2].songId)
        }
    }

    @Test
    fun `database rejects invalid song data and app cannot alter song schema`() {
        asApplication { connection ->
            fun insert(title: String?, artist: String?, video: String?) {
                connection.prepareStatement("INSERT INTO app.songs (title, artist, youtube_video_id, workspace_id) VALUES (?, ?, ?, 1)").use { statement ->
                    statement.setString(1, title)
                    statement.setString(2, artist)
                    statement.setString(3, video)
                    statement.executeUpdate()
                }
            }
            for ((title, artist, video) in listOf(Triple(null, "John", "7aNW9hRKYA8"), Triple(" ", "John", "7aNW9hRKYA8"),
                Triple("Song", null, "7aNW9hRKYA8"), Triple("Song", " ", "7aNW9hRKYA8"),
                Triple("Song", "John", null), Triple("Song", "John", "invalid"))) {
                assertTrue(assertFailsWith<SQLException> { insert(title, artist, video) }.sqlState in setOf("23502", "23514"))
            }
            insert("Song", "John", "7aNW9hRKYA8")
            assertEquals("23505", assertFailsWith<SQLException> { insert("Other", "Other", "7aNW9hRKYA8") }.sqlState)
            // YouTube IDs are case-sensitive, so differently cased IDs remain distinct.
            insert("Other version", "John", "7ANW9hRKYA8")
            assertEquals("42501", assertFailsWith<SQLException> {
                connection.createStatement().use { it.execute("ALTER TABLE app.songs ADD COLUMN forbidden text") }
            }.sqlState)
        }
    }

    @Test
    fun `concurrent saves of one video create exactly one song`() = runBlocking<Unit> {
        ApplicationDatabase(runtimeConfig()).use { first ->
            ApplicationDatabase(runtimeConfig()).use { second ->
                val start = CompletableDeferred<Unit>()
                val attempts = listOf(first, second).map { managed ->
                    async {
                        start.await()
                        try {
                            ExposedSongRepository(managed.database, WorkspaceContext(1)).create(NewSong("Country Roads", "John Denver", "7aNW9hRKYA8"))
                            "created"
                        } catch (_: DuplicateSongVideoException) {
                            "conflict"
                        }
                    }
                }
                start.complete(Unit)
                assertEquals(listOf("conflict", "created"), attempts.awaitAll().sorted())
                assertEquals(1, ExposedSongRepository(first.database, WorkspaceContext(1)).list().size)
            }
        }
    }

    @Test
    fun `song HTTP API saves extracted IDs and returns database duplicate conflicts`() = testApplication {
        environment { config = runtimeConfig() }
        application { module() }
        suspend fun save(video: String) = client.post("/api/songs") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SaveSongRequest("  Country Roads  ", " John Denver ", video)))
        }
        val created = save("https://www.youtube.com/watch?v=7aNW9hRKYA8&list=RD7aNW9hRKYA8&t=3")
        assertEquals(HttpStatusCode.Created, created.status)
        val song = Json.decodeFromString<SongResponse>(created.bodyAsText())
        assertEquals("Country Roads", song.title)
        assertEquals("7aNW9hRKYA8", song.youtubeVideoId)
        val duplicate = save("https://youtu.be/7aNW9hRKYA8")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        val error = Json.decodeFromString<com.millodysingqueue.ApiError>(duplicate.bodyAsText())
        assertEquals("VIDEO_ALREADY_EXISTS", error.code)
        assertTrue(error.fieldErrors.containsKey("youtubeVideo"))
        assertEquals(listOf(song), Json.decodeFromString<List<SongResponse>>(client.get("/api/songs").bodyAsText()))
    }

    @Test
    fun `workspace repositories isolate lists and scope all duplicate rules`() = runBlocking {
        val otherId = createWorkspace()
        ApplicationDatabase(runtimeConfig()).use { managed ->
            val firstSingers = ExposedSingerRepository(managed.database, WorkspaceContext(1))
            val otherSingers = ExposedSingerRepository(managed.database, WorkspaceContext(otherId))
            val firstSongs = ExposedSongRepository(managed.database, WorkspaceContext(1))
            val otherSongs = ExposedSongRepository(managed.database, WorkspaceContext(otherId))
            val singer = NewSinger("Mill", "Bandara", "mill@example.com")
            val song = NewSong("Country Roads", "John Denver", "7aNW9hRKYA8")
            val firstSinger = firstSingers.create(singer)
            val firstSong = firstSongs.create(song)
            assertTrue(otherSingers.list().isEmpty())
            assertTrue(otherSongs.list().isEmpty())
            val otherSinger = otherSingers.create(singer)
            val otherSong = otherSongs.create(song)
            assertEquals(listOf(firstSinger), firstSingers.list())
            assertEquals(listOf(otherSinger), otherSingers.list())
            assertEquals(listOf(firstSong), firstSongs.list())
            assertEquals(listOf(otherSong), otherSongs.list())
            for (repository in listOf(firstSingers, otherSingers)) {
                assertFailsWith<DuplicateSingerNicknameException> { repository.create(singer.copy(email = null)) }
                assertFailsWith<DuplicateSingerEmailException> { repository.create(singer.copy(nickName = "Host", email = "MILL@example.com")) }
                repository.create(NewSinger("Mick", null, null))
                repository.create(NewSinger("Mick", null, null))
            }
            for (repository in listOf(firstSongs, otherSongs)) {
                assertFailsWith<DuplicateSongVideoException> { repository.create(song) }
            }
        }
    }

    @Test
    fun `database requires valid ownership and restricts deletion of populated workspaces`() {
        asApplication { connection ->
            for (table in listOf("singers", "songs")) {
                val fields = if (table == "singers") "singer_name" else "title, artist, youtube_video_id"
                val values = if (table == "singers") "'Mill'" else "'Song', 'Artist', '7aNW9hRKYA8'"
                assertEquals("23502", assertFailsWith<SQLException> {
                    connection.createStatement().use { it.execute("INSERT INTO app.$table ($fields) VALUES ($values)") }
                }.sqlState)
                assertEquals("23503", assertFailsWith<SQLException> {
                    connection.createStatement().use { it.execute("INSERT INTO app.$table ($fields, workspace_id) VALUES ($values, 9223372036854775807)") }
                }.sqlState)
            }
            for (sql in listOf("INSERT INTO app.workspaces (workspace_name) VALUES ('Forbidden')",
                "UPDATE app.workspaces SET workspace_name = 'Forbidden'", "DELETE FROM app.workspaces",
                "SELECT nextval('app.workspaces_workspace_id_seq')")) {
                assertEquals("42501", assertFailsWith<SQLException> { connection.createStatement().use { it.execute(sql) } }.sqlState)
            }
        }
        for (table in listOf("singers", "songs")) {
            val id = createWorkspace()
            asMigrator { connection ->
                connection.createStatement().use { statement ->
                    if (table == "singers") statement.execute("INSERT INTO app.singers (workspace_id, singer_name) VALUES ($id, 'Mill')")
                    else statement.execute("INSERT INTO app.songs (workspace_id, title, artist, youtube_video_id) VALUES ($id, 'Song', 'Artist', '7aNW9hRKYA8')")
                    assertEquals("23503", assertFailsWith<SQLException> {
                        statement.execute("DELETE FROM app.workspaces WHERE workspace_id = $id")
                    }.sqlState)
                }
            }
        }
    }

    @Test
    fun `configured workspace must exist and pooled connections remain usable after failure`() {
        ApplicationDatabase(runtimeConfig()).use { managed ->
            WorkspaceContext(1).requireExists(managed.database)
            assertFailsWith<IllegalArgumentException> {
                WorkspaceContext(Long.MAX_VALUE).requireExists(managed.database)
            }
            WorkspaceContext(1).requireExists(managed.database)
        }
    }

    @Test
    fun `API uses server workspace and ignores client scope headers and query parameters`() = testApplication {
        val other = createWorkspace()
        ApplicationDatabase(runtimeConfig()).use { managed ->
            ExposedSingerRepository(managed.database, WorkspaceContext(other)).create(NewSinger("Private singer", null, "private@example.com"))
            ExposedSongRepository(managed.database, WorkspaceContext(other)).create(NewSong("Private song", "Artist", "7aNW9hRKYA8"))
        }
        environment { config = runtimeConfig() }
        application { module() }
        for (path in listOf("singers", "songs")) {
            val response = client.get("/api/$path?workspaceId=$other") { headers.append("X-Workspace-Id", other.toString()) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("[]", response.bodyAsText())
        }
        val created = client.post("/api/songs") {
            headers.append("X-Workspace-Id", other.toString())
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SaveSongRequest("Public song", "Artist", "7aNW9hRKYA8")))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        ApplicationDatabase(runtimeConfig()).use { managed ->
            assertEquals(listOf("Public song"), ExposedSongRepository(managed.database, WorkspaceContext(1)).list().map { it.title })
            assertEquals(listOf("Private song"), ExposedSongRepository(managed.database, WorkspaceContext(other)).list().map { it.title })
        }
    }

    @Test
    fun `fresh database migrates fully with workspace privileges and ownership constraints`() {
        val name = "workspace_fresh_test"
        val url = postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') + "/" + name
        fun <T> asAdmin(block: (Connection) -> T): T =
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use(block)
        asAdmin { connection ->
            connection.createStatement().use { it.execute("CREATE DATABASE $name OWNER millody_migrator") }
        }
        try {
            DriverManager.getConnection(url, "millody_migrator", migrationPassword).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("REVOKE ALL ON DATABASE $name FROM PUBLIC")
                    statement.execute("GRANT CONNECT ON DATABASE $name TO millody_app")
                    statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC")
                    statement.execute("CREATE SCHEMA app AUTHORIZATION millody_migrator")
                    statement.execute("CREATE SCHEMA liquibase AUTHORIZATION millody_migrator")
                    statement.execute("GRANT USAGE ON SCHEMA app TO millody_app")
                    statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO millody_app")
                    statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT USAGE, SELECT ON SEQUENCES TO millody_app")
                }
            }
            DatabaseMigrations.execute("update", url, "millody_migrator", migrationPassword, ByteArrayOutputStream())
            DatabaseMigrations.execute("validate", url, "millody_migrator", migrationPassword, ByteArrayOutputStream())
            DriverManager.getConnection(url, "millody_app", applicationPassword).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT workspace_id, workspace_name FROM app.workspaces").use { result ->
                        assertTrue(result.next())
                        assertEquals(1L, result.getLong(1))
                        assertEquals("MillodySingQueue", result.getString(2))
                    }
                    insertSinger(connection, "Mill")
                    statement.execute("INSERT INTO app.songs (workspace_id, title, artist, youtube_video_id) VALUES (1, 'Country Roads', 'John Denver', '7aNW9hRKYA8')")
                    assertEquals("42501", assertFailsWith<SQLException> {
                        statement.execute("INSERT INTO app.workspaces (workspace_name) VALUES ('Forbidden')")
                    }.sqlState)
                }
            }
        } finally {
            asAdmin { connection ->
                connection.createStatement().use { it.execute("DROP DATABASE $name WITH (FORCE)") }
            }
        }
    }

    @Test
    fun `nonexistent configured workspace prevents runtime startup and closes its pool`() {
        testApplication {
            environment { config = runtimeConfig(Long.MAX_VALUE) }
            application { module() }
            assertFailsWith<IllegalArgumentException> { startApplication() }
        }
        asMigrator { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE usename = 'millody_app' AND application_name = 'MillodySingQueue'").use { result ->
                    assertTrue(result.next())
                    assertEquals(0, result.getInt(1))
                }
            }
        }
    }

    private fun createWorkspace(): Long = asMigrator { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("INSERT INTO app.workspaces (workspace_name) VALUES ('Other organiser') RETURNING workspace_id").use { result ->
                assertTrue(result.next())
                result.getLong(1)
            }
        }
    }

    private fun migrateLegacyDatabase() {
        DriverManager.getConnection(postgres.jdbcUrl, "millody_migrator", migrationPassword).use { connection ->
            val database = liquibase.database.DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(liquibase.database.jvm.JdbcConnection(connection))
            database.defaultSchemaName = "app"
            database.liquibaseSchemaName = "liquibase"
            liquibase.resource.ClassLoaderResourceAccessor().use { resources ->
                liquibase.Scope.child(mapOf(liquibase.Scope.Attr.resourceAccessor.name to resources)) {
                    liquibase.command.CommandScope("update")
                        .addArgumentValue("database", database)
                        .addArgumentValue("changelogFile", "db/legacy-changelog.xml")
                        .setOutput(ByteArrayOutputStream())
                        .execute()
                }
            }
        }
    }

    @Test
    fun `performance order and cancellation persist across pools including repeated selections`() = runBlocking {
        var eventId = 0L
        ApplicationDatabase(runtimeConfig()).use { managed ->
            val (event, singer, song) = performanceFixture(managed)
            eventId = event
            val repository = ExposedPerformanceRepository(managed.database, WorkspaceContext(1))
            val first = repository.add(event, singer, song, 0)
            val second = repository.add(event, singer, song, 1)
            assertEquals(listOf(1L, 2L), second.performances.map { it.queuePosition })
            val moved = repository.move(event, second.performances[1].performanceId, "UP", 2)
            assertEquals(second.performances[1].performanceId, moved.performances[0].performanceId)
            assertFailsWith<PerformanceValidationException> { repository.move(event, moved.performances[0].performanceId, "UP", 3) }
            assertEquals(3L, repository.queue(event).queueVersion)
            val cancelled = repository.cancel(event, moved.performances[0].performanceId, 3)
            assertEquals(listOf(first.performances[0].performanceId), cancelled.performances.map { it.performanceId })
            assertFailsWith<StaleQueueException> { repository.add(event, singer, song, 3) }
            assertFailsWith<PerformanceNotFoundException> { repository.cancel(event, moved.performances[0].performanceId, 4) }
            asApplication { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT status FROM app.performances ORDER BY performance_id").use { result ->
                        assertTrue(result.next()); assertEquals("WAITING", result.getString(1))
                        assertTrue(result.next()); assertEquals("CANCELLED", result.getString(1))
                    }
                }
            }
        }
        ApplicationDatabase(runtimeConfig()).use { managed ->
            val queue = ExposedPerformanceRepository(managed.database, WorkspaceContext(1)).queue(eventId)
            assertEquals(4L, queue.queueVersion)
            assertEquals(1, queue.performances.size)
            assertEquals("Mill", queue.performances[0].singerName)
            assertEquals("Country Roads", queue.performances[0].songTitle)
        }
    }

    @Test
    fun `events and performances reject other workspace references at repository and database boundaries`() = runBlocking {
        val other = createWorkspace()
        ApplicationDatabase(runtimeConfig()).use { managed ->
            val (event, singer, song) = performanceFixture(managed)
            val (otherEvent, otherSinger, otherSong) = performanceFixture(managed, other)
            val repository = ExposedPerformanceRepository(managed.database, WorkspaceContext(1))
            assertEquals(listOf(event), repository.events().map { it.eventId })
            assertFailsWith<PerformanceNotFoundException> { repository.queue(otherEvent) }
            assertFailsWith<PerformanceNotFoundException> { repository.add(otherEvent, singer, song, 0) }
            assertFailsWith<PerformanceValidationException> { repository.add(event, otherSinger, song, 0) }
            assertFailsWith<PerformanceValidationException> { repository.add(event, singer, otherSong, 0) }
            assertFailsWith<PerformanceValidationException> { repository.add(event, Long.MAX_VALUE, Long.MAX_VALUE, 0) }
            assertTrue(repository.queue(event).performances.isEmpty())
            asApplication { connection ->
                for ((e, si, so) in listOf(Triple(otherEvent, singer, song), Triple(event, otherSinger, song), Triple(event, singer, otherSong))) {
                    assertEquals("23503", assertFailsWith<SQLException> {
                        connection.createStatement().use { it.execute("INSERT INTO app.performances (workspace_id, event_id, singer_id, song_id, queue_position) VALUES (1, $e, $si, $so, 1)") }
                    }.sqlState)
                }
                connection.createStatement().use { statement ->
                    for ((position, status) in listOf(0 to "WAITING", 1 to "COMPLETED")) {
                        assertEquals("23514", assertFailsWith<SQLException> {
                            statement.execute("INSERT INTO app.performances (workspace_id, event_id, singer_id, song_id, queue_position, status) VALUES (1, $event, $singer, $song, $position, '$status')")
                        }.sqlState)
                    }
                }
            }
            repository.add(event, singer, song, 0)
            asApplication { connection ->
                for (sql in listOf(
                    "INSERT INTO app.performances (workspace_id, event_id, singer_id, song_id, queue_position) VALUES (1, $event, $singer, $song, 1)",
                    "DELETE FROM app.events WHERE event_id = $event",
                    "DELETE FROM app.singers WHERE singer_id = $singer",
                    "DELETE FROM app.songs WHERE song_id = $song",
                )) {
                    val expected = if (sql.startsWith("INSERT")) "23505" else "23503"
                    assertEquals(expected, assertFailsWith<SQLException> { connection.createStatement().use { it.execute(sql) } }.sqlState)
                }
            }
        }
    }

    @Test
    fun `concurrent additions compare versions under the same event lock without losing changes`() = runBlocking {
        ApplicationDatabase(runtimeConfig()).use { first ->
            ApplicationDatabase(runtimeConfig()).use { second ->
                val (event, singer, song) = performanceFixture(first)
                val start = CompletableDeferred<Unit>()
                val attempts = listOf(first, second).map { managed -> async {
                    start.await()
                    try {
                        ExposedPerformanceRepository(managed.database, WorkspaceContext(1)).add(event, singer, song, 0)
                        "saved"
                    } catch (_: StaleQueueException) { "stale" }
                } }
                start.complete(Unit)
                assertEquals(listOf("saved", "stale"), attempts.awaitAll().sorted())
                val repository = ExposedPerformanceRepository(first.database, WorkspaceContext(1))
                val refreshed = repository.queue(event)
                assertEquals(1, refreshed.performances.size)
                val next = repository.add(event, singer, song, refreshed.queueVersion)
                assertEquals(listOf(1L, 2L), next.performances.map { it.queuePosition })
            }
        }
    }

    @Test
    fun `concurrent move and cancellation cannot overwrite one another`() = runBlocking {
        ApplicationDatabase(runtimeConfig()).use { first ->
            ApplicationDatabase(runtimeConfig()).use { second ->
                val (event, singer, song) = performanceFixture(first)
                val one = ExposedPerformanceRepository(first.database, WorkspaceContext(1))
                val two = ExposedPerformanceRepository(second.database, WorkspaceContext(1))
                one.add(event, singer, song, 0)
                val id = one.add(event, singer, song, 1).performances[1].performanceId
                val start = CompletableDeferred<Unit>()
                val operations = listOf<suspend () -> Unit>({ one.move(event, id, "UP", 2) }, { two.cancel(event, id, 2) })
                val attempts = operations.map { operation -> async {
                    start.await()
                    try { operation(); "saved" } catch (_: StaleQueueException) { "stale" }
                } }
                start.complete(Unit)
                assertEquals(listOf("saved", "stale"), attempts.awaitAll().sorted())
                assertEquals(3L, one.queue(event).queueVersion)
            }
        }
    }

    @Test
    fun `two hundred performances can be ordered cancelled and appended`() = runBlocking {
        ApplicationDatabase(runtimeConfig()).use { managed ->
            val (event, singer, song) = performanceFixture(managed)
            val repository = ExposedPerformanceRepository(managed.database, WorkspaceContext(1))
            repeat(200) { index -> repository.add(event, singer, song, index.toLong()) }
            val initial = repository.queue(event)
            assertEquals(200, initial.performances.size)
            assertEquals((1L..200L).toList(), initial.performances.map { it.queuePosition })
            val moved = repository.move(event, initial.performances.last().performanceId, "UP", 200)
            assertEquals(initial.performances.last().performanceId, moved.performances[198].performanceId)
            repository.cancel(event, initial.performances[99].performanceId, 201)
            val appended = repository.add(event, singer, song, 202)
            assertEquals(200, appended.performances.size)
            assertEquals(201L, appended.performances.last().queuePosition)
            assertEquals(203L, appended.queueVersion)
        }
    }

    @Test
    fun `performance HTTP API uses database ownership version errors and safe storage failures`() = testApplication {
        var event = 0L
        var singer = 0L
        var song = 0L
        ApplicationDatabase(runtimeConfig()).use { managed ->
            val fixture = performanceFixture(managed)
            event = fixture.first; singer = fixture.second; song = fixture.third
        }
        environment { config = runtimeConfig() }
        application { module() }
        val path = "/api/events/$event/performances"
        val created = client.post(path) {
            contentType(ContentType.Application.Json)
            setBody("""{"singerId":"$singer","songId":"$song","queueVersion":"0"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val response = Json.decodeFromString<QueueResponse>(created.bodyAsText())
        assertEquals("1", response.queueVersion)
        val stale = client.post("$path/${response.performances[0].performanceId}/cancel") {
            contentType(ContentType.Application.Json); setBody("""{"queueVersion":"0"}""")
        }
        assertEquals(HttpStatusCode.Conflict, stale.status)
        val other = createWorkspace()
        var otherEvent = 0L
        ApplicationDatabase(runtimeConfig()).use { managed -> otherEvent = performanceFixture(managed, other).first }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/events/$otherEvent/performances").status)
        asMigrator { connection -> connection.createStatement().use { it.execute("REVOKE SELECT ON app.events FROM millody_app") } }
        try {
            assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/events").status)
        } finally {
            asMigrator { connection -> connection.createStatement().use { it.execute("GRANT SELECT ON app.events TO millody_app") } }
        }
    }

    private suspend fun performanceFixture(managed: ApplicationDatabase, workspace: Long = 1): Triple<Long, Long, Long> {
        val scope = WorkspaceContext(workspace)
        val event = ExposedPerformanceRepository(managed.database, scope)
            .createEvent(NewEvent("Friday at Mill’s", LocalDate.parse("2026-11-13"), "Mill"))
        val singer = ExposedSingerRepository(managed.database, scope).create(NewSinger("Mill", "Host", null))
        val song = ExposedSongRepository(managed.database, scope).create(NewSong("Country Roads", "John Denver", "7aNW9hRKYA8"))
        return Triple(event.eventId, singer.singerId, song.songId)
    }

    private fun runtimeConfig(workspaceId: Long = 1) = MapApplicationConfig(
        "database.url" to postgres.jdbcUrl,
        "database.user" to "millody_app",
        "database.password" to applicationPassword,
        "workspace.id" to workspaceId.toString(),
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
            "INSERT INTO app.singers (singer_name, email, nick_name, workspace_id) VALUES (?, ?, ?, 1) RETURNING singer_id",
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
