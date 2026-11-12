package com.millodysingqueue.performance

import com.millodysingqueue.FakePerformanceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PerformanceServiceTest {
    @Test
    fun `event values are trimmed and sorted by date then ID`() = runTest {
        val service = PerformanceService(FakePerformanceRepository())
        val first = service.createEvent("  Friday at Mill’s ", "2026-11-13", " Mill ")
        assertEquals("Friday at Mill’s", first.eventName)
        assertEquals("Mill", first.organiserName)
        service.createEvent("Earlier", "2026-11-01", "Mick")
        val latest = service.createEvent("Another Friday", "2026-11-13", "Anne")
        assertEquals(listOf(latest.eventId, first.eventId, 2L), service.events().map { it.eventId })
    }

    @Test
    fun `event validation rejects invalid dates blank long and control character values without saving`() = runTest {
        val service = PerformanceService(FakePerformanceRepository())
        for (date in listOf(null, "", "2026-02-30", "2026-2-01", "0000-01-01", "2026-11-13T00:00:00Z")) {
            val failure = assertFailsWith<PerformanceValidationException> { service.createEvent(" ", date, "\t") }
            assertEquals(setOf("eventName", "eventDate", "organiserName"), failure.fields.keys)
        }
        for (value in listOf("x".repeat(201), "bad\nvalue")) {
            assertFailsWith<PerformanceValidationException> { service.createEvent(value, "2026-11-13", value) }
        }
        assertTrue(service.events().isEmpty())
        service.createEvent("x".repeat(200), "2024-02-29", "x".repeat(200))
    }

    @Test
    fun `invalid IDs versions and directions cannot mutate the queue`() = runTest {
        val service = PerformanceService(FakePerformanceRepository())
        service.createEvent("Friday", "2026-11-13", "Mill")
        for (id in listOf(null, "", "0", "-1", "abc", "9223372036854775808")) {
            assertFailsWith<PerformanceValidationException> { service.add("1", id, "1", "0") }
            assertFailsWith<PerformanceValidationException> { service.queue(id) }
        }
        for (version in listOf(null, "", "-1", "1.5", "9223372036854775808")) {
            assertFailsWith<PerformanceValidationException> { service.add("1", "1", "1", version) }
        }
        assertFailsWith<PerformanceValidationException> { service.move("1", "1", "SIDEWAYS", "0") }
        assertTrue(service.queue("1").performances.isEmpty())
    }
}
