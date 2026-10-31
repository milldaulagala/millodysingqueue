package com.millodysingqueue.singer

import com.millodysingqueue.FakeSingerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SingerServiceTest {
    @Test
    fun `normalises names and optional fields without changing email case`() = runTest {
        val service = SingerService(FakeSingerRepository())
        val singer = service.register(CreateSingerRequest("  Mick Smith  ", "  Work Mick ", " Mick@Example.com "))
        assertEquals("Mick Smith", singer.singerName)
        assertEquals("Work Mick", singer.nickName)
        assertEquals("Mick@Example.com", singer.email)
        val minimal = service.register(CreateSingerRequest("Mill", "  ", "\t"))
        assertNull(minimal.nickName)
        assertNull(minimal.email)
    }

    @Test
    fun `rejects absent blank long or control-containing names before saving`() = runTest {
        val repository = FakeSingerRepository()
        val service = SingerService(repository)
        for (name in listOf(null, "", "  ", "\t\n", "a".repeat(101), "Mi\u0000ll", "Mi\nll")) {
            val error = assertFailsWith<SingerValidationException> { service.register(CreateSingerRequest(name)) }
            assertTrue(error.fieldErrors.containsKey("singerName"))
        }
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `validates nickname length and email format with useful field errors`() = runTest {
        val service = SingerService(FakeSingerRepository())
        for (email in listOf("mill", "mill@", "@example.com", "mill@example", "mill @example.com", "mill@example..com", "a".repeat(243) + "@example.com")) {
            assertTrue(assertFailsWith<SingerValidationException> {
                service.register(CreateSingerRequest("Mill", email = email))
            }.fieldErrors.containsKey("email"))
        }
        val error = assertFailsWith<SingerValidationException> {
            service.register(CreateSingerRequest("", "n".repeat(101), "invalid"))
        }
        assertEquals(setOf("singerName", "nickName", "email"), error.fieldErrors.keys)
    }

    @Test
    fun `supports duplicate names unicode and names at the length limit`() = runTest {
        val repository = FakeSingerRepository()
        val service = SingerService(repository)
        service.register(CreateSingerRequest("Mick"))
        service.register(CreateSingerRequest("Mick"))
        service.register(CreateSingerRequest("මිල්"))
        service.register(CreateSingerRequest("a".repeat(100)))
        assertEquals(4, repository.saved.size)
    }
}
