package com.millodysingqueue.client;

import static com.millodysingqueue.client.ScreenWidgets.applyError;
import static com.millodysingqueue.client.ScreenWidgets.field;
import static com.millodysingqueue.client.ScreenWidgets.heading;
import static com.millodysingqueue.client.ScreenWidgets.liveMessage;
import static com.millodysingqueue.client.ScreenWidgets.panel;
import static com.millodysingqueue.client.ScreenWidgets.showMessage;
import static com.millodysingqueue.client.ScreenWidgets.text;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PerformanceScreen extends FlowPanel {
    private final PerformanceApi api = new PerformanceApi();
    private final List<EventData> events = new ArrayList<>();
    private final List<PerformanceData> performances = new ArrayList<>();
    private final List<SongData> songs = new ArrayList<>();
    private final ListBox eventSelect = new ListBox();
    private final ListBox singerSelect = new ListBox();
    private final ListBox songSelect = new ListBox();
    private final TextBox songSearch = new TextBox();
    private final TextBox eventName = new TextBox();
    private final TextBox eventDate = new TextBox();
    private final TextBox organiser = new TextBox();
    private final Label nameError = new Label();
    private final Label dateError = new Label();
    private final Label organiserError = new Label();
    private final Label singerError = new Label();
    private final Label songError = new Label();
    private final FlowPanel eventDetails = panel("event-details");
    private final Label message = liveMessage("performance-message");
    private final Label createMessage = liveMessage("create-night-message");
    private final Label choiceMessage = liveMessage("performance-choices-message");
    private final Label totals = text("Choose a night to get started.", "card-description");
    private final Label saved = text("", "queue-badge");
    private final FlowPanel rows = panel("performance-rows");
    private final FlowPanel removal = panel("card removal-confirmation");
    private final Label removalText = text("", "card-description");
    private final Button keep = new Button("Keep performance");
    private PerformanceData pendingRemoval;
    private final FlowPanel first = panel("card first-card");
    private final Button createToggle = new Button("Create night");
    private final Button create = new Button("Create night");
    private final Button add = new Button("+ Add performance");
    private final Button refresh = new Button("Refresh order");
    private final FlowPanel createCard;
    private String eventId = "";
    private String version = "0";
    private boolean busy;
    private boolean queueReady;
    private boolean singersReady;
    private boolean songsReady;
    private int eventRequest;
    private int queueRequest;
    private int choiceRequest;

    PerformanceScreen() {
        setStyleName("content performance-content");
        FlowPanel intro = panel("introduction");
        FlowPanel copy = panel("intro-copy");
        copy.add(text("THE STAGE IS YOURS", "eyebrow"));
        copy.add(heading("A stage for every voice.", 1));
        copy.add(text("Choose a night. Build the order. Let everyone shine.", "intro-description"));
        intro.add(copy);
        add(intro);
        FlowPanel strip = panel("card event-strip");
        eventSelect.setStyleName("text-input event-select");
        eventSelect.getElement().setId("performance-event");
        LabelElement label = Document.get().createLabelElement();
        label.setHtmlFor("performance-event");
        label.setInnerText("Karaoke night");
        strip.getElement().appendChild(label);
        strip.add(eventSelect);
        strip.add(eventDetails);
        createToggle.setStyleName("button secondary");
        strip.add(createToggle);
        add(strip);
        createCard = createCard();
        createCard.setVisible(false);
        add(createCard);
        createToggle.addClickHandler(event -> {
            createCard.setVisible(!createCard.isVisible());
            createToggle.getElement().setAttribute("aria-expanded", Boolean.toString(createCard.isVisible()));
            if (createCard.isVisible()) eventName.setFocus(true);
        });
        createToggle.getElement().setAttribute("aria-expanded", "false");
        createToggle.getElement().setAttribute("aria-controls", "create-night-card");
        removal.getElement().setAttribute("role", "region");
        removal.getElement().setAttribute("aria-label", "Confirm performance removal");
        removal.add(heading("Remove this performance?", 2));
        removal.add(removalText);
        FlowPanel removalActions = panel("removal-actions");
        keep.setStyleName("button secondary");
        Button confirm = new Button("Remove performance");
        confirm.setStyleName("button secondary navy-button");
        removalActions.add(keep); removalActions.add(confirm); removal.add(removalActions);
        keep.addClickHandler(event -> {
            pendingRemoval = null; removal.setVisible(false); setBusy(false); refresh.setFocus(true);
        });
        confirm.addClickHandler(event -> {
            PerformanceData performance = pendingRemoval;
            if (performance == null) return;
            pendingRemoval = null; removal.setVisible(false);
            executeChange(performance, "cancel", null);
        });
        removal.setVisible(false); add(removal);
        add(message);
        FlowPanel layout = panel("performance-workspace");
        layout.add(orderCard());
        FlowPanel sidebar = panel("performance-sidebar");
        sidebar.add(addCard());
        sidebar.add(first);
        layout.add(sidebar);
        add(layout);
        add(text("Every singer deserves a turn to shine.", "page-footer"));
        eventSelect.addChangeHandler(event -> {
            eventId = eventSelect.getSelectedValue();
            performances.clear();
            queueReady = false;
            message.setVisible(false);
            showEventDetails();
            render();
            loadQueue();
        });
        refresh.addClickHandler(event -> refreshAll());
        render();
    }

    void onShow() { if (!busy) refreshAll(); }

    private FlowPanel createCard() {
        FlowPanel card = panel("card create-night-card");
        card.getElement().setId("create-night-card");
        card.add(heading("Create night", 2));
        card.add(text("Give your next karaoke night a name.", "card-description"));
        FormPanel form = new FormPanel();
        form.getElement().setAttribute("novalidate", "novalidate");
        FlowPanel fields = panel("night-form");
        fields.add(field("Night name", eventName, "night-name", "Required · For example, Friday at Mill’s.", nameError));
        fields.add(field("Night date", eventDate, "night-date", "Required · The date of your event.", dateError));
        eventDate.getElement().setAttribute("type", "date");
        fields.add(field("Organiser’s name", organiser, "night-organiser", "Required · Who is hosting this night?", organiserError));
        for (TextBox input : List.of(eventName, organiser)) input.setMaxLength(200);
        for (TextBox input : List.of(eventName, eventDate, organiser)) input.getElement().setAttribute("aria-required", "true");
        create.setStyleName("button primary");
        create.getElement().setAttribute("type", "submit");
        fields.add(create);
        fields.add(createMessage);
        form.setWidget(fields);
        form.addSubmitHandler(event -> { event.cancel(); createNight(); });
        card.add(form);
        return card;
    }

    private FlowPanel orderCard() {
        FlowPanel card = panel("card order-card");
        FlowPanel top = panel("directory-top");
        FlowPanel title = panel("directory-titles");
        title.add(heading("Singing order", 2));
        title.add(totals);
        top.add(title);
        saved.getElement().setAttribute("role", "status");
        top.add(saved);
        card.add(top);
        refresh.setStyleName("button secondary refresh-order");
        card.add(refresh);
        rows.getElement().setAttribute("role", "list");
        rows.getElement().setAttribute("aria-label", "Waiting performances");
        rows.getElement().setAttribute("tabindex", "0");
        card.add(rows);
        card.add(text("Move performances up or down to set the order.", "order-note"));
        return card;
    }

    private FlowPanel addCard() {
        FlowPanel card = panel("card add-performance-card");
        card.add(heading("Add a performance", 2));
        card.add(text("Choose a singer and a saved song.", "card-description"));
        FlowPanel fields = panel("registration-form");
        fields.add(selectField("Singer", singerSelect, "performance-singer", singerError));
        songSearch.setStyleName("text-input");
        songSearch.getElement().setAttribute("type", "search");
        songSearch.getElement().setAttribute("aria-label", "Search your song library");
        songSearch.getElement().setAttribute("placeholder", "Search your song library…");
        songSearch.setMaxLength(200);
        songSearch.addBitlessDomHandler(event -> renderSongs(), InputEvent.TYPE);
        fields.add(songSearch);
        fields.add(selectField("Song", songSelect, "performance-song", songError));
        fields.add(choiceMessage);
        fields.add(text("New performances join the end of the order.", "field-hint"));
        add.setStyleName("button primary register-button");
        add.addClickHandler(event -> addPerformance());
        fields.add(add);
        card.add(fields);
        return card;
    }

    private FlowPanel selectField(String title, ListBox input, String id, Label error) {
        FlowPanel field = panel("field");
        LabelElement label = Document.get().createLabelElement();
        label.setHtmlFor(id);
        label.setInnerText(title);
        field.getElement().appendChild(label);
        input.setStyleName("text-input");
        input.getElement().setId(id);
        input.getElement().setAttribute("aria-required", "true");
        input.getElement().setAttribute("aria-describedby", id + "-error");
        field.add(input);
        error.setStyleName("field-error");
        error.getElement().setId(id + "-error");
        error.setVisible(false);
        field.add(error);
        return field;
    }

    private void refreshAll() {
        message.setVisible(false);
        loadChoices();
        loadEvents();
    }

    private void loadEvents() {
        int request = ++eventRequest;
        ++queueRequest;
        queueReady = false;
        refresh.setEnabled(false);
        eventSelect.setEnabled(false);
        saved.setText("Loading…");
        render();
        api.events(new PerformanceApi.Callback<List<EventData>>() {
            public void onSuccess(List<EventData> result) {
                if (request != eventRequest) return;
                events.clear(); events.addAll(result);
                eventSelect.clear();
                boolean exists = false;
                for (EventData event : events) {
                    eventSelect.addItem(event.name + " · " + event.date, event.id);
                    if (event.id.equals(eventId)) exists = true;
                }
                if (!exists) eventId = events.isEmpty() ? "" : events.get(0).id;
                for (int i = 0; i < eventSelect.getItemCount(); i++) {
                    if (eventSelect.getValue(i).equals(eventId)) eventSelect.setSelectedIndex(i);
                }
                if (events.isEmpty()) {
                    eventSelect.addItem("Create your first night", "");
                    performances.clear();
                    saved.setText("");
                    showMessage(message, "Create your first night to start building the singing order.", false);
                    refresh.setEnabled(true);
                }
                showEventDetails();
                render();
                loadQueue();
            }
            public void onFailure(String value, Map<String, String> fields) {
                if (request != eventRequest) return;
                showMessage(message, value, true);
                saved.setText("Not refreshed");
                refresh.setEnabled(true);
                render();
            }
        });
    }

    private void loadQueue() {
        int request = ++queueRequest;
        if (eventId.isEmpty()) return;
        String requestedEvent = eventId;
        queueReady = false;
        saved.setText("Loading…");
        refresh.setEnabled(false);
        render();
        api.queue(eventId, new PerformanceApi.Callback<PerformanceApi.QueueData>() {
            public void onSuccess(PerformanceApi.QueueData data) {
                if (request != queueRequest || !requestedEvent.equals(eventId)) return;
                refresh.setEnabled(true);
                accept(data);
            }
            public void onFailure(String value, Map<String, String> fields) {
                if (request != queueRequest || !requestedEvent.equals(eventId)) return;
                showMessage(message, value + " The displayed order may be out of date.", true);
                saved.setText("Not refreshed");
                refresh.setEnabled(true);
                render();
            }
        });
    }

    private void loadChoices() {
        int request = ++choiceRequest;
        singersReady = false; songsReady = false;
        showMessage(choiceMessage, "Loading singers and songs…", false);
        new SingerApi().list(new SingerApi.Callback<List<SingerData>>() {
            public void onSuccess(List<SingerData> result) {
                if (request != choiceRequest) return;
                String selected = singerSelect.getSelectedValue();
                singerSelect.clear(); singerSelect.addItem("Select a singer", "");
                for (SingerData singer : result) singerSelect.addItem(singer.name + (singer.nickname.isEmpty() ? "" : " · " + singer.nickname), singer.id);
                selectValue(singerSelect, selected);
                singersReady = true; choicesLoaded();
            }
            public void onFailure(String value, Map<String, String> fields) {
                if (request != choiceRequest) return;
                showMessage(choiceMessage, value, true); render();
            }
        });
        new SongApi().list(new SongApi.Callback<List<SongData>>() {
            public void onSuccess(List<SongData> result) {
                if (request != choiceRequest) return;
                songs.clear(); songs.addAll(result); songsReady = true;
                renderSongs(); choicesLoaded();
            }
            public void onFailure(String value, Map<String, String> fields) {
                if (request != choiceRequest) return;
                showMessage(choiceMessage, value, true); render();
            }
        });
    }

    private void choicesLoaded() {
        if (singersReady && songsReady) {
            choiceMessage.setVisible(false);
            if (singerSelect.getItemCount() <= 1 || songs.isEmpty()) {
                showMessage(choiceMessage, "Register a singer in Singers and save a song in Songs, then refresh this page.", false);
            }
        }
        render();
    }

    private void renderSongs() {
        String selected = songSelect.getSelectedValue();
        songSelect.clear(); songSelect.addItem("Select a song", "");
        String query = songSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        for (SongData song : songs) if (song.matches(query)) songSelect.addItem(song.title + " · " + song.artist, song.id);
        if (songSelect.getItemCount() == 1 && !query.isEmpty()) songSelect.setItemText(0, "No matching songs");
        selectValue(songSelect, selected);
    }

    private void selectValue(ListBox list, String value) {
        for (int i = 0; i < list.getItemCount(); i++) if (list.getValue(i).equals(value)) list.setSelectedIndex(i);
    }

    private void showEventDetails() {
        eventDetails.clear();
        for (EventData event : events) {
            if (!event.id.equals(eventId)) continue;
            HTML calendar = new HTML(SafeHtmlUtils.fromSafeConstant(
                    "<svg viewBox='0 0 24 24' width='22' height='22' aria-hidden='true' fill='none' stroke='currentColor' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'><rect x='3' y='5' width='18' height='16' rx='2'/><path d='M7 3v4M17 3v4M3 10h18'/></svg>"));
            HTML person = new HTML(SafeHtmlUtils.fromSafeConstant(
                    "<svg viewBox='0 0 24 24' width='22' height='22' aria-hidden='true' fill='none' stroke='currentColor' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='7' r='4'/><path d='M3 21v-2a9 9 0 0 1 18 0v2'/></svg>"));
            calendar.setStyleName("event-detail-icon");
            person.setStyleName("event-detail-icon");
            FlowPanel date = panel("event-detail");
            date.add(calendar);
            date.add(text(displayDate(event.date), "event-detail-text"));
            FlowPanel host = panel("event-detail");
            host.add(person);
            host.add(text("Organiser: " + event.organiser, "event-detail-text"));
            eventDetails.add(date);
            eventDetails.add(host);
        }
    }

    /** Format calendar components directly: no browser timezone conversion. */
    private String displayDate(String isoDate) {
        if (!isoDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return isoDate;
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        int month = Integer.parseInt(isoDate.substring(5, 7));
        if (month < 1 || month > 12) return isoDate;
        return Integer.parseInt(isoDate.substring(8, 10)) + " " + months[month - 1] + " " + isoDate.substring(0, 4);
    }

    private void createNight() {
        if (busy) return;
        applyError(eventName, nameError, null); applyError(eventDate, dateError, null); applyError(organiser, organiserError, null);
        showMessage(createMessage, "Creating night…", false);
        setBusy(true);
        api.create(eventName.getText(), eventDate.getText(), organiser.getText(), new PerformanceApi.Callback<EventData>() {
            public void onSuccess(EventData event) {
                eventId = event.id;
                performances.clear();
                eventName.setText(""); eventDate.setText(""); organiser.setText("");
                createMessage.setVisible(false); createCard.setVisible(false);
                createToggle.getElement().setAttribute("aria-expanded", "false");
                showMessage(message, "Night created. Choose a singer and song to add the first performance.", false);
                setBusy(false); loadEvents();
                if (isAttached()) createToggle.setFocus(true);
            }
            public void onFailure(String value, Map<String, String> fields) {
                setBusy(false);
                applyError(eventName, nameError, fields.get("eventName"));
                applyError(eventDate, dateError, fields.get("eventDate"));
                applyError(organiser, organiserError, fields.get("organiserName"));
                showMessage(createMessage, value, true);
                if (isAttached()) {
                    if (fields.containsKey("eventName")) eventName.setFocus(true);
                    else if (fields.containsKey("eventDate")) eventDate.setFocus(true);
                    else if (fields.containsKey("organiserName")) organiser.setFocus(true);
                }
            }
        });
    }

    private void addPerformance() {
        if (busy || !queueReady) return;
        selectError(singerSelect, singerError, null); selectError(songSelect, songError, null);
        if (singerSelect.getSelectedValue().isEmpty()) {
            selectError(singerSelect, singerError, "Select a singer."); singerSelect.setFocus(true); return;
        }
        if (songSelect.getSelectedValue().isEmpty()) {
            selectError(songSelect, songError, "Select a song."); songSelect.setFocus(true); return;
        }
        setBusy(true);
        api.add(eventId, singerSelect.getSelectedValue(), songSelect.getSelectedValue(), version, mutationCallback("Performance added."));
    }

    private void change(PerformanceData performance, String action, String direction) {
        if (busy || !queueReady) return;
        if ("cancel".equals(action)) {
            pendingRemoval = performance;
            removalText.setText("Remove " + performance.singerName + " singing “" + performance.songTitle + "” from this night’s order?");
            removal.setVisible(true); setBusy(true); keep.setFocus(true); return;
        }
        executeChange(performance, action, direction);
    }

    private void executeChange(PerformanceData performance, String action, String direction) {
        setBusy(true);
        api.change(eventId, performance.performanceId, action, direction, version,
                mutationCallback("cancel".equals(action) ? "Performance removed from the order." : "Singing order saved."));
    }

    private PerformanceApi.Callback<PerformanceApi.QueueData> mutationCallback(String success) {
        ++queueRequest;
        return new PerformanceApi.Callback<PerformanceApi.QueueData>() {
            public void onSuccess(PerformanceApi.QueueData data) {
                setBusy(false); accept(data);
                showMessage(message, success, false);
                if (isAttached()) refresh.setFocus(true);
            }
            public void onFailure(String value, Map<String, String> fields) {
                queueReady = false; setBusy(false);
                selectError(singerSelect, singerError, fields.get("singerId"));
                selectError(songSelect, songError, fields.get("songId"));
                showMessage(message, value, true);
                saved.setText("Refresh needed");
                if (isAttached()) refresh.setFocus(true);
            }
        };
    }

    private void accept(PerformanceApi.QueueData data) {
        if (!data.eventId.equals(eventId)) return;
        version = data.version;
        performances.clear(); performances.addAll(data.performances);
        queueReady = true; saved.setText("Saved"); render();
    }

    private void selectError(ListBox input, Label label, String value) {
        input.getElement().setAttribute("aria-invalid", value == null ? "false" : "true");
        label.setText(value == null ? "" : value); label.setVisible(value != null);
    }

    private void setBusy(boolean value) {
        busy = value;
        create.setEnabled(!value); createToggle.setEnabled(!value); refresh.setEnabled(!value);
        eventName.setEnabled(!value); eventDate.setEnabled(!value); organiser.setEnabled(!value);
        render();
    }

    private void render() {
        int scroll = rows.getElement().getScrollTop();
        rows.clear(); first.clear();
        Set<String> singers = new HashSet<>();
        for (PerformanceData row : performances) singers.add(row.singerId);
        totals.setText(performances.size() + (performances.size() == 1 ? " waiting performance · " : " waiting performances · ")
                + singers.size() + (singers.size() == 1 ? " singer" : " singers"));
        eventSelect.setEnabled(!busy && !events.isEmpty() && refresh.isEnabled());
        add.setEnabled(!busy && queueReady && singersReady && songsReady && singerSelect.getItemCount() > 1 && !songs.isEmpty());
        singerSelect.setEnabled(!busy && singersReady); songSelect.setEnabled(!busy && songsReady); songSearch.setEnabled(!busy && songsReady);
        rows.getElement().setAttribute("aria-busy", Boolean.toString(busy || (!eventId.isEmpty() && !queueReady)));
        for (int i = 0; i < performances.size(); i++) {
            PerformanceData performance = performances.get(i);
            FlowPanel row = panel("performance-row" + (i == 0 ? " first-row" : ""));
            row.getElement().setAttribute("role", "listitem");
            row.add(text((i < 9 ? "0" : "") + (i + 1), "performance-number"));
            row.add(text(performance.singerName.substring(0, 1).toUpperCase(java.util.Locale.ROOT), "avatar"));
            FlowPanel singer = panel("performance-singer");
            singer.add(text(performance.singerName, "singer-name"));
            singer.add(text(performance.nickName, "singer-nickname"));
            row.add(singer);
            FlowPanel song = panel("performance-song");
            song.add(text(performance.songTitle, "singer-name")); song.add(text(performance.artist, "song-artist"));
            row.add(song);
            FlowPanel actions = panel("performance-actions");
            Button up = action("↑", "Move " + performance.singerName + " up", performance, "move", "UP");
            Button down = action("↓", "Move " + performance.singerName + " down", performance, "move", "DOWN");
            up.setEnabled(!busy && queueReady && i > 0); down.setEnabled(!busy && queueReady && i + 1 < performances.size());
            actions.add(up); actions.add(down);
            Button remove = new Button(SafeHtmlUtils.fromSafeConstant("<svg viewBox='0 0 24 24' width='20' height='20' aria-hidden='true' fill='none' stroke='currentColor' stroke-width='1.7'><path d='M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7'/></svg>"));
            remove.setStyleName("icon-button bin-button");
            String label = "Remove " + performance.singerName + " singing " + performance.songTitle;
            remove.getElement().setAttribute("aria-label", label); remove.setTitle(label);
            remove.setEnabled(!busy && queueReady); remove.addClickHandler(event -> change(performance, "cancel", null));
            actions.add(remove); row.add(actions); rows.add(row);
        }
        if (performances.isEmpty()) {
            FlowPanel empty = panel("empty-state");
            empty.add(text("♪", "empty-icon"));
            empty.add(text(eventId.isEmpty() ? "Create your first night" : "Your stage is ready", "empty-title"));
            empty.add(text(eventId.isEmpty() ? "Give your night a name, date and organiser." : "Add a performance to give the first singer their turn.", "empty-description"));
            rows.add(empty);
        }
        first.add(text("FIRST IN LINE", "eyebrow"));
        if (performances.isEmpty()) first.add(text("A voice is waiting to shine.", "card-description"));
        else {
            PerformanceData next = performances.get(0);
            first.add(heading(next.singerName + (next.nickName.isEmpty() ? "" : " · " + next.nickName), 2));
            first.add(text(next.songTitle, "singer-name")); first.add(text(next.artist, "song-artist"));
        }
        rows.getElement().setScrollTop(scroll);
    }

    private Button action(String value, String label, PerformanceData performance, String action, String direction) {
        Button button = new Button(value);
        button.setStyleName("icon-button move-button");
        button.setTitle(label); button.getElement().setAttribute("aria-label", label);
        button.addClickHandler(event -> change(performance, action, direction));
        return button;
    }
}
