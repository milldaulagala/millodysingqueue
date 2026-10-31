package com.millodysingqueue.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MillodySingQueueEntryPoint implements EntryPoint {
    private final SingerApi api = new SingerApi();
    private final TextBox name = new TextBox();
    private final TextBox nickname = new TextBox();
    private final TextBox email = new TextBox();
    private final TextBox search = new TextBox();
    private final Label nameError = new Label();
    private final Label nicknameError = new Label();
    private final Label emailError = new Label();
    private final Label formMessage = liveMessage("form-message");
    private final Label directoryMessage = liveMessage("directory-message");
    private final Label count = new Label("—");
    private final Label results = new Label();
    private final FlowPanel rows = panel("singer-rows");
    private final Button register = new Button("Register singer");
    private final Button refresh = new Button("Refresh directory");
    private final List<SingerData> singers = new ArrayList<>();
    private boolean directoryLoaded;
    private boolean saving;
    private int directoryRequest;

    @Override
    public void onModuleLoad() {
        FlowPanel shell = panel("shell");
        FlowPanel header = panel("site-header");
        FlowPanel brand = panel("brand");
        brand.add(text("♪", "brand-mark"));
        FlowPanel brandText = panel("brand-copy");
        brandText.add(text("MillodySingQueue", "brand-name"));
        brandText.add(text("Every singer. Every song. Your night.", "brand-tagline"));
        brand.add(brandText);
        header.add(brand);
        header.add(text("Singer management", "header-context"));
        shell.add(header);

        FlowPanel content = panel("content");
        FlowPanel introduction = panel("introduction");
        FlowPanel introductionText = panel("intro-copy");
        introductionText.add(text("THE STAGE IS YOURS", "eyebrow"));
        introductionText.add(heading("Good nights start with great company.", 1));
        introductionText.add(text("Welcome your singers. Remember their names. Make everyone feel at home.", "intro-description"));
        introduction.add(introductionText);
        FlowPanel summary = panel("summary");
        count.setStyleName("summary-count");
        summary.add(count);
        summary.add(text("REGISTERED SINGERS", "summary-label"));
        introduction.add(summary);
        content.add(introduction);

        FlowPanel layout = panel("workspace");
        layout.add(registrationCard());
        layout.add(directoryCard());
        content.add(layout);
        content.add(text("A familiar face. A favourite song. A night to remember.", "page-footer"));
        shell.add(content);
        RootPanel.get("app").add(shell);
        loadSingers();
    }

    private FlowPanel registrationCard() {
        FlowPanel card = panel("card registration-card");
        card.add(text("A PLACE FOR EVERY VOICE", "eyebrow"));
        card.add(heading("Welcome to MillodySingQueue", 2));
        card.add(text("Just a name to get started. The rest is optional.", "card-description"));
        FormPanel form = new FormPanel();
        form.getElement().setAttribute("novalidate", "novalidate");
        FlowPanel fields = panel("registration-form");
        fields.add(field("Singer name", name, "singer-name", "Required · Their name or preferred singing name.", nameError));
        name.getElement().setAttribute("maxlength", "100");
        name.getElement().setAttribute("autocomplete", "name");
        name.getElement().setAttribute("aria-required", "true");
        fields.add(field("Nickname", nickname, "singer-nickname", "Optional · Helpful when two singers share a name.", nicknameError));
        nickname.getElement().setAttribute("maxlength", "100");
        nickname.getElement().setAttribute("autocomplete", "nickname");
        fields.add(field("Email", email, "singer-email", "Optional · Singers don't need an email to join.", emailError));
        email.getElement().setAttribute("type", "email");
        email.getElement().setAttribute("maxlength", "254");
        email.getElement().setAttribute("autocomplete", "email");
        fields.add(formMessage);
        register.setStyleName("button primary register-button");
        register.getElement().setAttribute("type", "submit");
        fields.add(register);
        form.setWidget(fields);
        form.addSubmitHandler(event -> {
            event.cancel();
            registerSinger();
        });
        card.add(form);
        card.add(text("Same name? No problem. Add a nickname to tell your singers apart.", "registration-note"));
        return card;
    }

    private FlowPanel directoryCard() {
        FlowPanel card = panel("card directory-card");
        FlowPanel top = panel("directory-top");
        FlowPanel titles = panel("directory-titles");
        titles.add(text("YOUR GROWING CHORUS", "eyebrow"));
        titles.add(heading("Singer directory", 2));
        top.add(titles);
        refresh.setStyleName("button secondary");
        refresh.addClickHandler(event -> loadSingers());
        top.add(refresh);
        card.add(top);
        card.add(text("Familiar faces, all in one place.", "card-description"));
        search.setStyleName("search-input");
        search.getElement().setId("singer-search");
        search.getElement().setAttribute("type", "search");
        search.getElement().setAttribute("placeholder", "Search name, nickname or email…");
        search.getElement().setAttribute("aria-label", "Search singers by name, nickname or email");
        search.addBitlessDomHandler(event -> renderDirectory(), InputEvent.TYPE);
        card.add(search);
        results.setStyleName("result-count");
        results.getElement().setAttribute("role", "status");
        card.add(results);
        card.add(directoryMessage);
        rows.getElement().setAttribute("role", "list");
        rows.getElement().setAttribute("aria-label", "Registered singers");
        card.add(rows);
        return card;
    }

    private void registerSinger() {
        if (saving) return;
        clearErrors();
        showMessage(formMessage, "Registering singer…", false);
        setSaving(true);
        api.create(name.getText(), nickname.getText(), email.getText(), new SingerApi.Callback<SingerData>() {
            public void onSuccess(SingerData singer) {
                setSaving(false);
                name.setText("");
                nickname.setText("");
                email.setText("");
                search.setText("");
                showMessage(formMessage, singer.name + " is registered. Welcome to the chorus!", false);
                name.setFocus(true);
                loadSingers();
            }
            public void onFailure(String message, Map<String, String> errors) {
                setSaving(false);
                applyError(name, nameError, errors.get("singerName"));
                applyError(nickname, nicknameError, errors.get("nickName"));
                applyError(email, emailError, errors.get("email"));
                showMessage(formMessage, message, true);
                if (errors.containsKey("singerName")) name.setFocus(true);
                else if (errors.containsKey("nickName")) nickname.setFocus(true);
                else if (errors.containsKey("email")) email.setFocus(true);
            }
        });
    }

    private void loadSingers() {
        int requestId = ++directoryRequest;
        refresh.setEnabled(false);
        rows.getElement().setAttribute("aria-busy", "true");
        showMessage(directoryMessage, "Loading your singers…", false);
        api.list(new SingerApi.Callback<List<SingerData>>() {
            public void onSuccess(List<SingerData> loaded) {
                if (requestId != directoryRequest) return;
                singers.clear();
                singers.addAll(loaded);
                directoryLoaded = true;
                count.setText(Integer.toString(singers.size()));
                directoryMessage.setVisible(false);
                finish();
                renderDirectory();
            }
            public void onFailure(String message, Map<String, String> errors) {
                if (requestId != directoryRequest) return;
                showMessage(directoryMessage, message + (directoryLoaded ? " Showing the last loaded directory." : ""), true);
                finish();
            }
            private void finish() {
                refresh.setEnabled(true);
                rows.getElement().setAttribute("aria-busy", "false");
            }
        });
    }

    private void renderDirectory() {
        rows.clear();
        if (!directoryLoaded) return;
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        int matched = 0;
        for (SingerData singer : singers) {
            if (!singer.matches(query)) continue;
            matched++;
            FlowPanel row = panel("singer-row");
            row.getElement().setAttribute("role", "listitem");
            row.add(text(singer.name.substring(0, 1).toUpperCase(Locale.ROOT), "avatar"));
            FlowPanel details = panel("singer-details");
            details.add(text(singer.name, "singer-name"));
            if (!singer.nickname.isEmpty()) details.add(text(singer.nickname, "singer-nickname"));
            details.add(text(singer.email.isEmpty() ? "No email added" : singer.email, "singer-email"));
            row.add(details);
            rows.add(row);
        }
        results.setText(query.isEmpty() ? matched + (matched == 1 ? " singer registered" : " singers registered")
                : "Showing " + matched + " of " + singers.size() + " singers");
        if (matched == 0) {
            FlowPanel empty = panel("empty-state");
            empty.add(text(query.isEmpty() ? "♪" : "⌕", "empty-icon"));
            empty.add(text(query.isEmpty() ? "Your chorus starts here" : "No matching singers", "empty-title"));
            empty.add(text(query.isEmpty() ? "Register your first singer and give every voice a place."
                    : "Try another name, nickname or email.", "empty-description"));
            rows.add(empty);
        }
    }

    private void setSaving(boolean value) {
        saving = value;
        register.setEnabled(!value);
        register.setText(value ? "Registering…" : "Register singer");
        name.setEnabled(!value);
        nickname.setEnabled(!value);
        email.setEnabled(!value);
    }

    private void clearErrors() {
        applyError(name, nameError, null);
        applyError(nickname, nicknameError, null);
        applyError(email, emailError, null);
    }

    private void applyError(TextBox input, Label label, String message) {
        input.getElement().setAttribute("aria-invalid", message == null ? "false" : "true");
        label.setText(message == null ? "" : message);
        label.setVisible(message != null);
    }

    private FlowPanel field(String title, TextBox input, String id, String hint, Label error) {
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

    private static FlowPanel panel(String style) {
        FlowPanel panel = new FlowPanel();
        panel.setStyleName(style);
        return panel;
    }

    private static Label text(String value, String style) {
        Label label = new Label(value);
        label.setStyleName(style);
        return label;
    }

    private static Label heading(String value, int level) {
        Label label = text(value, "heading heading-" + level);
        label.getElement().setAttribute("role", "heading");
        label.getElement().setAttribute("aria-level", Integer.toString(level));
        return label;
    }

    private static Label liveMessage(String id) {
        Label label = new Label();
        label.getElement().setId(id);
        label.getElement().setAttribute("role", "status");
        label.getElement().setAttribute("aria-live", "polite");
        label.setVisible(false);
        return label;
    }

    private static void showMessage(Label label, String value, boolean error) {
        label.setText(value);
        label.setStyleName("message " + (error ? "message-error" : "message-success"));
        label.setVisible(true);
    }
}
