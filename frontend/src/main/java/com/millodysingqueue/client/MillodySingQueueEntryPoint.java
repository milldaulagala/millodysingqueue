package com.millodysingqueue.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;

public final class MillodySingQueueEntryPoint implements EntryPoint {
    private final Label status = new Label();
    private final Button checkConnection = new Button("Check connection");

    @Override
    public void onModuleLoad() {
        FlowPanel panel = new FlowPanel();
        panel.setStyleName("foundation-panel");

        Label title = new Label("MillodySingQueue");
        title.setStyleName("app-title");
        title.getElement().setAttribute("role", "heading");
        title.getElement().setAttribute("aria-level", "1");
        panel.add(title);

        Label subtitle = new Label("Every singer. Every song. Your night.");
        subtitle.setStyleName("subtitle");
        panel.add(subtitle);

        status.getElement().setAttribute("role", "status");
        status.getElement().setAttribute("aria-live", "polite");
        status.getElement().setId("connection-status");
        panel.add(status);

        checkConnection.addClickHandler(event -> checkBackend());
        panel.add(checkConnection);
        RootPanel.get("app").add(panel);
        checkBackend();
    }

    private void checkBackend() {
        setStatus("Checking backend connection…", "checking");
        checkConnection.setEnabled(false);

        RequestBuilder builder = new RequestBuilder(
                RequestBuilder.GET, GWT.getHostPageBaseURL() + "api/health");
        builder.setHeader("Accept", "application/json");
        builder.setTimeoutMillis(5000);

        try {
            builder.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    checkConnection.setEnabled(true);
                    if (response.getStatusCode() != 200) {
                        showFailure();
                        return;
                    }
                    try {
                        JSONObject body = JSONParser.parseStrict(response.getText()).isObject();
                        JSONValue value = body == null ? null : body.get("status");
                        if (value != null && value.isString() != null
                                && "UP".equals(value.isString().stringValue())) {
                            setStatus("Backend connected", "connected");
                        } else {
                            showFailure();
                        }
                    } catch (RuntimeException exception) {
                        showFailure();
                    }
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    checkConnection.setEnabled(true);
                    showFailure();
                }
            });
        } catch (RequestException exception) {
            checkConnection.setEnabled(true);
            showFailure();
        }
    }

    private void showFailure() {
        setStatus("Unable to connect to the backend. Please try again.", "disconnected");
    }

    private void setStatus(String message, String state) {
        status.setText(message);
        status.setStyleName("connection-status " + state);
    }
}
