package com.millodysingqueue.client;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

/** Shared form semantics and presentation for admin screens. */
final class ScreenWidgets {
    private ScreenWidgets() {}

    static void applyError(TextBox input, Label label, String message) {
        input.getElement().setAttribute("aria-invalid", message == null ? "false" : "true");
        label.setText(message == null ? "" : message);
        label.setVisible(message != null);
    }

    static FlowPanel field(String title, TextBox input, String id, String hint, Label error) {
        FlowPanel field = panel("field");
        LabelElement label = Document.get().createLabelElement();
        label.setHtmlFor(id);
        label.setInnerText(title);
        field.getElement().appendChild(label);
        input.setStyleName("text-input");
        input.getElement().setId(id);
        input.getElement().setAttribute("aria-describedby", id + "-hint " + id + "-error");
        field.add(input);
        Label help = text(hint, "field-hint");
        help.getElement().setId(id + "-hint");
        field.add(help);
        error.setStyleName("field-error");
        error.getElement().setId(id + "-error");
        error.setVisible(false);
        field.add(error);
        return field;
    }

    static FlowPanel panel(String style) {
        FlowPanel panel = new FlowPanel();
        panel.setStyleName(style);
        return panel;
    }

    static Label text(String value, String style) {
        Label label = new Label(value);
        label.setStyleName(style);
        return label;
    }

    static Label heading(String value, int level) {
        Label label = text(value, "heading heading-" + level);
        label.getElement().setAttribute("role", "heading");
        label.getElement().setAttribute("aria-level", Integer.toString(level));
        return label;
    }

    static Label liveMessage(String id) {
        Label label = new Label();
        label.getElement().setId(id);
        label.getElement().setAttribute("role", "status");
        label.getElement().setAttribute("aria-live", "polite");
        label.setVisible(false);
        return label;
    }

    static void showMessage(Label label, String value, boolean error) {
        label.setText(value);
        label.setStyleName("message " + (error ? "message-error" : "message-success"));
        label.setVisible(true);
    }
}
