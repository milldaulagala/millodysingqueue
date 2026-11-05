--liquibase formatted sql

--changeset mill:1005-add-workspaces dbms:postgresql
CREATE TABLE app.workspaces (
    workspace_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspaces_name_not_blank CHECK (workspace_name ~ '[^[:space:]]')
);

INSERT INTO app.workspaces (workspace_name) VALUES ('MillodySingQueue');

ALTER TABLE app.singers ADD COLUMN workspace_id BIGINT;
ALTER TABLE app.songs ADD COLUMN workspace_id BIGINT;

UPDATE app.singers SET workspace_id = (SELECT workspace_id FROM app.workspaces);
UPDATE app.songs SET workspace_id = (SELECT workspace_id FROM app.workspaces);

ALTER TABLE app.singers
    ALTER COLUMN workspace_id SET NOT NULL,
    ADD CONSTRAINT singers_workspace_fk FOREIGN KEY (workspace_id)
        REFERENCES app.workspaces (workspace_id) ON DELETE RESTRICT;
ALTER TABLE app.songs
    ALTER COLUMN workspace_id SET NOT NULL,
    ADD CONSTRAINT songs_workspace_fk FOREIGN KEY (workspace_id)
        REFERENCES app.workspaces (workspace_id) ON DELETE RESTRICT;

DROP INDEX app.singers_email_case_insensitive_unique;
DROP INDEX app.singers_name_nickname_case_insensitive_unique;
ALTER TABLE app.songs DROP CONSTRAINT songs_youtube_video_id_unique;

CREATE UNIQUE INDEX singers_email_case_insensitive_unique
    ON app.singers (workspace_id, lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX singers_name_nickname_case_insensitive_unique
    ON app.singers (workspace_id, lower(btrim(singer_name)), lower(btrim(nick_name)))
    WHERE nick_name IS NOT NULL;
ALTER TABLE app.songs ADD CONSTRAINT songs_youtube_video_id_unique
    UNIQUE (workspace_id, youtube_video_id);

-- Partial unique singer indexes cannot cover all workspace lookups.
CREATE INDEX singers_workspace_idx ON app.singers (workspace_id);
-- The song unique constraint already indexes workspace_id as its leading column.

-- Runtime only needs to verify its configured workspace, not administer ownership.
REVOKE INSERT, UPDATE, DELETE ON app.workspaces FROM millody_app;
REVOKE ALL ON SEQUENCE app.workspaces_workspace_id_seq FROM millody_app;

-- Deliberately no automatic rollback: ownership and customer data must be preserved.
-- Correct applied changes with a new changeset.
