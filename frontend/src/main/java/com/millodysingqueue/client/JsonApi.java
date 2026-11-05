package com.millodysingqueue.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared same-origin JSON transport; screens decide how to present failures. */
final class JsonApi {
    interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String message, Map<String, String> fields);
    }

    private final String path;
    private final String loadFailure;
    private final String saveFailure;

    JsonApi(String path, String loadFailure, String saveFailure) {
        this.path = path;
        this.loadFailure = loadFailure;
        this.saveFailure = saveFailure;
    }

    void send(RequestBuilder.Method method, String payload, Callback<JSONValue> callback) {
        RequestBuilder builder = new RequestBuilder(method, GWT.getHostPageBaseURL() + path);
        builder.setHeader("Accept", "application/json");
        if (payload != null) builder.setHeader("Content-Type", "application/json");
        builder.setTimeoutMillis(15000);
        String networkMessage = payload == null
                ? loadFailure
                : saveFailure;
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

    static String requiredString(JSONObject object, String key) {
        JSONValue value = object.get(key);
        if (value == null || value.isString() == null) throw new IllegalArgumentException();
        return value.isString().stringValue();
    }

    static String optionalString(JSONObject object, String key) {
        JSONValue value = object.get(key);
        return value == null || value.isNull() != null ? "" : requiredString(object, key);
    }
}
