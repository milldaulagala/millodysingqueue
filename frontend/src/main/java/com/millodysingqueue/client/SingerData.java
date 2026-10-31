package com.millodysingqueue.client;

final class SingerData {
    final String id;
    final String name;
    final String nickname;
    final String email;

    SingerData(String id, String name, String nickname, String email) {
        this.id = id;
        this.name = name;
        this.nickname = nickname;
        this.email = email;
    }

    boolean matches(String query) {
        return (name + " " + nickname + " " + email).toLowerCase(java.util.Locale.ROOT).contains(query);
    }
}
