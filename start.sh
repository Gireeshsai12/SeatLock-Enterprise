#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f .env ]]; then
  umask 077
  random() { od -An -N32 -tx1 /dev/urandom | tr -d ' \n'; }
  {
    for name in POSTGRES_PASSWORD RABBITMQ_PASSWORD JWT_SECRET ADMIN_PASSWORD; do
      printf '%s=%s\n' "$name" "$(random)"
    done
  } > .env.tmp
  mv .env.tmp .env
fi
docker compose up --build -d --wait
web_port="$(sed -n 's/^WEB_PORT=//p' .env)"
printf 'SeatLock is available at http://localhost:%s\n' "${web_port:-8080}"
printf 'Admin email: admin@seatlock.local. Read ADMIN_PASSWORD from .env.\n'
