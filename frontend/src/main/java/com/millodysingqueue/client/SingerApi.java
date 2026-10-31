package com.millodysingqueue.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON transport only; widgets and validation presentation belong to the screen. */
final class SingerApi {
    interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String message, Map<String, String> fields);
    }

    void list(Callback<List<SingerData>> callback) {
        send(RequestBuilder.GET, null, new Callback<JSONValue>() {
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
        send(RequestBuilder.POST, payload.toString(), new Callback<JSONValue>() {
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

    private void send(RequestBuilder.Method method, String payload, Callback<JSONValue> callback) {
        RequestBuilder builder = new RequestBuilder(method, GWT.getHostPageBaseURL() + "api/singers");
        builder.setHeader("Accept", "application/json");
        if (payload != null) builder.setHeader("Content-Type", "application/json");
        builder.setTimeoutMillis(15000);
        String networkMessage = payload == null
                ? "Unable to load singers. Check your connection and refresh the directory."
                : "Unable to confirm registration. Refresh the directory before trying again.";
        try {
            builder.sendRequest(payload, new RequestCallback() {
                public void onResponseReceived(Request request, Response response) {
                    JSONValue body;
                    try {
                        body = JSONParser.parseStrict(response.getText());
                    } catch (RuntimeException exception) {
                        callback.onFailure(networkMessage, Map.of());
                        return;
                    }
                    if (response.getStatusCode() == (payload == null ? 200 : 201)) {
                        callback.onSuccess(body);
                        return;
                    }
                    JSONObject error = body.isObject();
                    Map<String, String> fields = new LinkedHashMap<>();
                    String message = networkMessage;
                    if (error != null) {
                        if (error.get("message") != null && error.get("message").isString() != null) {
                            message = error.get("message").isString().stringValue();
                        }
                        JSONValue values = error.get("fieldErrors");
                        if (values != null && values.isObject() != null) {
                            for (String key : values.isObject().keySet()) {
                                JSONValue value = values.isObject().get(key);
                                if (value.isString() != null) fields.put(key, value.isString().stringValue());
                            }
                        }
                    }
                    callback.onFailure(message, fields);
                }
                public void onError(Request request, Throwable exception) {
                    callback.onFailure(networkMessage, Map.of());
                }
            });
        } catch (RequestException exception) {
            callback.onFailure(networkMessage, Map.of());
        }
    }

    private SingerData parseSinger(JSONValue value) {
        JSONObject object = value.isObject();
        if (object == null) throw new IllegalArgumentException();
        return new SingerData(requiredString(object, "singerId"), requiredString(object, "singerName"),
                optionalString(object, "nickName"), optionalString(object, "email"));
    }

    private String requiredString(JSONObject object, String key) {
        JSONValue value = object.get(key);
        if (value == null || value.isString() == null) throw new IllegalArgumentException();
        return value.isString().stringValue();
    }

    private String optionalString(JSONObject object, String key) {
        JSONValue value = object.get(key);
        return value == null || value.isNull() != null ? "" : requiredString(object, key);
    }
}
