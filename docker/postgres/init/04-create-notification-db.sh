#!/bin/sh
# Runs only on first container init (empty data volume).
set -eu

NOTIFICATION_DB="${NOTIFICATION_POSTGRES_DB:-notification_db}"
NOTIFICATION_USER="${NOTIFICATION_POSTGRES_USER:-notification_app}"
NOTIFICATION_PASS="${NOTIFICATION_POSTGRES_PASSWORD:-notification_app}"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" \
  -c "CREATE USER \"${NOTIFICATION_USER}\" WITH PASSWORD '${NOTIFICATION_PASS}';"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" \
  -c "CREATE DATABASE \"${NOTIFICATION_DB}\" OWNER \"${NOTIFICATION_USER}\";"
