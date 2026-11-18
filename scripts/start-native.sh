#!/usr/bin/env bash
# Run from a non-root account with Maven, Java 21, Node, and PostgreSQL 16 binaries.
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD"
state="${SEATLOCK_STATE:-$root/.local}"
mkdir -p "$state"
state="$(cd "$state" && pwd)"
pgbin="${PG_BIN:-$(pg_config --bindir 2>/dev/null || true)}"
if [[ -z "$pgbin" || ! -x "$pgbin/initdb" ]]; then
  printf 'Set PG_BIN to the PostgreSQL bin directory.\n' >&2
  exit 1
fi
export PORT="${PORT:-8081}"
export DB_URL="jdbc:postgresql://127.0.0.1:${PG_PORT:-55432}/postgres"
export DB_USER=seatlock
# Loopback-only services. A separate data directory is owned by this demo.
umask 077
random() { od -An -N32 -tx1 /dev/urandom | tr -d ' \n'; }
if [[ ! -f "$state/demo.env" ]]; then
  {
    printf 'DB_PASSWORD=%s\n' "$(random)"
    printf 'JWT_SECRET=%s\n' "$(random)"
    printf 'ADMIN_PASSWORD=%s\n' "$(random)"
  } > "$state/demo.env"
fi
set -a
source "$state/demo.env"
set +a
if [[ ! -f "$state/postgres/PG_VERSION" ]]; then
  printf '%s\n' "$DB_PASSWORD" > "$state/.pg-password"
  "$pgbin/initdb" -D "$state/postgres" -U "$DB_USER" -A scram-sha-256 \
    --pwfile="$state/.pg-password" --no-locale -E UTF8 > "$state/init.log"
  rm "$state/.pg-password"
fi
"$pgbin/pg_ctl" -D "$state/postgres" -o "-h 127.0.0.1 -p ${PG_PORT:-55432} -c unix_socket_directories=''" -l "$state/postgres.log" start
api_pid='' worker_pid='' web_pid=''
stop() {
  for pid in "$web_pid" "$worker_pid" "$api_pid"; do
    if [[ -n "$pid" ]]; then kill "$pid" 2>/dev/null || true; fi
  done
  wait 2>/dev/null || true
  "$pgbin/pg_ctl" -D "$state/postgres" -m fast stop >/dev/null || true
}
trap stop EXIT
trap 'exit 130' INT TERM
( cd backend && "${MAVEN_CMD:-mvn}" -B -DskipTests package ) > "$state/backend-build.log" 2>&1
( cd frontend && npm ci --no-audit --no-fund && npm run build ) > "$state/frontend-build.log" 2>&1
java -jar backend/target/seatlock-1.0.0.jar > "$state/api.log" 2>&1 &
api_pid=$!
for ((attempt=0;attempt<90;attempt++)); do
  if curl --noproxy "*" -fsS "http://127.0.0.1:$PORT/actuator/health/readiness" >/dev/null 2>&1; then break; fi
  kill -0 "$api_pid"; sleep 1
done
curl --noproxy "*" -fsS "http://127.0.0.1:$PORT/actuator/health/readiness" >/dev/null
WORKER_MODE=true SPRING_MAIN_WEB_APPLICATION_TYPE=none java -jar backend/target/seatlock-1.0.0.jar > "$state/worker.log" 2>&1 &
worker_pid=$!
API_TARGET="http://127.0.0.1:$PORT" node frontend/node_modules/vite/bin/vite.js preview frontend --config frontend/vite.config.js \
  --host 127.0.0.1 --port "${WEB_PORT:-8080}" --strictPort > "$state/frontend.log" 2>&1 &
web_pid=$!
for ((attempt=0;attempt<30;attempt++)); do
  if curl --noproxy "*" -fsS "http://127.0.0.1:${WEB_PORT:-8080}/api/v1/events" >/dev/null 2>&1; then break; fi
  kill -0 "$web_pid"; sleep 1
done
curl --noproxy "*" -fsS "http://127.0.0.1:${WEB_PORT:-8080}/api/v1/events" >/dev/null
printf 'SeatLock at http://127.0.0.1:%s\n' "${WEB_PORT:-8080}"
printf 'Admin email: admin@seatlock.local. Read ADMIN_PASSWORD from %s/demo.env\n' "$state"
while kill -0 "$api_pid" && kill -0 "$worker_pid" && kill -0 "$web_pid"; do sleep 2; done
printf 'A SeatLock service exited. Check %s.\n' "$state" >&2
exit 1
