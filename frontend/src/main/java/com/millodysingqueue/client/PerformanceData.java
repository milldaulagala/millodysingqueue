package com.millodysingqueue.client;

final class PerformanceData {
    final String performanceId;
    final String singerId;
    final String singerName;
    final String nickName;
    final String songId;
    final String songTitle;
    final String artist;
    final String queuePosition;
    final String status;

    PerformanceData(String performanceId, String singerId, String singerName, String nickName,
            String songId, String songTitle, String artist, String queuePosition, String status) {
        this.performanceId = performanceId;
        this.singerId = singerId;
        this.singerName = singerName;
        this.nickName = nickName;
        this.songId = songId;
        this.songTitle = songTitle;
        this.artist = artist;
        this.queuePosition = queuePosition;
        this.status = status;
    }
}
