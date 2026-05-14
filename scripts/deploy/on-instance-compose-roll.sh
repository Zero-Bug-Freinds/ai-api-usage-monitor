#!/usr/bin/env bash
# Run on the EC2 host (via SSM). Pulls ECR images and restarts `docker-compose.prod.yml`.
# Prerequisites: Docker, compose plugin, AWS CLI; repo at DEPLOY_ROOT; `.env.deploy` with secrets (not in git).
#
# Env (required unless noted):
#   IMAGE_TAG              — immutable tag (git sha) to deploy
# Optional:
#   DEPLOY_ROOT            — default /opt/ai-api-usage-monitor
#   DEPLOY_STATE_DIR       — default /var/lib/ai-api-usage-monitor-deploy
#   COMPOSE_FILE           — default docker-compose.prod.yml
#   HEALTH_URL             — default http://127.0.0.1:8080/healthz (web-edge :8080 listener)
#   HEALTH_RETRIES         — default 30
#   HEALTH_INTERVAL_SEC    — default 2
#   AWS_REGION             — for `aws ecr get-login-password` (optional if already logged in to ECR)

set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/ai-api-usage-monitor}"
DEPLOY_STATE_DIR="${DEPLOY_STATE_DIR:-/var/lib/ai-api-usage-monitor-deploy}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/healthz}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SEC="${HEALTH_INTERVAL_SEC:-2}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}"

mkdir -p "$DEPLOY_STATE_DIR"
STATE_FILE="$DEPLOY_STATE_DIR/last-success-sha"
PREVIOUS_SHA=""
if [[ -f "$STATE_FILE" ]]; then
  PREVIOUS_SHA="$(cat "$STATE_FILE")"
fi

rollback() {
  if [[ -z "$PREVIOUS_SHA" ]]; then
    echo "Rollback skipped: no previous successful sha in $STATE_FILE" >&2
    return 1
  fi
  echo "Rolling back to IMAGE_TAG=$PREVIOUS_SHA"
  export IMAGE_TAG="$PREVIOUS_SHA"
  (cd "$DEPLOY_ROOT" && docker compose -f "$COMPOSE_FILE" --env-file .env.deploy pull && docker compose -f "$COMPOSE_FILE" --env-file .env.deploy up -d)
}

cd "$DEPLOY_ROOT"

if [[ -n "${AWS_REGION:-}" ]]; then
  ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
  REGISTRY="${ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"
fi

export IMAGE_TAG
if ! docker compose -f "$COMPOSE_FILE" --env-file .env.deploy pull; then
  echo "docker compose pull failed" >&2
  rollback || true
  exit 1
fi
if ! docker compose -f "$COMPOSE_FILE" --env-file .env.deploy up -d; then
  echo "docker compose up failed" >&2
  rollback || true
  exit 1
fi

ok=false
for ((i = 1; i <= HEALTH_RETRIES; i++)); do
  if curl -fsS "$HEALTH_URL" >/dev/null; then
    ok=true
    break
  fi
  sleep "$HEALTH_INTERVAL_SEC"
done

if [[ "$ok" != true ]]; then
  echo "Health check failed after $HEALTH_RETRIES attempts ($HEALTH_URL)" >&2
  rollback || true
  exit 1
fi

printf '%s' "$IMAGE_TAG" > "$STATE_FILE"
echo "Deploy OK; recorded last-success-sha=$IMAGE_TAG"
