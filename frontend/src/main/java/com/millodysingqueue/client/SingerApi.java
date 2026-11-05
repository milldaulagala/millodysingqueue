package com.millodysingqueue.client;

import static com.millodysingqueue.client.JsonApi.optionalString;
import static com.millodysingqueue.client.JsonApi.requiredString;

import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JSON transport only; widgets and validation presentation belong to the screen. */
final class SingerApi {
    interface Callback<T> extends JsonApi.Callback<T> {}
    private final JsonApi api = new JsonApi("api/singers",
            "Unable to load singers. Check your connection and refresh the directory.",
            "Unable to confirm registration. Refresh the directory before trying again.");

    void list(Callback<List<SingerData>> callback) {
        api.send(RequestBuilder.GET, null, new JsonApi.Callback<JSONValue>() {
            public void onSuccess(JSONValue result) {
                List<SingerData> singers = new ArrayList<>();
                try {
                    JSONArray array = result.isArray();
                    if (array == null) throw new IllegalArgumentException();
                    for (int i = 0; i < array.size(); i++) singers.add(parseSinger(array.get(i)));
                } catch (RuntimeException exception) {
                    callback.onFailure("The singer directory could not be read. Please refresh it.", Map.of());
                    return;
                }
                callback.onSuccess(singers);
            }
            public void onFailure(String message, Map<String, String> fields) {
                callback.onFailure(message, fields);
            }
        });
    }

    void create(String name, String nickname, String email, Callback<SingerData> callback) {
        JSONObject payload = new JSONObject();
        payload.put("singerName", new JSONString(name));
        payload.put("nickName", new JSONString(nickname));
        payload.put("email", new JSONString(email));
        api.send(RequestBuilder.POST, payload.toString(), new JsonApi.Callback<JSONValue>() {
            public void onSuccess(JSONValue result) {
                SingerData singer;
                try {
                    singer = parseSinger(result);
                } catch (RuntimeException exception) {
                    callback.onFailure("Unable to confirm registration. Refresh the directory before trying again.", Map.of());
                    return;
                }
                callback.onSuccess(singer);
            }
            public void onFailure(String message, Map<String, String> fields) {
                callback.onFailure(message, fields);
            }
        });
    }

    private SingerData parseSinger(JSONValue value) {
        JSONObject object = value.isObject();
        if (object == null) throw new IllegalArgumentException();
        return new SingerData(requiredString(object, "singerId"), requiredString(object, "singerName"),
                optionalString(object, "nickName"), optionalString(object, "email"));
    }

}
