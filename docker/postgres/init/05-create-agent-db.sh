#!/bin/sh
# Runs only on first container init (empty data volume).
set -eu

AGENT_DB="${AGENT_POSTGRES_DB:-agent_db}"
AGENT_USER="${AGENT_POSTGRES_USER:-agent_app}"
AGENT_PASS="${AGENT_POSTGRES_PASSWORD:-agent_app}"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" \
  -c "CREATE USER \"${AGENT_USER}\" WITH PASSWORD '${AGENT_PASS}';"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "postgres" \
  -c "CREATE DATABASE \"${AGENT_DB}\" OWNER \"${AGENT_USER}\";"
