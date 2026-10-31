package com.millodysingqueue.singer

import com.millodysingqueue.ApiError
import com.millodysingqueue.FakeSingerRepository
import com.millodysingqueue.configureApplication
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

class SingerApiTest {
    @Test
    fun `registration returns created JSON and directory is uncached and ordered`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository())) }
        assertEquals("[]", client.get("/api/singers").bodyAsText())
        val created = client.post("/api/singers") {
            contentType(ContentType.Application.Json)
            setBody("""{"singerName":"  Mill  ","nickName":"  ","email":""}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(ContentType.Application.Json, created.contentType()?.withoutParameters())
        val singer = Json.decodeFromString<SingerResponse>(created.bodyAsText())
        assertEquals("Mill", singer.singerName)
        assertEquals("1", singer.singerId)
        assertEquals(null, singer.email)
        client.post("/api/singers") {
            contentType(ContentType.Application.Json)
            setBody("""{"singerName":"Amy"}""")
        }
        val directory = client.get("/api/singers")
        assertEquals("no-store", directory.headers[HttpHeaders.CacheControl])
        assertEquals(listOf("Amy", "Mill"), Json.decodeFromString<List<SingerResponse>>(directory.bodyAsText()).map { it.singerName })
    }

    @Test
    fun `invalid fields and malformed JSON return safe structured errors`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository())) }
        for (payload in listOf("{}", """{"singerName":" "}""", """{"singerName":"Mill","email":"bad"}""", "{", "null", "[]", """{"singerName":12}""", """{"singerName":"Mill","email":true}""", """{"singerName":"Mill","extra":"unknown"}""")) {
            val response = client.post("/api/singers") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, payload)
            Json.decodeFromString<ApiError>(response.bodyAsText())
            assertFalse(response.bodyAsText().contains("Exception"))
        }
        val media = client.post("/api/singers") { contentType(ContentType.Text.Plain); setBody("Mill") }
        assertEquals(HttpStatusCode.UnsupportedMediaType, media.status)
        assertEquals("UNSUPPORTED_MEDIA_TYPE", Json.decodeFromString<ApiError>(media.bodyAsText()).code)
    }

    @Test
    fun `duplicate email returns conflict and field error`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository())) }
        suspend fun register(email: String) = client.post("/api/singers") {
            contentType(ContentType.Application.Json)
            setBody("""{"singerName":"Mill","email":"$email"}""")
        }
        assertEquals(HttpStatusCode.Created, register("Mill@example.com").status)
        val duplicate = register("mill@EXAMPLE.COM")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        val error = Json.decodeFromString<ApiError>(duplicate.bodyAsText())
        assertEquals("EMAIL_ALREADY_EXISTS", error.code)
        assertEquals(setOf("email"), error.fieldErrors.keys)
    }

    @Test
    fun `duplicate singer nickname returns the requested field error and different pairs are allowed`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository())) }
        suspend fun register(name: String, nickname: String?) = client.post("/api/singers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateSingerRequest(name, nickname)))
        }
        assertEquals(HttpStatusCode.Created, register("Mill", "Bandara").status)
        for ((name, nickname) in listOf("Mill" to "Bandara", " mill " to " BANDARA ")) {
            val duplicate = register(name, nickname)
            assertEquals(HttpStatusCode.Conflict, duplicate.status)
            val error = Json.decodeFromString<ApiError>(duplicate.bodyAsText())
            assertEquals("NICKNAME_ALREADY_EXISTS", error.code)
            assertEquals("nickname already exists. please choose a different nickname", error.message)
            assertEquals(mapOf("nickName" to error.message), error.fieldErrors)
        }
        assertEquals(HttpStatusCode.Created, register("Mill", "Host").status)
        assertEquals(HttpStatusCode.Created, register("Mick", "Bandara").status)
        assertEquals(HttpStatusCode.Created, register("Mill", null).status)
        assertEquals(HttpStatusCode.Created, register("Mill", " ").status)
        assertEquals(5, Json.decodeFromString<List<SingerResponse>>(client.get("/api/singers").bodyAsText()).size)
    }

    @Test
    fun `storage failures return retryable responses without SQL or secrets`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository().apply { unavailable = true })) }
        val response = client.get("/api/singers")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("STORAGE_UNAVAILABLE", Json.decodeFromString<ApiError>(response.bodyAsText()).code)
    }
}
