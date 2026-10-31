package com.millodysingqueue

import com.millodysingqueue.database.ApplicationDatabase
import com.millodysingqueue.singer.DuplicateSingerEmailException
import com.millodysingqueue.singer.DuplicateSingerNicknameException
import com.millodysingqueue.singer.ExposedSingerRepository
import com.millodysingqueue.singer.SingerService
import com.millodysingqueue.singer.SingerStorageException
import com.millodysingqueue.singer.SingerValidationException
import com.millodysingqueue.singer.singerRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

fun Application.module() {
    val database = ApplicationDatabase(environment.config)
    monitor.subscribe(ApplicationStopped) { database.close() }
    try {
        configureApplication(SingerService(ExposedSingerRepository(database.database)))
    } catch (exception: Exception) {
        database.close()
        throw exception
    }
}

/** Explicit dependencies keep API tests independent of production connections. */
fun Application.configureApplication(singers: SingerService) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<SingerValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError("VALIDATION_ERROR", cause.message!!, cause.fieldErrors))
        }
        exception<DuplicateSingerNicknameException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ApiError("NICKNAME_ALREADY_EXISTS", cause.message!!,
                mapOf("nickName" to cause.message!!)))
        }
        exception<DuplicateSingerEmailException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ApiError("EMAIL_ALREADY_EXISTS", cause.message!!,
                mapOf("email" to cause.message!!)))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "Send a valid JSON singer registration."))
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ApiError("UNSUPPORTED_MEDIA_TYPE", "Use application/json."))
        }
        exception<SingerStorageException> { call, _ ->
            call.respond(HttpStatusCode.ServiceUnavailable,
                ApiError("STORAGE_UNAVAILABLE", "Unable to access singers right now. Please try again."))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            // Avoid logging singer input, database statements or credentials.
            this@configureApplication.log.error("Request failed ({})", cause.javaClass.simpleName)
            call.respond(HttpStatusCode.InternalServerError, ApiError("INTERNAL_ERROR", "Something went wrong. Please try again."))
        }
    }
    routing {
        get("/api/health") {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(HealthResponse(status = "UP"))
        }
        singerRoutes(singers)
        staticResources("/", "static")
    }
}

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class ApiError(val code: String, val message: String, val fieldErrors: Map<String, String> = emptyMap())
