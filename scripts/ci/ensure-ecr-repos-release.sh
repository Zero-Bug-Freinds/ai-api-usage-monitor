#!/usr/bin/env bash
# Ensure ECR repositories exist for images this release job may push.
# Reads path-filter outputs and FORCE_ALL from the environment (see release.yml).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export AWS_REGION="${AWS_REGION:?}"

prefix="${ECR_REPOSITORY_PREFIX:?}"
is_true() { [[ "${1:-}" == "true" ]]; }

ensure_one() {
  "$ROOT/scripts/ci/ensure-ecr-repository.sh" "${prefix}/$1"
}

# Always return 0 so skipped ensures do not fail the script under set -e (last-command exit status).
ensure_if() {
  local flag="$1"
  local suffix="$2"
  if is_true "$flag"; then
    ensure_one "$suffix"
  fi
  return 0
}

ensure_web_if() {
  local service_flag="$1"
  local web_suffix="$2"
  if is_true "$service_flag" || is_true "${WEB_SHARED:-}"; then
    ensure_one "$web_suffix"
  fi
  return 0
}

if is_true "${FORCE_ALL:-false}"; then
  for s in \
    api-gateway-service \
    proxy-service \
    identity-service \
    identity-web \
    usage-service \
    usage-web \
    billing-service \
    billing-web \
    team-service \
    team-web \
    notification-service \
    notification-web \
    agent-service \
    agent-web \
    web-edge; do
    ensure_one "$s"
  done
  exit 0
fi

ensure_if "${API_GATEWAY:-}" api-gateway-service
ensure_if "${PROXY:-}" proxy-service
ensure_if "${IDENTITY:-}" identity-service
ensure_web_if "${IDENTITY:-}" identity-web

ensure_if "${USAGE:-}" usage-service
ensure_web_if "${USAGE:-}" usage-web

ensure_if "${BILLING:-}" billing-service
ensure_web_if "${BILLING:-}" billing-web

ensure_if "${TEAM:-}" team-service
ensure_web_if "${TEAM:-}" team-web

ensure_if "${NOTIFICATION:-}" notification-service
ensure_web_if "${NOTIFICATION:-}" notification-web

ensure_if "${AI_AGENT:-}" agent-service
ensure_web_if "${AI_AGENT:-}" agent-web

ensure_if "${WEB_EDGE:-}" web-edge

echo "ECR ensure step finished (no matching path filters is OK when this release pushes no images)."
exit 0
