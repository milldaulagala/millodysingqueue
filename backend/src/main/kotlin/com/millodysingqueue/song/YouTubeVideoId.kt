package com.millodysingqueue.song

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Extracts an ID locally. It never fetches URLs or checks video availability. */
object YouTubeVideoId {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
    private val embedHosts = setOf("youtube-nocookie.com", "www.youtube-nocookie.com")

    fun parse(value: String): String? {
        val input = value.trim()
        if (input.length > 2048) return null
        if (idPattern.matches(input)) return input
        return try {
            val uri = URI(if (input.contains("://")) input else "https://$input")
            if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.rawUserInfo != null || uri.port != -1) return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val path = uri.rawPath ?: return null
            val segments = path.split('/').drop(1)
            val id = when {
                host in setOf("youtu.be", "www.youtu.be") && segments.size == 1 -> segments.single()
                host in youtubeHosts && path == "/watch" -> {
                    val values = uri.rawQuery.orEmpty().split('&').map { part ->
                        val pair = part.split('=', limit = 2)
                        decode(pair[0]) to decode(pair.getOrElse(1) { "" })
                    }.filter { it.first == "v" }.map { it.second }
                    values.singleOrNull()
                }
                host in youtubeHosts && segments.size == 2 && segments[0] in setOf("embed", "shorts", "live") -> segments[1]
                host in embedHosts && segments.size == 2 && segments[0] == "embed" -> segments[1]
                else -> null
            }
            id?.takeIf { idPattern.matches(it) }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.net.URISyntaxException) {
            null
        }
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
