package com.millodysingqueue.client;

import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.shared.EventHandler;

/** Browser input events include typing, paste, and clearing a search field. */
final class InputEvent extends DomEvent<InputEvent.Handler> {
    interface Handler extends EventHandler {
        void onInput(InputEvent event);
    }

    static final Type<Handler> TYPE = new Type<>("input", new InputEvent());

    @Override
    public Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(Handler handler) {
        handler.onInput(this);
    }
}
