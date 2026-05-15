#!/usr/bin/env bash
# Wrapper for manual ops on EC2: always passes the merged deploy env file.
# Usage (from repo root on the host):
#   ./scripts/deploy/docker-compose-prod.sh logs -f --tail 50
#   ./scripts/deploy/docker-compose-prod.sh ps
#
# Requires a prior successful roll (creates /var/lib/.../compose.env and ${DEPLOY_ROOT}/.env).

set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/ai-api-usage-monitor}"
if [[ -z "${DEPLOY_STATE_DIR:-}" ]]; then
  if [[ "$(id -u)" -eq 0 ]]; then
    DEPLOY_STATE_DIR="/var/lib/ai-api-usage-monitor-deploy"
  else
    DEPLOY_STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/ai-api-usage-monitor-deploy"
  fi
fi
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-${DEPLOY_STATE_DIR}/compose.env}"

if [[ ! -f "$COMPOSE_ENV_FILE" && -f "${DEPLOY_ROOT}/.env" ]]; then
  COMPOSE_ENV_FILE="${DEPLOY_ROOT}/.env"
fi

if [[ ! -f "$COMPOSE_ENV_FILE" ]]; then
  echo "Missing compose env file: $COMPOSE_ENV_FILE" >&2
  echo "Run scripts/deploy/on-instance-compose-roll.sh first (or copy .env.deploy and roll)." >&2
  exit 1
fi

cd "$DEPLOY_ROOT"
exec docker compose -f "$COMPOSE_FILE" --env-file "$COMPOSE_ENV_FILE" "$@"
