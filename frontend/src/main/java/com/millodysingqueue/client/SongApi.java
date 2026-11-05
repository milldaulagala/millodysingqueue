package com.millodysingqueue.client;

import static com.millodysingqueue.client.JsonApi.requiredString;

import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SongApi {
    interface Callback<T> extends JsonApi.Callback<T> {}
    private final JsonApi api = new JsonApi("api/songs",
            "Unable to load songs. Check your connection and refresh the library.",
            "Unable to confirm the save. Refresh the library before trying again.");

    void list(Callback<List<SongData>> callback) {
        api.send(RequestBuilder.GET, null, new JsonApi.Callback<JSONValue>() {
            public void onSuccess(JSONValue result) {
                List<SongData> songs = new ArrayList<>();
                try {
                    JSONArray array = result.isArray();
                    if (array == null) throw new IllegalArgumentException();
                    for (int i = 0; i < array.size(); i++) songs.add(parseSong(array.get(i)));
                } catch (RuntimeException exception) {
                    callback.onFailure("The song library could not be read. Please refresh it.", Map.of());
                    return;
                }
                callback.onSuccess(songs);
            }
            public void onFailure(String message, Map<String, String> fields) {
                callback.onFailure(message, fields);
            }
        });
    }

    void save(String title, String artist, String video, Callback<SongData> callback) {
        JSONObject payload = new JSONObject();
        payload.put("title", new JSONString(title));
        payload.put("artist", new JSONString(artist));
        payload.put("youtubeVideo", new JSONString(video));
        api.send(RequestBuilder.POST, payload.toString(), new JsonApi.Callback<JSONValue>() {
            public void onSuccess(JSONValue result) {
                SongData song;
                try {
                    song = parseSong(result);
                } catch (RuntimeException exception) {
                    callback.onFailure("Unable to confirm the save. Refresh the library before trying again.", Map.of());
                    return;
                }
                callback.onSuccess(song);
            }
            public void onFailure(String message, Map<String, String> fields) {
                callback.onFailure(message, fields);
            }
        });
    }

    private SongData parseSong(JSONValue value) {
        JSONObject object = value.isObject();
        if (object == null) throw new IllegalArgumentException();
        String videoId = requiredString(object, "youtubeVideoId");
        if (!videoId.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException();
        return new SongData(requiredString(object, "songId"), requiredString(object, "title"),
                requiredString(object, "artist"), videoId);
    }
}
