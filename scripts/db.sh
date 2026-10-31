#!/bin/sh
set -eu

ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
if [ "$#" -ne 1 ]; then
    printf '%s\n' 'Usage: ./scripts/db.sh validate|update-sql|update|status' >&2
    exit 1
fi

case "$1" in
    validate) TASK=dbValidate ;;
    update-sql) TASK=dbUpdateSql ;;
    update) TASK=dbUpdate ;;
    status) TASK=dbStatus ;;
    *) printf '%s\n' 'Supported commands: validate, update-sql, update, status.' >&2; exit 1 ;;
esac

if [ ! -f "$ROOT/.env" ]; then
    printf '%s\n' 'Create .env with ./scripts/setup-local-env.sh first.' >&2
    exit 1
fi

# This is your trusted, local configuration file, not arbitrary downloaded input.
set -a
. "$ROOT/.env"
set +a

cd "$ROOT/backend"
exec ./gradlew --no-daemon "$TASK"
