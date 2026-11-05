--liquibase formatted sql

--changeset mill:1004-create-songs dbms:postgresql
CREATE TABLE app.songs (
    song_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    artist VARCHAR(200) NOT NULL,
    youtube_video_id VARCHAR(11) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT songs_title_not_blank CHECK (title ~ '[^[:space:]]'),
    CONSTRAINT songs_artist_not_blank CHECK (artist ~ '[^[:space:]]'),
    CONSTRAINT songs_video_id_format CHECK (youtube_video_id ~ '^[A-Za-z0-9_-]{11}$'),
    CONSTRAINT songs_youtube_video_id_unique UNIQUE (youtube_video_id)
);

-- Deliberately no automatic rollback: dropping the table would destroy the library.
-- Correct applied changes with a new changeset; use tested backups for recovery.
