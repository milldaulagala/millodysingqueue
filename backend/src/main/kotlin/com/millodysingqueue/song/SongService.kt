package com.millodysingqueue.song

class SongService(private val repository: SongRepository) {
    suspend fun save(request: SaveSongRequest): Song {
        val title = request.title?.trim().orEmpty()
        val artist = request.artist?.trim().orEmpty()
        val videoId = YouTubeVideoId.parse(request.youtubeVideo.orEmpty())
        val errors = linkedMapOf<String, String>()
        validateText(title, "title", "song title", errors)
        validateText(artist, "artist", "artist", errors)
        if (videoId == null) errors["youtubeVideo"] = "Enter a valid YouTube link or an 11-character video ID."
        if (errors.isNotEmpty()) throw SongValidationException(errors)
        return repository.create(NewSong(title, artist, requireNotNull(videoId)))
    }

    suspend fun list(): List<Song> = repository.list()

    private fun validateText(value: String, field: String, label: String, errors: MutableMap<String, String>) {
        when {
            value.isBlank() -> errors[field] = "Enter the $label."
            value.length > 200 -> errors[field] = "Use 200 characters or fewer."
            value.any { it.isISOControl() } -> errors[field] = "Remove control characters from the $label."
        }
    }
}
