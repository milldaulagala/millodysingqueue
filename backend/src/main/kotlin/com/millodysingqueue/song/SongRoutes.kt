package com.millodysingqueue.song

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

fun Route.songRoutes(service: SongService) {
    route("/api/songs") {
        get {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(service.list().map { it.toResponse() })
        }
        post {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            if (!call.request.contentType().match(ContentType.Application.Json)) {
                throw UnsupportedMediaTypeException(call.request.contentType())
            }
            call.respond(HttpStatusCode.Created, service.save(call.receive<JsonObject>().toSongRequest()).toResponse())
        }
    }
}

private fun JsonObject.toSongRequest(): SaveSongRequest {
    if (keys.any { it !in setOf("title", "artist", "youtubeVideo") }) throw BadRequestException("Unknown song field.")
    fun optionalString(name: String): String? = when (val value = get(name)) {
        null, JsonNull -> null
        is JsonPrimitive -> if (value.isString) value.content else throw BadRequestException("Expected a string.")
        else -> throw BadRequestException("Expected a string.")
    }
    return SaveSongRequest(optionalString("title"), optionalString("artist"), optionalString("youtubeVideo"))
}
