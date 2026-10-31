--liquibase formatted sql

--changeset mill:1002-create-singers dbms:postgresql
CREATE TABLE app.singers (
    singer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    singer_name TEXT NOT NULL,
    nick_name TEXT,
    email TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT singers_name_not_blank CHECK (singer_name ~ '[^[:space:]]'),
    CONSTRAINT singers_email_no_whitespace CHECK (
        email IS NULL OR (email <> '' AND email !~ '[[:space:]]')
    )
);

CREATE UNIQUE INDEX singers_email_case_insensitive_unique
    ON app.singers (lower(email))
    WHERE email IS NOT NULL;

-- Deliberately no automatic rollback: dropping this table would destroy history.
-- Correct applied changes with a new changeset; use tested backups for recovery.
