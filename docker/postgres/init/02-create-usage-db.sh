#!/bin/sh
# Runs only on first container init (empty data volume).
# See docs/local-run-and-usage-verification.md for manual SQL when reusing postgres_data.
set -eu

USAGE_DB="${USAGE_POSTGRES_DB:-usage_db}"
USAGE_USER="${USAGE_POSTGRES_USER:-usage_app}"
USAGE_PASS="${USAGE_POSTGRES_PASSWORD:-usage_app}"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" \
  -c "CREATE USER \"${USAGE_USER}\" WITH PASSWORD '${USAGE_PASS}';"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" \
  -c "CREATE DATABASE \"${USAGE_DB}\" OWNER \"${USAGE_USER}\";"
