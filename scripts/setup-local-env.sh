#!/bin/sh
set -eu

ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
if [ -e "$ROOT/.env" ]; then
    printf '%s\n' '.env already exists; leaving it unchanged.' >&2
    exit 1
fi

command -v openssl >/dev/null 2>&1 || {
    printf '%s\n' 'openssl is required to generate local passwords.' >&2
    exit 1
}

ADMIN_PASSWORD=$(openssl rand -hex 24)
MIGRATOR_PASSWORD=$(openssl rand -hex 24)
APP_PASSWORD=$(openssl rand -hex 24)

# Restrictive permissions and noclobber prevent replacing an existing secret file.
umask 077
(
    set -C
    cat > "$ROOT/.env" <<EOF
# Generated local credentials. Never commit this file.
POSTGRES_PORT=5432
POSTGRES_PASSWORD=$ADMIN_PASSWORD
MIGRATION_DB_PASSWORD=$MIGRATOR_PASSWORD
APPLICATION_DB_PASSWORD=$APP_PASSWORD
MIGRATION_DB_URL=jdbc:postgresql://127.0.0.1:\${POSTGRES_PORT}/millodysingqueue
MIGRATION_DB_USER=millody_migrator
APPLICATION_DB_URL=jdbc:postgresql://127.0.0.1:\${POSTGRES_PORT}/millodysingqueue
APPLICATION_DB_USER=millody_app
EOF
)
printf '%s\n' 'Created ignored .env with random passwords and owner-only permissions.'
