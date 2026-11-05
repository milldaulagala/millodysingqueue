package com.millodysingqueue.song

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeVideoIdTest {
    private val id = "7aNW9hRKYA8"

    @Test
    fun `accepts raw case-sensitive IDs and trims surrounding whitespace`() {
        assertEquals(id, YouTubeVideoId.parse("  $id  "))
        assertEquals("7ANW9hRKYA8", YouTubeVideoId.parse("7ANW9hRKYA8"))
        assertEquals("_Ab-cD12e34", YouTubeVideoId.parse("_Ab-cD12e34"))
    }

    @Test
    fun `extracts IDs from supported watch short embed shorts and live links`() {
        val links = listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://www.youtube.com/watch?v=$id&list=RD$id&start_radio=1&t=3s",
            "https://m.youtube.com/watch?t=3&v=$id#lyrics",
            "https://music.youtube.com/watch?v=$id",
            "youtube.com/watch?v=$id",
            "https://youtu.be/$id?si=share-token",
            "https://www.youtube.com/embed/$id?start=3",
            "https://www.youtube-nocookie.com/embed/$id",
            "https://youtube.com/shorts/$id",
            "https://youtube.com/live/$id",
            "http://www.youtube.com/watch?v=$id",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=$id",
        )
        for (link in links) assertEquals(id, YouTubeVideoId.parse(link), link)
    }

    @Test
    fun `rejects malformed IDs missing videos ambiguous queries and invalid paths`() {
        for (value in listOf("", "short", "$id-extra", "7aNW9hRKY!8", "https://youtube.com/",
            "https://youtube.com/watch?list=PL123", "https://youtube.com/watch?v=$id&v=$id",
            "https://youtu.be/$id/extra", "https://youtube.com/embed/", "https://youtu.be/%ZZ",
            "https://youtube.com/playlist?list=$id", "https://youtube.com/watch?v=%ZZ", "a".repeat(2049))) {
            assertNull(YouTubeVideoId.parse(value), value)
        }
    }

    @Test
    fun `rejects unrelated hosts spoofed hosts credentials and non-web schemes`() {
        for (value in listOf("https://example.com/watch?v=$id", "https://youtube.com.evil.test/watch?v=$id",
            "https://youtube.com@evil.test/watch?v=$id", "https://user@youtube.com/watch?v=$id",
            "https://youtube.com:8443/watch?v=$id", "javascript:alert(1)", "file:///watch?v=$id",
            "ftp://youtube.com/watch?v=$id", "https://evil.test/youtube.com/watch?v=$id")) {
            assertNull(YouTubeVideoId.parse(value), value)
        }
    }
}
