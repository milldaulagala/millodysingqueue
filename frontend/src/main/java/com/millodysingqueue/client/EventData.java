package com.millodysingqueue.client;

final class EventData {
    final String id;
    final String name;
    final String date;
    final String organiser;

    EventData(String id, String name, String date, String organiser) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.organiser = organiser;
    }
}
