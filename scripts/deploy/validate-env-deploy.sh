#!/usr/bin/env bash
# Validate .env.deploy shape before deploy (quotes, host:port in *_POSTGRES_HOST, required keys).
# Usage:
#   ./scripts/deploy/validate-env-deploy.sh
#   ./scripts/deploy/validate-env-deploy.sh /opt/ai-api-usage-monitor/.env.deploy

set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/ai-api-usage-monitor}"
ENV_FILE="${1:-${DEPLOY_ROOT}/.env.deploy}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing: $ENV_FILE" >&2
  exit 1
fi

sanitize_env_value() {
  local v="$1"
  v="$(printf '%s' "$v" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  if [[ "$v" =~ ^\"(.*)\"$ ]]; then v="${BASH_REMATCH[1]}"; fi
  if [[ "$v" =~ ^\'(.*)\'$ ]]; then v="${BASH_REMATCH[1]}"; fi
  printf '%s' "$v"
}

errors=0
warns=0

check_no_quotes_in_raw() {
  local line="$1"
  if [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*=\" ]] || [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*=\' ]]; then
    echo "ERROR: quoted value not allowed in .env.deploy (use unquoted KEY=value): $line" >&2
    errors=$((errors + 1))
  fi
}

hosts=()
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  line="$(printf '%s' "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  [[ -z "$line" ]] && continue
  check_no_quotes_in_raw "$line"
  [[ "$line" != *=* ]] && continue
  key="${line%%=*}"
  val="${line#*=}"
  if [[ "$key" =~ _POSTGRES_HOST$ ]]; then
    val_raw="$(sanitize_env_value "$val")"
    if [[ "$val_raw" == *:* ]]; then
      echo "ERROR: ${key} must be hostname only (no :5432). Use ${key%_HOST}_PORT=5432. Got: ${val_raw}" >&2
      errors=$((errors + 1))
    fi
    if [[ "$val_raw" =~ [[:space:]] ]]; then
      echo "ERROR: ${key} contains whitespace: '${val_raw}'" >&2
      errors=$((errors + 1))
    fi
    hosts+=("$val_raw")
  fi
done < <(sed 's/\r$//' "$ENV_FILE")

for req in IDENTITY_POSTGRES_HOST USAGE_POSTGRES_HOST IMAGE_TAG; do
  if ! grep -q "^${req}=" "$ENV_FILE"; then
    echo "WARN: missing ${req}" >&2
    warns=$((warns + 1))
  fi
done

rabbit_host="$(env_file_value RABBITMQ_HOST)"
if [[ -z "${rabbit_host//[[:space:]]/}" ]]; then
  echo "WARN: RABBITMQ_HOST is empty — proxy-service and other publishers may default to compose hostname rabbitmq" >&2
  warns=$((warns + 1))
elif [[ "${rabbit_host,,}" == "rabbitmq" ]]; then
  echo "WARN: RABBITMQ_HOST=rabbitmq is for local Compose only; EC2 deploy should use host.docker.internal" >&2
  warns=$((warns + 1))
else
  echo "OK: RABBITMQ_HOST=${rabbit_host}"
fi

notif_rabbit_url="$(env_file_value NOTIFICATION_RABBITMQ_URL)"
if [[ -z "${notif_rabbit_url//[[:space:]]/}" ]]; then
  echo "WARN: NOTIFICATION_RABBITMQ_URL empty — notification-service uses composed amqp:// from RABBITMQ_* in compose" >&2
  warns=$((warns + 1))
elif [[ "${notif_rabbit_url,,}" == *@rabbitmq:* ]] || [[ "${notif_rabbit_url,,}" == *//rabbitmq:* ]]; then
  echo "WARN: NOTIFICATION_RABBITMQ_URL uses hostname rabbitmq — EC2 should use host.docker.internal" >&2
  warns=$((warns + 1))
else
  echo "OK: NOTIFICATION_RABBITMQ_URL is set (notification Nest RABBITMQ_URL)"
fi

if [[ ${#hosts[@]} -gt 1 ]]; then
  first="${hosts[0]}"
  for h in "${hosts[@]}"; do
    if [[ "$h" != "$first" ]]; then
      echo "WARN: staging single-RDS expects identical *_POSTGRES_HOST; saw '${first}' and '${h}'" >&2
      warns=$((warns + 1))
    fi
  done
fi

if [[ ${#hosts[@]} -gt 0 ]]; then
  echo "OK: postgres host(s) look like hostname-only (example endpoint shape): ${hosts[0]}"
fi

env_file_value() {
  local key="$1"
  local line val
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  [[ -z "$line" ]] && return 0
  val="${line#*=}"
  sanitize_env_value "$val"
}

gateway_dev_mode_enabled() {
  local raw
  raw="$(env_file_value GATEWAY_DEV_MODE)"
  raw="${raw,,}"
  [[ "$raw" == "true" || "$raw" == "1" ]]
}

# Gateway env: WARN only — do not block on-instance-compose-roll (existing EC2 stacks may omit until next gateway recreate).
gw_shared="$(env_file_value GATEWAY_SHARED_SECRET)"
if [[ -z "${gw_shared//[[:space:]]/}" ]]; then
  echo "WARN: GATEWAY_SHARED_SECRET is empty — api-gateway GatewayStartupValidation may fail" >&2
  warns=$((warns + 1))
fi

gw_jwt="$(env_file_value GATEWAY_JWT_SECRET)"
jwt_secret="$(env_file_value JWT_SECRET)"
if [[ -z "${gw_jwt//[[:space:]]/}" ]]; then
  echo "WARN: GATEWAY_JWT_SECRET is empty — gateway JWT verification unavailable when dev-mode=false" >&2
  warns=$((warns + 1))
fi
if [[ -z "${jwt_secret//[[:space:]]/}" ]]; then
  echo "WARN: JWT_SECRET is empty — identity JWT signing unavailable" >&2
  warns=$((warns + 1))
fi
if [[ -n "${gw_jwt//[[:space:]]/}" && -n "${jwt_secret//[[:space:]]/}" && "$gw_jwt" != "$jwt_secret" ]]; then
  echo "WARN: GATEWAY_JWT_SECRET and JWT_SECRET differ — login JWT may fail at gateway (401)" >&2
  warns=$((warns + 1))
fi

if ! gateway_dev_mode_enabled; then
  gw_bearer="$(env_file_value GATEWAY_INTERNAL_BEARER_TOKEN)"
  if [[ -z "${gw_bearer//[[:space:]]/}" ]]; then
    echo "WARN: GATEWAY_INTERNAL_BEARER_TOKEN is empty while GATEWAY_DEV_MODE is not true — api-gateway recreate may crash (502)" >&2
    warns=$((warns + 1))
  elif [[ ${#gw_bearer} -lt 32 ]]; then
    echo "WARN: GATEWAY_INTERNAL_BEARER_TOKEN is shorter than 32 characters — api-gateway startup validation may fail" >&2
    warns=$((warns + 1))
  fi
fi

if [[ "$errors" -gt 0 ]]; then
  echo "validate-env-deploy: ${errors} error(s), ${warns} warning(s)" >&2
  exit 1
fi
echo "validate-env-deploy: passed (${warns} warning(s))"
