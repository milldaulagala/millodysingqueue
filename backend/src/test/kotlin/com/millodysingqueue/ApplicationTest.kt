package com.millodysingqueue

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun `health endpoint returns an uncached JSON status`() = testApplication {
        environment { config = ApplicationConfig("application.conf") }
        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(buildJsonObject { put("status", "UP") }, Json.parseToJsonElement(response.bodyAsText()))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `frontend and compiled bootstrap are served by the application`() = testApplication {
        environment { config = ApplicationConfig("application.conf") }
        val page = client.get("/")

        assertEquals(HttpStatusCode.OK, page.status)
        assertEquals(ContentType.Text.Html, page.contentType()?.withoutParameters())
        assertContains(page.bodyAsText(), "millodysingqueue/millodysingqueue.nocache.js")
        assertEquals(HttpStatusCode.OK, client.get("/app.css").status)
        assertEquals(HttpStatusCode.OK, client.get("/millodysingqueue/millodysingqueue.nocache.js").status)
    }

    @Test
    fun `unknown API paths and build metadata are not served as the application page`() = testApplication {
        environment { config = ApplicationConfig("application.conf") }
        for (path in listOf("/api/missing", "/WEB-INF/classes/com/millodysingqueue/client/MillodySingQueueEntryPoint.class")) {
            assertEquals(HttpStatusCode.NotFound, client.get(path).status, path)
        }
    }
}
