package com.millodysingqueue.performance

import java.time.LocalDate
import java.time.format.DateTimeParseException

class PerformanceService(private val repository: PerformanceRepository) {
    suspend fun events() = repository.events()

    suspend fun createEvent(name: String?, date: String?, organiser: String?): Event {
        val errors = linkedMapOf<String, String>()
        fun text(value: String?, field: String, label: String): String {
            val trimmed = value?.trim().orEmpty()
            when {
                trimmed.isBlank() -> errors[field] = "Enter the $label."
                trimmed.length > 200 -> errors[field] = "Use 200 characters or fewer."
                trimmed.any { it.isISOControl() } -> errors[field] = "Remove control characters from the $label."
            }
            return trimmed
        }
        val eventName = text(name, "eventName", "night name")
        val organiserName = text(organiser, "organiserName", "organiser’s name")
        val eventDate = try {
            date?.takeIf { it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) }?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) { null }
        if (eventDate == null || eventDate.year !in 1..9999) errors["eventDate"] = "Enter a valid date (YYYY-MM-DD)."
        if (errors.isNotEmpty()) throw PerformanceValidationException(errors)
        return repository.createEvent(NewEvent(eventName, requireNotNull(eventDate), organiserName))
    }

    fun id(value: String?, field: String): Long = value?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw PerformanceValidationException(mapOf(field to "Select a valid ${if (field == "eventId") "night" else if (field == "singerId") "singer" else if (field == "songId") "song" else "performance"}."))

    fun version(value: String?): Long = value?.toLongOrNull()?.takeIf { it >= 0 }
        ?: throw PerformanceValidationException(mapOf("queueVersion" to "Refresh the order before making a change."))

    suspend fun queue(event: String?) = repository.queue(id(event, "eventId"))
    suspend fun add(event: String?, singer: String?, song: String?, version: String?) =
        repository.add(id(event, "eventId"), id(singer, "singerId"), id(song, "songId"), version(version))

    suspend fun move(event: String?, performance: String?, direction: String?, version: String?): PerformanceQueue {
        if (direction !in setOf("UP", "DOWN")) throw PerformanceValidationException(mapOf("direction" to "Choose UP or DOWN."))
        return repository.move(id(event, "eventId"), id(performance, "performanceId"), requireNotNull(direction), version(version))
    }

    suspend fun cancel(event: String?, performance: String?, version: String?) =
        repository.cancel(id(event, "eventId"), id(performance, "performanceId"), version(version))
}
