#!/usr/bin/env bash
# Create logical PostgreSQL databases on the staging RDS (one host, multiple DB names).
# Run from EC2 in the same VPC as RDS (SSM session). Install client: sudo dnf install -y postgresql15
#
#   export PGHOST="<terraform output -raw staging_rds_address>"
#   export PGUSER="appadmin"
#   export PGPASSWORD="<terraform output -raw staging_rds_master_password>"
#   ./scripts/deploy/rds-staging-create-logical-dbs.sh

set -euo pipefail

PGHOST="${PGHOST:?}"
PGUSER="${PGUSER:?}"
PGPASSWORD="${PGPASSWORD:?}"

for db in identity usage billing team notification agent; do
  exists="$(psql -h "$PGHOST" -U "$PGUSER" -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$db'" || true)"
  if [[ "$exists" == "1" ]]; then
    echo "Database $db already exists, skip."
  else
    echo "Creating database $db..."
    psql -h "$PGHOST" -U "$PGUSER" -d postgres -c "CREATE DATABASE $db;"
  fi
done

echo "Done. For quick staging .env.deploy: set all *_POSTGRES_USER to $PGUSER, *_POSTGRES_PASSWORD to the same PGPASSWORD, hosts to PGHOST; NOTIFICATION_DATABASE_URL user/password must match."
