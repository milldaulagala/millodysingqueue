--liquibase formatted sql

--changeset mill:1006-create-events-and-performances dbms:postgresql
ALTER TABLE app.singers ADD CONSTRAINT singers_workspace_identity_unique UNIQUE (workspace_id, singer_id);
ALTER TABLE app.songs ADD CONSTRAINT songs_workspace_identity_unique UNIQUE (workspace_id, song_id);

CREATE TABLE app.events (
    event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES app.workspaces (workspace_id) ON DELETE RESTRICT,
    event_name VARCHAR(200) NOT NULL CHECK (event_name ~ '[^[:space:]]'),
    event_date DATE NOT NULL,
    organiser_name VARCHAR(200) NOT NULL CHECK (organiser_name ~ '[^[:space:]]'),
    queue_version BIGINT NOT NULL DEFAULT 0 CHECK (queue_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT events_workspace_identity_unique UNIQUE (workspace_id, event_id)
);
CREATE INDEX events_workspace_date_idx ON app.events (workspace_id, event_date DESC, event_id DESC);

CREATE TABLE app.performances (
    performance_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    singer_id BIGINT NOT NULL,
    song_id BIGINT NOT NULL,
    queue_position BIGINT NOT NULL CHECK (queue_position > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT performances_event_fk FOREIGN KEY (workspace_id, event_id)
        REFERENCES app.events (workspace_id, event_id) ON DELETE RESTRICT,
    CONSTRAINT performances_singer_fk FOREIGN KEY (workspace_id, singer_id)
        REFERENCES app.singers (workspace_id, singer_id) ON DELETE RESTRICT,
    CONSTRAINT performances_song_fk FOREIGN KEY (workspace_id, song_id)
        REFERENCES app.songs (workspace_id, song_id) ON DELETE RESTRICT,
    CONSTRAINT performances_position_unique UNIQUE (workspace_id, event_id, queue_position)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX performances_waiting_idx ON app.performances (workspace_id, event_id, status, queue_position);
CREATE INDEX performances_singer_idx ON app.performances (workspace_id, singer_id);
CREATE INDEX performances_song_idx ON app.performances (workspace_id, song_id);

-- No destructive automatic rollback. Preserve event records and fix forward.
