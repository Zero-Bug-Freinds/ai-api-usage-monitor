#!/bin/sh
set -eu

cd /repo/services/notification-service

echo "[notification-service] waiting for database..."

attempt=1
max_attempts="${PRISMA_MIGRATE_MAX_ATTEMPTS:-30}"
sleep_seconds="${PRISMA_MIGRATE_SLEEP_SECONDS:-2}"

while true; do
  if ./node_modules/.bin/prisma migrate deploy; then
    echo "[notification-service] prisma migrate deploy done"
    break
  fi

  if [ "$attempt" -ge "$max_attempts" ]; then
    echo "[notification-service] prisma migrate deploy failed after ${attempt} attempts" >&2
    exit 1
  fi

  echo "[notification-service] migrate failed (attempt ${attempt}/${max_attempts}), retrying in ${sleep_seconds}s..." >&2
  attempt=$((attempt + 1))
  sleep "$sleep_seconds"
done

exec node dist/main.js

