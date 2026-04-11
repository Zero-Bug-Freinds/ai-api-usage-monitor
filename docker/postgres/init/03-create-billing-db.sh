#!/bin/sh
# Runs only on first container init (empty data volume).
set -eu

BILLING_DB="${BILLING_POSTGRES_DB:-billing_db}"
BILLING_USER="${BILLING_POSTGRES_USER:-billing_app}"
BILLING_PASS="${BILLING_POSTGRES_PASSWORD:-billing_app}"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" \
  -c "CREATE USER \"${BILLING_USER}\" WITH PASSWORD '${BILLING_PASS}';"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" \
  -c "CREATE DATABASE \"${BILLING_DB}\" OWNER \"${BILLING_USER}\";"
