package com.millodysingqueue.client;

import java.util.Locale;

final class SongData {
    final String id;
    final String title;
    final String artist;
    final String videoId;

    SongData(String id, String title, String artist, String videoId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.videoId = videoId;
    }

    boolean matches(String query) {
        return (title + " " + artist).toLowerCase(Locale.ROOT).contains(query);
    }
}
