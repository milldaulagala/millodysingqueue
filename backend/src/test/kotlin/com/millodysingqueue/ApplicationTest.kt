package com.millodysingqueue

import com.millodysingqueue.singer.SingerService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ApplicationTest {
    @Test
    fun `health endpoint returns an uncached JSON status`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository())) }
        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(buildJsonObject { put("status", "UP") }, Json.parseToJsonElement(response.bodyAsText()))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `frontend and compiled bootstrap are served by the application`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository())) }
        val page = client.get("/")

        assertEquals(HttpStatusCode.OK, page.status)
        assertEquals(ContentType.Text.Html, page.contentType()?.withoutParameters())
        assertContains(page.bodyAsText(), "millodysingqueue/millodysingqueue.nocache.js")
        assertEquals(HttpStatusCode.OK, client.get("/app.css").status)
        assertEquals(HttpStatusCode.OK, client.get("/millodysingqueue/millodysingqueue.nocache.js").status)
    }

    @Test
    fun `unknown API paths and build metadata are not served as the application page`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureApplication(SingerService(FakeSingerRepository())) }
        for (path in listOf(
            "/api/missing",
            "/WEB-INF/classes/com/millodysingqueue/client/MillodySingQueueEntryPoint.class",
            "/db/changelog/db.changelog-master.xml",
        )) {
            assertEquals(HttpStatusCode.NotFound, client.get(path).status, path)
        }
    }
}
