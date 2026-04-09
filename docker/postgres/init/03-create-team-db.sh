#!/bin/sh
set -eu

TEAM_DB="${TEAM_POSTGRES_DB:-team_db}"
TEAM_USER="${TEAM_POSTGRES_USER:-team_app}"
TEAM_PASSWORD="${TEAM_POSTGRES_PASSWORD:-team_app}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
DO
\$do\$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${TEAM_USER}') THEN
      EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${TEAM_USER}', '${TEAM_PASSWORD}');
   END IF;
END
\$do\$;

SELECT format('CREATE DATABASE %I OWNER %I', '${TEAM_DB}', '${TEAM_USER}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${TEAM_DB}')\gexec
SQL
