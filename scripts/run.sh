#!/bin/sh
set -eu

ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
if [ ! -f "$ROOT/.env" ]; then
    printf '%s\n' 'Create .env with ./scripts/setup-local-env.sh and follow docs/local-database.md first.' >&2
    exit 1
fi
set -a
. "$ROOT/.env"
# Defaults also support .env files created before task 1003.
APPLICATION_DB_URL=${APPLICATION_DB_URL:-jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5432}/millodysingqueue}
APPLICATION_DB_USER=${APPLICATION_DB_USER:-millody_app}
set +a
exec "$ROOT/backend/gradlew" -p "$ROOT/backend" --no-daemon run
