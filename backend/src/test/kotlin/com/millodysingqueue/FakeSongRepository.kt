package com.millodysingqueue

import com.millodysingqueue.song.DuplicateSongVideoException
import com.millodysingqueue.song.NewSong
import com.millodysingqueue.song.Song
import com.millodysingqueue.song.SongRepository
import com.millodysingqueue.song.SongStorageException
import java.time.Instant
import java.util.Locale

/** Test-only storage: runtime always uses PostgreSQL. */
class FakeSongRepository : SongRepository {
    val saved = mutableListOf<Song>()
    var unavailable = false

    override suspend fun create(song: NewSong): Song {
        if (unavailable) throw SongStorageException()
        if (saved.any { it.youtubeVideoId == song.youtubeVideoId }) throw DuplicateSongVideoException()
        return Song((saved.size + 1).toLong(), song.title, song.artist, song.youtubeVideoId,
            Instant.parse("2026-10-31T00:00:00Z")).also { saved.add(it) }
    }

    override suspend fun list(): List<Song> {
        if (unavailable) throw SongStorageException()
        return saved.sortedWith(compareBy<Song> { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.artist.lowercase(Locale.ROOT) }.thenBy { it.songId })
    }
}
