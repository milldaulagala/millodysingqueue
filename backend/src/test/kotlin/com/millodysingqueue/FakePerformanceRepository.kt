package com.millodysingqueue

import com.millodysingqueue.performance.*
import java.time.Instant

/** Test-only storage for HTTP and validation tests; PostgreSQL verifies concurrency. */
class FakePerformanceRepository : PerformanceRepository {
    private val events = mutableListOf<Event>()
    private val queues = mutableMapOf<Long, MutableList<Performance>>()
    var unavailable = false
    private fun available() { if (unavailable) throw PerformanceStorageException() }
    override suspend fun events(): List<Event> {
        available()
        return events.sortedWith(compareByDescending<Event> { it.eventDate }.thenByDescending { it.eventId })
    }
    override suspend fun createEvent(event: NewEvent): Event {
        available()
        return Event(events.size.toLong() + 1, event.eventName, event.eventDate, event.organiserName, 0, Instant.EPOCH).also {
            events.add(it)
            queues[it.eventId] = mutableListOf()
        }
    }
    override suspend fun queue(eventId: Long): PerformanceQueue {
        available()
        val event = events.find { it.eventId == eventId } ?: throw PerformanceNotFoundException()
        return PerformanceQueue(eventId, event.queueVersion, queues.getValue(eventId).filter { it.status == "WAITING" }.sortedBy { it.queuePosition })
    }
    private suspend fun check(eventId: Long, version: Long) {
        if (queue(eventId).queueVersion != version) throw StaleQueueException()
    }
    private suspend fun changed(eventId: Long): PerformanceQueue {
        val index = events.indexOfFirst { it.eventId == eventId }
        events[index] = events[index].copy(queueVersion = events[index].queueVersion + 1)
        return queue(eventId)
    }
    override suspend fun add(eventId: Long, singerId: Long, songId: Long, version: Long): PerformanceQueue {
        check(eventId, version)
        val rows = queues.getValue(eventId)
        rows.add(Performance(rows.size.toLong() + 1, singerId, "Singer", null, songId, "Song", "Artist",
            (rows.maxOfOrNull { it.queuePosition } ?: 0) + 1, "WAITING", Instant.EPOCH))
        return changed(eventId)
    }
    override suspend fun move(eventId: Long, performanceId: Long, direction: String, version: Long): PerformanceQueue {
        check(eventId, version)
        val ordered = queue(eventId).performances
        val index = ordered.indexOfFirst { it.performanceId == performanceId }
        if (index < 0) throw PerformanceNotFoundException()
        val neighbour = index + if (direction == "UP") -1 else 1
        if (neighbour !in ordered.indices) throw PerformanceValidationException(mapOf("direction" to "Cannot move further."))
        val rows = queues.getValue(eventId)
        for ((from, to) in listOf(index to neighbour, neighbour to index)) {
            val item = rows.indexOfFirst { it.performanceId == ordered[from].performanceId }
            rows[item] = rows[item].copy(queuePosition = ordered[to].queuePosition)
        }
        return changed(eventId)
    }
    override suspend fun cancel(eventId: Long, performanceId: Long, version: Long): PerformanceQueue {
        check(eventId, version)
        val rows = queues.getValue(eventId)
        val index = rows.indexOfFirst { it.performanceId == performanceId && it.status == "WAITING" }
        if (index < 0) throw PerformanceNotFoundException()
        rows[index] = rows[index].copy(status = "CANCELLED")
        return changed(eventId)
    }
}
