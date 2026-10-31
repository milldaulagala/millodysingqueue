-- Bootstrap only: the official PostgreSQL image runs this on an empty volume.
-- psql quotes values as SQL literals; passwords never appear in command arguments.
\set ON_ERROR_STOP on
\getenv migration_password MIGRATION_DB_PASSWORD
\getenv application_password APPLICATION_DB_PASSWORD

BEGIN;

CREATE ROLE millody_migrator LOGIN PASSWORD :'migration_password';
CREATE ROLE millody_app LOGIN PASSWORD :'application_password';

SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO millody_migrator, millody_app', current_database()) \gexec
REVOKE ALL ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA app AUTHORIZATION millody_migrator;
CREATE SCHEMA liquibase AUTHORIZATION millody_migrator;
REVOKE ALL ON SCHEMA app, liquibase FROM PUBLIC;
GRANT USAGE ON SCHEMA app TO millody_app;

-- Objects created by the migration role get application DML permissions.
-- The application cannot create tables or read Liquibase's tracking schema.
ALTER DEFAULT PRIVILEGES FOR ROLE millody_migrator IN SCHEMA app
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO millody_app;
ALTER DEFAULT PRIVILEGES FOR ROLE millody_migrator IN SCHEMA app
    GRANT USAGE, SELECT ON SEQUENCES TO millody_app;

COMMIT;
