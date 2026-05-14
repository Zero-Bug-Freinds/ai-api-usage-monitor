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

is_true "${API_GATEWAY:-}" && ensure_one api-gateway-service
is_true "${PROXY:-}" && ensure_one proxy-service
is_true "${IDENTITY:-}" && ensure_one identity-service
if is_true "${IDENTITY:-}" || is_true "${WEB_SHARED:-}"; then
  ensure_one identity-web
fi

is_true "${USAGE:-}" && ensure_one usage-service
if is_true "${USAGE:-}" || is_true "${WEB_SHARED:-}"; then
  ensure_one usage-web
fi

is_true "${BILLING:-}" && ensure_one billing-service
if is_true "${BILLING:-}" || is_true "${WEB_SHARED:-}"; then
  ensure_one billing-web
fi

is_true "${TEAM:-}" && ensure_one team-service
if is_true "${TEAM:-}" || is_true "${WEB_SHARED:-}"; then
  ensure_one team-web
fi

is_true "${NOTIFICATION:-}" && ensure_one notification-service
if is_true "${NOTIFICATION:-}" || is_true "${WEB_SHARED:-}"; then
  ensure_one notification-web
fi

is_true "${AI_AGENT:-}" && ensure_one agent-service
if is_true "${AI_AGENT:-}" || is_true "${WEB_SHARED:-}"; then
  ensure_one agent-web
fi

is_true "${WEB_EDGE:-}" && ensure_one web-edge
