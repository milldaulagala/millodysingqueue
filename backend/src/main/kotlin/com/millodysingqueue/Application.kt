package com.millodysingqueue

import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/api/health") {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(HealthResponse(status = "UP"))
        }

        staticResources("/", "static")
    }
}

@Serializable
data class HealthResponse(val status: String)
