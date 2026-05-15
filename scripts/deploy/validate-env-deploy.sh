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

if [[ "$errors" -gt 0 ]]; then
  echo "validate-env-deploy: ${errors} error(s), ${warns} warning(s)" >&2
  exit 1
fi
echo "validate-env-deploy: passed (${warns} warning(s))"
