package com.millodysingqueue.client;

import static com.millodysingqueue.client.ScreenWidgets.panel;
import static com.millodysingqueue.client.ScreenWidgets.text;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;

public final class MillodySingQueueEntryPoint implements EntryPoint {
    private final SingerScreen singers = new SingerScreen();
    private final SongScreen songs = new SongScreen();
    private final SimplePanel screen = new SimplePanel();
    private final Anchor singersLink = new Anchor("Singers", "#singers");
    private final Anchor songsLink = new Anchor("Songs", "#songs");

    @Override
    public void onModuleLoad() {
        FlowPanel shell = panel("shell");
        FlowPanel header = panel("site-header");
        FlowPanel brand = panel("brand");
        brand.add(text("♪", "brand-mark"));
        FlowPanel copy = panel("brand-copy");
        copy.add(text("MillodySingQueue", "brand-name"));
        copy.add(text("Every singer. Every song. Your night.", "brand-tagline"));
        brand.add(copy);
        header.add(brand);
        FlowPanel navigation = panel("main-navigation");
        navigation.getElement().setAttribute("role", "navigation");
        navigation.getElement().setAttribute("aria-label", "Main navigation");
        navigation.add(singersLink);
        navigation.add(songsLink);
        header.add(navigation);
        shell.add(header);
        shell.add(screen);
        RootPanel.get("app").add(shell);
        History.addValueChangeHandler(event -> showScreen(event.getValue()));
        showScreen(History.getToken());
    }

    private void showScreen(String token) {
        boolean showSongs = "songs".equals(token);
        screen.setWidget(showSongs ? songs : singers);
        select(singersLink, !showSongs);
        select(songsLink, showSongs);
        if (showSongs) songs.onShow();
        else singers.onShow();
    }

    private void select(Anchor link, boolean selected) {
        link.setStyleName("navigation-link" + (selected ? " active" : ""));
        if (selected) link.getElement().setAttribute("aria-current", "page");
        else link.getElement().removeAttribute("aria-current");
    }
}
