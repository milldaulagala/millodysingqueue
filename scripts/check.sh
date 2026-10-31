#!/bin/sh
set -eu

ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
command -v docker >/dev/null 2>&1 || {
    printf '%s\n' 'Docker CLI and a running container engine are required for database integration tests.' >&2
    exit 1
}

# Testcontainers does not automatically follow the Docker CLI's active context.
if [ -z "${DOCKER_HOST:-}" ]; then
    DOCKER_HOST=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
    export DOCKER_HOST
fi

case "$DOCKER_HOST" in
    unix://*/.colima/*/docker.sock)
        # Colima forwards published ports to localhost; Ryuk uses the VM's socket.
        export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}
        export TESTCONTAINERS_HOST_OVERRIDE=${TESTCONTAINERS_HOST_OVERRIDE:-127.0.0.1}
        ;;
esac

docker info >/dev/null 2>&1 || {
    printf '%s\n' 'Cannot reach Docker. Start Colima and check docker context show.' >&2
    exit 1
}

if [ "$#" -eq 0 ]; then
    set -- clean build
fi
exec "$ROOT/backend/gradlew" -p "$ROOT/backend" --no-daemon "$@"
