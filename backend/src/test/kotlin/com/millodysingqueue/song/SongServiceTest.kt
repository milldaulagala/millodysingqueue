package com.millodysingqueue.song

import com.millodysingqueue.FakeSongRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SongServiceTest {
    @Test
    fun `normalises text and stores only the extracted video ID`() = runTest {
        val repository = FakeSongRepository()
        val song = SongService(repository).save(SaveSongRequest("  Take Me Home, Country Roads  ",
            " John Denver ", "https://youtu.be/7aNW9hRKYA8?t=3"))
        assertEquals("Take Me Home, Country Roads", song.title)
        assertEquals("John Denver", song.artist)
        assertEquals("7aNW9hRKYA8", song.youtubeVideoId)
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun `reports all invalid fields and does not save`() = runTest {
        val repository = FakeSongRepository()
        val service = SongService(repository)
        for (request in listOf(SaveSongRequest(), SaveSongRequest(" ", "\t", "bad"),
            SaveSongRequest("t".repeat(201), "a".repeat(201), "bad"),
            SaveSongRequest("Bad\nTitle", "Bad\u0000Artist", "bad"))) {
            val failure = assertFailsWith<SongValidationException> { service.save(request) }
            assertEquals(setOf("title", "artist", "youtubeVideo"), failure.fieldErrors.keys)
        }
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `same video in another URL conflicts but another version is allowed`() = runTest {
        val service = SongService(FakeSongRepository())
        service.save(SaveSongRequest("Country Roads", "John Denver", "7aNW9hRKYA8"))
        assertFailsWith<DuplicateSongVideoException> {
            service.save(SaveSongRequest("Another title", "Another artist", "https://youtube.com/watch?v=7aNW9hRKYA8"))
        }
        service.save(SaveSongRequest("Country Roads", "John Denver", "_Ab-cD12e34"))
        assertEquals(2, service.list().size)
    }

    @Test
    fun `supports unicode and titles and artists at the length limit`() = runTest {
        val service = SongService(FakeSongRepository())
        service.save(SaveSongRequest("සිංදු", "ගායකයා", "7aNW9hRKYA8"))
        val song = service.save(SaveSongRequest("t".repeat(200), "a".repeat(200), "_Ab-cD12e34"))
        assertEquals(200, song.title.length)
        assertEquals(200, song.artist.length)
    }
}
