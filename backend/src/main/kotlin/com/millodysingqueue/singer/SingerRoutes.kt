package com.millodysingqueue.singer

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

fun Route.singerRoutes(service: SingerService) {
    route("/api/singers") {
        get {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(service.list().map { it.toResponse() })
        }
        post {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            if (!call.request.contentType().match(ContentType.Application.Json)) {
                throw UnsupportedMediaTypeException(call.request.contentType())
            }
            val request = call.receive<JsonObject>().toRegistration()
            call.respond(HttpStatusCode.Created, service.register(request).toResponse())
        }
    }
}

private fun JsonObject.toRegistration(): CreateSingerRequest {
    if (keys.any { it !in setOf("singerName", "nickName", "email") }) {
        throw BadRequestException("Unknown registration field.")
    }
    fun optionalString(name: String): String? = when (val value = get(name)) {
        null, JsonNull -> null
        is JsonPrimitive -> if (value.isString) value.content else throw BadRequestException("Expected a string.")
        else -> throw BadRequestException("Expected a string.")
    }
    return CreateSingerRequest(optionalString("singerName"), optionalString("nickName"), optionalString("email"))
}
