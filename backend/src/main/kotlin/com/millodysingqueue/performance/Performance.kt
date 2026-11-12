package com.millodysingqueue.performance

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

data class Event(
    val eventId: Long, val eventName: String, val eventDate: LocalDate, val organiserName: String,
    val queueVersion: Long, val createdAt: Instant,
)
data class Performance(
    val performanceId: Long, val singerId: Long, val singerName: String, val nickName: String?,
    val songId: Long, val songTitle: String, val artist: String, val queuePosition: Long,
    val status: String, val createdAt: Instant,
)
data class PerformanceQueue(val eventId: Long, val queueVersion: Long, val performances: List<Performance>)

fun Event.toResponse() = EventResponse(eventId.toString(), eventName, eventDate.toString(), organiserName,
    queueVersion.toString(), createdAt.toString())
fun Performance.toResponse() = PerformanceResponse(performanceId.toString(), singerId.toString(), singerName,
    nickName, songId.toString(), songTitle, artist, queuePosition.toString(), status, createdAt.toString())
fun PerformanceQueue.toResponse() = QueueResponse(eventId.toString(), queueVersion.toString(), performances.map { it.toResponse() })

@Serializable
data class EventResponse(
    val eventId: String, val eventName: String, val eventDate: String, val organiserName: String,
    val queueVersion: String, val createdAt: String,
)

@Serializable
data class PerformanceResponse(
    val performanceId: String, val singerId: String, val singerName: String, val nickName: String?,
    val songId: String, val songTitle: String, val artist: String, val queuePosition: String,
    val status: String, val createdAt: String,
)

@Serializable
data class QueueResponse(val eventId: String, val queueVersion: String, val performances: List<PerformanceResponse>)

data class NewEvent(val eventName: String, val eventDate: LocalDate, val organiserName: String)

interface PerformanceRepository {
    suspend fun events(): List<Event>
    suspend fun createEvent(event: NewEvent): Event
    suspend fun queue(eventId: Long): PerformanceQueue
    suspend fun add(eventId: Long, singerId: Long, songId: Long, version: Long): PerformanceQueue
    suspend fun move(eventId: Long, performanceId: Long, direction: String, version: Long): PerformanceQueue
    suspend fun cancel(eventId: Long, performanceId: Long, version: Long): PerformanceQueue
}

class PerformanceValidationException(val fields: Map<String, String>) : RuntimeException("Check the highlighted fields.")
class PerformanceNotFoundException : RuntimeException("The requested night or performance was not found.")
class StaleQueueException : RuntimeException("The singing order changed. Refresh the order and try again.")
class PerformanceStorageException : RuntimeException("Unable to access nights and performances right now. Please try again.")
