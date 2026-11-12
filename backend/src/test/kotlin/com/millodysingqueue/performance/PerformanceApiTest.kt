package com.millodysingqueue.performance

import com.millodysingqueue.*
import com.millodysingqueue.singer.SingerService
import com.millodysingqueue.song.SongService
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json

class PerformanceApiTest {
    @Test
    fun `event and queue endpoints create reorder and cancel without deleting history`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository()), SongService(FakeSongRepository()), PerformanceService(FakePerformanceRepository())) }
        suspend fun post(path: String, body: String) = client.post(path) { contentType(ContentType.Application.Json); setBody(body) }
        val event = post("/api/events", """{"eventName":"Friday","eventDate":"2026-11-13","organiserName":"Mill"}""")
        assertEquals(HttpStatusCode.Created, event.status)
        assertEquals("1", Json.decodeFromString<EventResponse>(event.bodyAsText()).eventId)
        val path = "/api/events/1/performances"
        val first = post(path, """{"singerId":"1","songId":"1","queueVersion":"0"}""")
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals("no-store", first.headers[HttpHeaders.CacheControl])
        val stale = post(path, """{"singerId":"1","songId":"1","queueVersion":"0"}""")
        assertEquals(HttpStatusCode.Conflict, stale.status)
        assertEquals("STALE_QUEUE", Json.decodeFromString<ApiError>(stale.bodyAsText()).code)
        assertEquals(HttpStatusCode.Created, post(path, """{"singerId":"1","songId":"1","queueVersion":"1"}""").status)
        val moved = post("$path/2/move", """{"direction":"UP","queueVersion":"2"}""")
        assertEquals(HttpStatusCode.OK, moved.status)
        assertEquals(listOf("2", "1"), Json.decodeFromString<QueueResponse>(moved.bodyAsText()).performances.map { it.performanceId })
        val removed = post("$path/2/cancel", """{"queueVersion":"3"}""")
        assertEquals(HttpStatusCode.OK, removed.status)
        val queue = client.get(path)
        assertEquals("no-store", queue.headers[HttpHeaders.CacheControl])
        assertEquals(listOf("1"), Json.decodeFromString<QueueResponse>(queue.bodyAsText()).performances.map { it.performanceId })
        assertEquals("4", Json.decodeFromString<QueueResponse>(queue.bodyAsText()).queueVersion)
    }

    @Test
    fun `strict JSON contracts validation missing references and media types return safe errors`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository()), SongService(FakeSongRepository()), PerformanceService(FakePerformanceRepository())) }
        for (body in listOf("{", "[]", "null", """{"eventName":1}""", """{"workspaceId":"2"}""", """{"eventDate":[]}""")) {
            val response = client.post("/api/events") { contentType(ContentType.Application.Json); setBody(body) }
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            assertEquals("INVALID_REQUEST", Json.decodeFromString<ApiError>(response.bodyAsText()).code)
        }
        val invalid = client.post("/api/events") { contentType(ContentType.Application.Json); setBody("{}") }
        assertEquals(setOf("eventName", "eventDate", "organiserName"), Json.decodeFromString<ApiError>(invalid.bodyAsText()).fieldErrors.keys)
        assertEquals(HttpStatusCode.UnsupportedMediaType, client.post("/api/events") { setBody("not JSON") }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/events/999/performances").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/events/not-an-id/performances").status)
        for (body in listOf("""{"singerId":1}""", """{"queueVersion":0}""", """{"direction":true}""")) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/api/events/1/performances") { contentType(ContentType.Application.Json); setBody(body) }.status)
        }
    }

    @Test
    fun `storage failures are retryable and disclose no internal details`() = testApplication {
        val repository = FakePerformanceRepository().apply { unavailable = true }
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository()), SongService(FakeSongRepository()), PerformanceService(repository)) }
        val responses = listOf(client.get("/api/events"), client.get("/api/events/1/performances"), client.post("/api/events") {
            contentType(ContentType.Application.Json)
            setBody("""{"eventName":"Friday","eventDate":"2026-11-13","organiserName":"Mill"}""")
        })
        for (response in responses) {
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("STORAGE_UNAVAILABLE", Json.decodeFromString<ApiError>(response.bodyAsText()).code)
            assertFalse(response.bodyAsText().contains("Exception"))
        }
    }
}
