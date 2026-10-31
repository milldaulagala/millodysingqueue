package com.millodysingqueue.singer

import java.time.Instant
import kotlinx.serialization.Serializable

data class Singer(
    val singerId: Long,
    val singerName: String,
    val nickName: String?,
    val email: String?,
    val createdAt: Instant,
)

data class NewSinger(val singerName: String, val nickName: String?, val email: String?)

@Serializable
data class CreateSingerRequest(
    val singerName: String? = null,
    val nickName: String? = null,
    val email: String? = null,
)

@Serializable
data class SingerResponse(
    // A string preserves BIGINT precision in JavaScript clients.
    val singerId: String,
    val singerName: String,
    val nickName: String?,
    val email: String?,
    val createdAt: String,
)

fun Singer.toResponse() = SingerResponse(singerId.toString(), singerName, nickName, email, createdAt.toString())

class SingerValidationException(val fieldErrors: Map<String, String>) : RuntimeException("Check the highlighted fields.")
class DuplicateSingerNicknameException : RuntimeException("nickname already exists. please choose a different nickname")
class DuplicateSingerEmailException : RuntimeException("A singer with this email is already registered.")
class SingerStorageException : RuntimeException("Singer storage is temporarily unavailable.")

interface SingerRepository {
    suspend fun create(singer: NewSinger): Singer
    suspend fun list(): List<Singer>
}
