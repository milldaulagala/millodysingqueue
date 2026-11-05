package com.millodysingqueue.client;

import static com.millodysingqueue.client.ScreenWidgets.applyError;
import static com.millodysingqueue.client.ScreenWidgets.field;
import static com.millodysingqueue.client.ScreenWidgets.heading;
import static com.millodysingqueue.client.ScreenWidgets.liveMessage;
import static com.millodysingqueue.client.ScreenWidgets.panel;
import static com.millodysingqueue.client.ScreenWidgets.showMessage;
import static com.millodysingqueue.client.ScreenWidgets.text;

import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SongScreen extends FlowPanel {
    private final SongApi api = new SongApi();
    private final TextBox title = new TextBox();
    private final TextBox artist = new TextBox();
    private final TextBox video = new TextBox();
    private final TextBox search = new TextBox();
    private final Label titleError = new Label();
    private final Label artistError = new Label();
    private final Label videoError = new Label();
    private final Label formMessage = liveMessage("song-form-message");
    private final Label libraryMessage = liveMessage("library-message");
    private final Label count = new Label("—");
    private final Label results = new Label();
    private final FlowPanel rows = panel("song-rows");
    private final Button save = new Button("Save song");
    private final Button refresh = new Button("Refresh library");
    private final List<SongData> songs = new ArrayList<>();
    private boolean loaded;
    private boolean saving;
    private int libraryRequest;

    SongScreen() {
        setStyleName("content");
        FlowPanel intro = panel("introduction");
        FlowPanel copy = panel("intro-copy");
        copy.add(text("THE STAGE IS YOURS", "eyebrow"));
        copy.add(heading("A song for every voice.", 1));
        copy.add(text("Build your karaoke collection, one favourite at a time.", "intro-description"));
        intro.add(copy);
        FlowPanel summary = panel("summary");
        count.setStyleName("summary-count");
        summary.add(count);
        summary.add(text("SONGS IN YOUR LIBRARY", "summary-label"));
        intro.add(summary);
        add(intro);
        FlowPanel layout = panel("workspace");
        layout.add(saveCard());
        layout.add(libraryCard());
        add(layout);
        add(text("Your favourites, ready for the next karaoke night.", "page-footer"));
    }

    void onShow() {
        if (!loaded && refresh.isEnabled()) loadSongs();
    }

    private FlowPanel saveCard() {
        FlowPanel card = panel("card registration-card");
        card.add(text("MAKE ROOM FOR A FAVOURITE", "eyebrow"));
        card.add(heading("Save a song", 2));
        card.add(text("Save a karaoke video for another great night.", "card-description"));
        FormPanel form = new FormPanel();
        form.getElement().setAttribute("novalidate", "novalidate");
        FlowPanel fields = panel("registration-form");
        fields.add(field("Song title", title, "song-title", "Required · The name of the song.", titleError));
        fields.add(field("Artist", artist, "song-artist", "Required · The original artist or band.", artistError));
        fields.add(field("YouTube link or video ID", video, "song-video", "Paste a YouTube link or an 11-character video ID.", videoError));
        for (TextBox input : List.of(title, artist, video)) input.getElement().setAttribute("aria-required", "true");
        title.getElement().setAttribute("maxlength", "200");
        artist.getElement().setAttribute("maxlength", "200");
        video.getElement().setAttribute("maxlength", "2048");
        video.getElement().setAttribute("autocapitalize", "none");
        video.getElement().setAttribute("spellcheck", "false");
        fields.add(formMessage);
        save.setStyleName("button primary register-button");
        save.getElement().setAttribute("type", "submit");
        fields.add(save);
        form.setWidget(fields);
        form.addSubmitHandler(event -> {
            event.cancel();
            saveSong();
        });
        card.add(form);
        card.add(text("One video, one library entry. Different versions are welcome.", "registration-note"));
        return card;
    }

    private FlowPanel libraryCard() {
        FlowPanel card = panel("card directory-card");
        FlowPanel top = panel("directory-top");
        FlowPanel copy = panel("directory-titles");
        copy.add(text("YOUR KARAOKE COLLECTION", "eyebrow"));
        copy.add(heading("Song library", 2));
        top.add(copy);
        refresh.setStyleName("button secondary");
        refresh.addClickHandler(event -> loadSongs());
        top.add(refresh);
        card.add(top);
        search.setStyleName("search-input");
        search.getElement().setAttribute("type", "search");
        search.getElement().setAttribute("placeholder", "Search by title or artist…");
        search.getElement().setAttribute("aria-label", "Search songs by title or artist");
        search.addBitlessDomHandler(event -> renderLibrary(), InputEvent.TYPE);
        card.add(search);
        results.setStyleName("result-count");
        results.getElement().setAttribute("role", "status");
        card.add(results);
        card.add(libraryMessage);
        rows.getElement().setAttribute("role", "list");
        rows.getElement().setAttribute("aria-label", "Saved songs");
        card.add(rows);
        return card;
    }

    private void saveSong() {
        if (saving) return;
        applyError(title, titleError, null);
        applyError(artist, artistError, null);
        applyError(video, videoError, null);
        showMessage(formMessage, "Saving song…", false);
        setSaving(true);
        api.save(title.getText(), artist.getText(), video.getText(), new SongApi.Callback<SongData>() {
            public void onSuccess(SongData song) {
                setSaving(false);
                title.setText("");
                artist.setText("");
                video.setText("");
                search.setText("");
                showMessage(formMessage, "Song saved successfully.", false);
                if (isAttached()) title.setFocus(true);
                loadSongs();
            }
            public void onFailure(String message, Map<String, String> errors) {
                setSaving(false);
                applyError(title, titleError, errors.get("title"));
                applyError(artist, artistError, errors.get("artist"));
                applyError(video, videoError, errors.get("youtubeVideo"));
                showMessage(formMessage, message, true);
                if (!isAttached()) return;
                if (errors.containsKey("title")) title.setFocus(true);
                else if (errors.containsKey("artist")) artist.setFocus(true);
                else if (errors.containsKey("youtubeVideo")) video.setFocus(true);
            }
        });
    }

    private void loadSongs() {
        int requestId = ++libraryRequest;
        refresh.setEnabled(false);
        rows.getElement().setAttribute("aria-busy", "true");
        showMessage(libraryMessage, "Loading your songs…", false);
        api.list(new SongApi.Callback<List<SongData>>() {
            public void onSuccess(List<SongData> loaded) {
                if (requestId != libraryRequest) return;
                songs.clear();
                songs.addAll(loaded);
                SongScreen.this.loaded = true;
                count.setText(Integer.toString(songs.size()));
                libraryMessage.setVisible(false);
                finish();
                renderLibrary();
            }
            public void onFailure(String message, Map<String, String> errors) {
                if (requestId != libraryRequest) return;
                showMessage(libraryMessage, message + (loaded ? " Showing the last loaded library." : ""), true);
                finish();
            }
            private void finish() {
                refresh.setEnabled(true);
                rows.getElement().setAttribute("aria-busy", "false");
            }
        });
    }

    private void renderLibrary() {
        rows.clear();
        if (!loaded) return;
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        int matched = 0;
        for (SongData song : songs) {
            if (!song.matches(query)) continue;
            matched++;
            FlowPanel row = panel("singer-row song-row");
            row.getElement().setAttribute("role", "listitem");
            row.add(text("♪", "song-icon"));
            FlowPanel details = panel("singer-details");
            details.add(text(song.title, "singer-name"));
            details.add(text(song.artist, "song-artist"));
            Anchor youtube = new Anchor("View on YouTube ↗", "https://www.youtube.com/watch?v=" + song.videoId, "_blank");
            youtube.setStyleName("video-link");
            youtube.getElement().setAttribute("rel", "noopener noreferrer");
            youtube.getElement().setAttribute("aria-label", "View " + song.title + " on YouTube (opens a new tab)");
            details.add(youtube);
            row.add(details);
            rows.add(row);
        }
        results.setText(query.isEmpty() ? matched + (matched == 1 ? " song saved" : " songs saved")
                : "Showing " + matched + " of " + songs.size() + " songs");
        if (matched == 0) {
            FlowPanel empty = panel("empty-state");
            empty.add(text(query.isEmpty() ? "♪" : "⌕", "empty-icon"));
            empty.add(text(query.isEmpty() ? "Your next favourite starts here" : "No matching songs", "empty-title"));
            empty.add(text(query.isEmpty() ? "Save your first karaoke video and start your collection."
                    : "Try another song title or artist.", "empty-description"));
            rows.add(empty);
        }
    }

    private void setSaving(boolean value) {
        saving = value;
        save.setEnabled(!value);
        save.setText(value ? "Saving…" : "Save song");
        title.setEnabled(!value);
        artist.setEnabled(!value);
        video.setEnabled(!value);
    }
}
