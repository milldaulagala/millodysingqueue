package com.millodysingqueue.client;

import static com.millodysingqueue.client.JsonApi.requiredString;
import static com.millodysingqueue.client.JsonApi.optionalString;

import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PerformanceApi {
    interface Callback<T> extends JsonApi.Callback<T> {}
    interface Decoder<T> { T decode(JSONValue value); }
    static final class QueueData {
        final String eventId;
        final String version;
        final List<PerformanceData> performances;
        QueueData(String eventId, String version, List<PerformanceData> performances) {
            this.eventId = eventId;
            this.version = version;
            this.performances = performances;
        }
    }

    void events(Callback<List<EventData>> callback) {
        request("api/events", null, 200, value -> {
            JSONArray array = array(value);
            List<EventData> events = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) events.add(event(array.get(i)));
            return events;
        }, callback);
    }

    void create(String name, String date, String organiser, Callback<EventData> callback) {
        JSONObject body = new JSONObject();
        body.put("eventName", new JSONString(name));
        body.put("eventDate", new JSONString(date));
        body.put("organiserName", new JSONString(organiser));
        request("api/events", body, 201, this::event, callback);
    }

    void queue(String eventId, Callback<QueueData> callback) {
        request(path(eventId), null, 200, this::queue, callback);
    }

    void add(String eventId, String singerId, String songId, String version, Callback<QueueData> callback) {
        JSONObject body = version(version);
        body.put("singerId", new JSONString(singerId));
        body.put("songId", new JSONString(songId));
        request(path(eventId), body, 201, this::queue, callback);
    }

    void change(String eventId, String performanceId, String action, String direction, String version, Callback<QueueData> callback) {
        requireId(performanceId);
        JSONObject body = version(version);
        if (direction != null) body.put("direction", new JSONString(direction));
        request(path(eventId) + "/" + performanceId + "/" + action, body, 200, this::queue, callback);
    }

    private String path(String id) {
        requireId(id);
        return "api/events/" + id + "/performances";
    }
    private static void requireId(String value) {
        if (!value.matches("[1-9][0-9]*")) throw new IllegalArgumentException();
    }
    private JSONObject version(String value) {
        JSONObject result = new JSONObject();
        result.put("queueVersion", new JSONString(value));
        return result;
    }
    private EventData event(JSONValue value) {
        JSONObject object = object(value);
        String id = requiredString(object, "eventId");
        requireId(id);
        return new EventData(id, requiredString(object, "eventName"), requiredString(object, "eventDate"), requiredString(object, "organiserName"));
    }
    private QueueData queue(JSONValue value) {
        JSONObject object = object(value);
        String id = requiredString(object, "eventId");
        requireId(id);
        String version = requiredString(object, "queueVersion");
        if (!version.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException();
        List<PerformanceData> rows = new ArrayList<>();
        JSONArray array = array(object.get("performances"));
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = object(array.get(i));
            String performanceId = requiredString(item, "performanceId");
            requireId(performanceId);
            String status = requiredString(item, "status");
            if (!"WAITING".equals(status)) throw new IllegalArgumentException();
            rows.add(new PerformanceData(performanceId, requiredString(item, "singerId"), requiredString(item, "singerName"),
                    optionalString(item, "nickName"), requiredString(item, "songId"), requiredString(item, "songTitle"),
                    requiredString(item, "artist"), requiredString(item, "queuePosition"), status));
        }
        return new QueueData(id, version, rows);
    }
    private static JSONObject object(JSONValue value) {
        if (value == null || value.isObject() == null) throw new IllegalArgumentException();
        return value.isObject();
    }
    private static JSONArray array(JSONValue value) {
        if (value == null || value.isArray() == null) throw new IllegalArgumentException();
        return value.isArray();
    }
    private <T> void request(String path, JSONObject body, int status, Decoder<T> decoder, Callback<T> callback) {
        JsonApi api = new JsonApi(path, "Unable to load nights or performances. Please refresh and try again.",
                "Unable to confirm the change. Refresh before trying again; it may already have been saved.");
        api.send(body == null ? RequestBuilder.GET : RequestBuilder.POST, body == null ? null : body.toString(), status,
                new JsonApi.Callback<JSONValue>() {
                    public void onSuccess(JSONValue value) {
                        T result;
                        try { result = decoder.decode(value); }
                        catch (RuntimeException exception) {
                            callback.onFailure("Unable to read the response. Refresh before trying again.", Map.of());
                            return;
                        }
                        callback.onSuccess(result);
                    }
                    public void onFailure(String message, Map<String, String> fields) { callback.onFailure(message, fields); }
                });
    }
}
