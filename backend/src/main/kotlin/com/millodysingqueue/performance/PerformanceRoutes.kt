package com.millodysingqueue.performance

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun Route.performanceRoutes(service: PerformanceService) {
    route("/api/events") {
        get {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(service.events().map { it.toResponse() })
        }
        post {
            val body = call.body(setOf("eventName", "eventDate", "organiserName"))
            call.respond(HttpStatusCode.Created, service.createEvent(body["eventName"], body["eventDate"], body["organiserName"]).toResponse())
        }
        route("/{eventId}/performances") {
            get {
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respond(service.queue(call.parameters["eventId"]).toResponse())
            }
            post {
                val body = call.body(setOf("singerId", "songId", "queueVersion"))
                call.respond(HttpStatusCode.Created, service.add(call.parameters["eventId"], body["singerId"], body["songId"], body["queueVersion"]).toResponse())
            }
            post("/{performanceId}/move") {
                val body = call.body(setOf("direction", "queueVersion"))
                call.respond(service.move(call.parameters["eventId"], call.parameters["performanceId"], body["direction"], body["queueVersion"]).toResponse())
            }
            post("/{performanceId}/cancel") {
                val body = call.body(setOf("queueVersion"))
                call.respond(service.cancel(call.parameters["eventId"], call.parameters["performanceId"], body["queueVersion"]).toResponse())
            }
        }
    }
}

private suspend fun ApplicationCall.body(allowed: Set<String>): Map<String, String?> {
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    if (!request.contentType().match(ContentType.Application.Json)) throw UnsupportedMediaTypeException(request.contentType())
    val body = receive<JsonObject>()
    if (body.keys.any { it !in allowed }) throw BadRequestException("Unknown field.")
    return body.mapValues { (_, value) ->
        when {
            value == JsonNull -> null
            value is JsonPrimitive && value.isString -> value.content
            else -> throw BadRequestException("Expected a string.")
        }
    }
}
