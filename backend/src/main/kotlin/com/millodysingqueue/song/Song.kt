package com.millodysingqueue.song

import java.time.Instant
import kotlinx.serialization.Serializable

data class Song(val songId: Long, val title: String, val artist: String, val youtubeVideoId: String, val createdAt: Instant)
data class NewSong(val title: String, val artist: String, val youtubeVideoId: String)

@Serializable
data class SaveSongRequest(val title: String? = null, val artist: String? = null, val youtubeVideo: String? = null)

@Serializable
data class SongResponse(val songId: String, val title: String, val artist: String, val youtubeVideoId: String, val createdAt: String)

fun Song.toResponse() = SongResponse(songId.toString(), title, artist, youtubeVideoId, createdAt.toString())

class SongValidationException(val fieldErrors: Map<String, String>) : RuntimeException("Check the highlighted fields.")
class DuplicateSongVideoException : RuntimeException("This YouTube video is already saved. Choose a different karaoke version.")
class SongStorageException : RuntimeException("Song storage is temporarily unavailable.")

interface SongRepository {
    suspend fun create(song: NewSong): Song
    suspend fun list(): List<Song>
}
