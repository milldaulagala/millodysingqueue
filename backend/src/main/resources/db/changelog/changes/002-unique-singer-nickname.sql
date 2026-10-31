--liquibase formatted sql

--changeset mill:1003-unique-singer-nickname dbms:postgresql
-- Resolve any existing duplicate named pairs before applying this migration.
-- Nicknames remain optional; NULL nicknames do not participate in uniqueness.
CREATE UNIQUE INDEX singers_name_nickname_case_insensitive_unique
    ON app.singers (lower(btrim(singer_name)), lower(btrim(nick_name)))
    WHERE nick_name IS NOT NULL;

--rollback DROP INDEX app.singers_name_nickname_case_insensitive_unique;
