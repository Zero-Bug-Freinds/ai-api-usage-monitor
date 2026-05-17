#!/usr/bin/env bash
# systemd oneshot: after reboot, restart the full stack when .env.deploy and a prior successful deploy exist.
# Does not replace SSM rolling deploy from GitHub Actions.

set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/ai-api-usage-monitor}"
ENV_DEPLOY="${DEPLOY_ROOT}/.env.deploy"
STATE_FILE="/var/lib/ai-api-usage-monitor-deploy/last-success-sha"
COMPOSE_ENV="/var/lib/ai-api-usage-monitor-deploy/compose.env"
RDS_WAIT_RETRIES="${RDS_WAIT_RETRIES:-60}"
RDS_WAIT_INTERVAL_SEC="${RDS_WAIT_INTERVAL_SEC:-10}"

wait_for_rds_from_env_deploy() {
  [[ -f "$ENV_DEPLOY" ]] || return 0
  local host=""
  host="$(grep -E '^IDENTITY_POSTGRES_HOST=' "$ENV_DEPLOY" | head -n 1 | cut -d= -f2- | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  [[ -n "${host//[[:space:]]/}" ]] || return 0
  for ((i = 1; i <= RDS_WAIT_RETRIES; i++)); do
    if getent hosts "$host" >/dev/null 2>&1 && command -v nc >/dev/null 2>&1 && nc -z -w 3 "$host" 5432 2>/dev/null; then
      echo "RDS reachable ($host:5432) before compose up"
      return 0
    fi
    echo "Waiting for RDS $host:5432 ($i/${RDS_WAIT_RETRIES})..."
    sleep "$RDS_WAIT_INTERVAL_SEC"
  done
  echo "WARN: RDS not reachable yet; compose up may fail until RDS is available (alpha: run alpha-stack-start.sh first)." >&2
}

if [[ ! -f "$ENV_DEPLOY" ]] || [[ ! -f "$STATE_FILE" ]]; then
  exit 0
fi

wait_for_rds_from_env_deploy

if [[ ! -x "${DEPLOY_ROOT}/scripts/deploy/on-instance-compose-roll.sh" ]]; then
  exit 0
fi

export IMAGE_TAG="$(cat "$STATE_FILE")"
export DEPLOY_ROOT
export AWS_REGION="${AWS_REGION:-$(curl -fsS --max-time 2 http://169.254.169.254/latest/meta-data/placement/region)}"

# Fast path: reuse merged compose.env from the last roll when present.
if [[ -f "$COMPOSE_ENV" ]]; then
  cd "$DEPLOY_ROOT"
  if docker compose -p aio-bootstrap -f docker-compose-prod.bootstrap.yml ps -q 2>/dev/null | grep -q .; then
    docker compose -p aio-bootstrap -f docker-compose-prod.bootstrap.yml --env-file /var/lib/ai-api-usage-monitor-deploy/bootstrap.env down 2>/dev/null || true
  fi
  docker compose -f docker-compose-prod.yml --env-file "$COMPOSE_ENV" up -d --remove-orphans
  exit 0
fi

exec bash "${DEPLOY_ROOT}/scripts/deploy/on-instance-compose-roll.sh"
