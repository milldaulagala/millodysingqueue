package com.millodysingqueue.song

import com.millodysingqueue.ApiError
import com.millodysingqueue.FakeSingerRepository
import com.millodysingqueue.FakeSongRepository
import com.millodysingqueue.configureApplication
import com.millodysingqueue.singer.SingerService
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json

class SongApiTest {
    @Test
    fun `save returns created JSON and the uncached library is ordered`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository()), SongService(FakeSongRepository())) }
        assertEquals("[]", client.get("/api/songs").bodyAsText())
        val saved = client.post("/api/songs") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SaveSongRequest("Country Roads", "John Denver", "https://youtu.be/7aNW9hRKYA8")))
        }
        assertEquals(HttpStatusCode.Created, saved.status)
        assertEquals(ContentType.Application.Json, saved.contentType()?.withoutParameters())
        val song = Json.decodeFromString<SongResponse>(saved.bodyAsText())
        assertEquals("1", song.songId)
        assertEquals("7aNW9hRKYA8", song.youtubeVideoId)
        assertEquals("no-store", saved.headers[HttpHeaders.CacheControl])
        client.post("/api/songs") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SaveSongRequest("ABBA karaoke", "ABBA", "_Ab-cD12e34")))
        }
        val library = client.get("/api/songs")
        assertEquals("no-store", library.headers[HttpHeaders.CacheControl])
        assertEquals(listOf("ABBA karaoke", "Country Roads"),
            Json.decodeFromString<List<SongResponse>>(library.bodyAsText()).map { it.title })
    }

    @Test
    fun `invalid input returns field errors and no song is saved`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository()), SongService(FakeSongRepository())) }
        val response = client.post("/api/songs") { contentType(ContentType.Application.Json); setBody("{}") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(setOf("title", "artist", "youtubeVideo"),
            Json.decodeFromString<ApiError>(response.bodyAsText()).fieldErrors.keys)
        assertEquals("[]", client.get("/api/songs").bodyAsText())
    }

    @Test
    fun `malformed JSON wrong field types and unexpected fields are rejected`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository()), SongService(FakeSongRepository())) }
        for (body in listOf("{", "[]", "null", """{"title":12}""", """{"artist":true}""",
            """{"youtubeVideo":[]}""", """{"extra":"value"}""")) {
            val response = client.post("/api/songs") { contentType(ContentType.Application.Json); setBody(body) }
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            assertEquals("INVALID_REQUEST", Json.decodeFromString<ApiError>(response.bodyAsText()).code)
        }
        val response = client.post("/api/songs") { contentType(ContentType.Text.Plain); setBody("Country Roads") }
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        Json.decodeFromString<ApiError>(response.bodyAsText())
    }

    @Test
    fun `duplicate video returns a conflict attached to the video field`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository()), SongService(FakeSongRepository())) }
        suspend fun save(video: String) = client.post("/api/songs") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SaveSongRequest("Country Roads", "John Denver", video)))
        }
        assertEquals(HttpStatusCode.Created, save("7aNW9hRKYA8").status)
        val response = save("https://www.youtube.com/watch?v=7aNW9hRKYA8&t=3")
        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals("VIDEO_ALREADY_EXISTS", error.code)
        assertEquals(mapOf("youtubeVideo" to error.message), error.fieldErrors)
    }

    @Test
    fun `storage failures produce safe errors for listing and saving`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository()),
            SongService(FakeSongRepository().apply { unavailable = true })) }
        val responses = listOf(client.get("/api/songs"), client.post("/api/songs") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SaveSongRequest("Country Roads", "John Denver", "7aNW9hRKYA8")))
        })
        for (response in responses) {
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("STORAGE_UNAVAILABLE", Json.decodeFromString<ApiError>(response.bodyAsText()).code)
            assertFalse(response.bodyAsText().contains("Exception"))
        }
    }
}
