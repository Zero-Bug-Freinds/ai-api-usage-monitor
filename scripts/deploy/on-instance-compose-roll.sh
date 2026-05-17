#!/usr/bin/env bash
# Run on the EC2 host (via SSM). Pulls ECR images and restarts the stack using `docker compose` (Compose v2 plugin).
# Compose file defaults to `docker-compose-prod.yml` (filename in repo root; not the legacy `docker-compose` CLI).
# Prerequisites: Docker, compose plugin, AWS CLI; repo at DEPLOY_ROOT; `.env.deploy` with secrets (not in git).
#
# Env (required unless noted):
#   IMAGE_TAG              — immutable tag (git sha) to deploy
# Optional:
#   DEPLOY_ROOT            — default /opt/ai-api-usage-monitor
#   ENV_DEPLOY_FILE        — default .env.deploy (relative to DEPLOY_ROOT or absolute)
#   ECR_REPOSITORY_PREFIX  — suffix after account ECR host when deriving ECR_IMAGE_PREFIX (default ai-api-usage-monitor)
#   DEPLOY_STATE_DIR       — unset: root → /var/lib/...; non-root → ~/.local/state/... (override anytime)
#   COMPOSE_FILE           — default compose file name docker-compose-prod.yml (passed to `docker compose -f`)
#   HEALTH_URL             — default http://127.0.0.1:8080/healthz (web-edge :8080 listener)
#   HEALTH_RETRIES         — default 30
#   HEALTH_INTERVAL_SEC    — default 2
#   AWS_REGION             — for `aws ecr get-login-password` (optional if already logged in to ECR)

set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/ai-api-usage-monitor}"
ENV_DEPLOY_FILE="${ENV_DEPLOY_FILE:-.env.deploy}"
ECR_REPOSITORY_PREFIX="${ECR_REPOSITORY_PREFIX:-ai-api-usage-monitor}"
if [[ -z "${DEPLOY_STATE_DIR:-}" ]]; then
  if [[ "$(id -u)" -eq 0 ]]; then
    DEPLOY_STATE_DIR="/var/lib/ai-api-usage-monitor-deploy"
  else
    DEPLOY_STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/ai-api-usage-monitor-deploy"
  fi
fi
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose-prod.yml}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/healthz}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SEC="${HEALTH_INTERVAL_SEC:-2}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}"

COMPOSE_ENV_FILE=""
ENV_DEPLOY_SOURCE=""

resolve_env_deploy_path() {
  if [[ "$ENV_DEPLOY_FILE" = /* ]]; then
    printf '%s' "$ENV_DEPLOY_FILE"
  else
    printf '%s' "${DEPLOY_ROOT%/}/$ENV_DEPLOY_FILE"
  fi
}

ensure_env_deploy_readable() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "Missing deploy env file: $path" >&2
    echo "Copy .env.deploy.example to .env.deploy on the host and fill secrets (see docs/aws-github-oidc-ecr-ssm.md)." >&2
    exit 1
  fi
  if [[ -r "$path" ]]; then
    return 0
  fi
  echo "Deploy env file not readable: $path" >&2
  if [[ "$(id -u)" -eq 0 ]]; then
    chown root:root "$path" 2>/dev/null || true
    chmod 600 "$path" 2>/dev/null || true
  elif command -v sudo >/dev/null 2>&1; then
    sudo chown root:root "$path"
    sudo chmod 600 "$path"
  fi
  if [[ ! -r "$path" ]]; then
    echo "Still cannot read $path — set mode 600 and owner readable by the deploy user (root for SSM)." >&2
    exit 1
  fi
}

# Copy to a readable temp file (CRLF-safe) before parsing — never grep the original if unreadable.
normalize_env_source_copy() {
  local source_path="$1"
  local dest_copy="$2"
  sed 's/\r$//' "$source_path" >"$dest_copy"
  chmod 600 "$dest_copy"
}

# Trim whitespace and optional surrounding quotes (compose .env must be unquoted KEY=value).
sanitize_env_value() {
  local v="$1"
  v="$(printf '%s' "$v" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  if [[ "$v" =~ ^\"(.*)\"$ ]]; then
    v="${BASH_REMATCH[1]}"
  elif [[ "$v" =~ ^\'(.*)\'$ ]]; then
    v="${BASH_REMATCH[1]}"
  fi
  printf '%s' "$v"
}

# Rewrite .env.deploy → sanitized copy: no quotes, no :5432 on *_POSTGRES_HOST (port is *_POSTGRES_PORT).
sanitize_env_deploy_file() {
  local src="$1"
  local dst="$2"
  local line key val host port_hint port_var
  : >"$dst"
  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" =~ ^[[:space:]]*# ]]; then
      printf '%s\n' "$line" >>"$dst"
      continue
    fi
    line="$(printf '%s' "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [[ -z "$line" ]] && continue
    [[ "$line" != *=* ]] && continue
    key="${line%%=*}"
    val="${line#*=}"
    key="$(sanitize_env_value "$key")"
    val="$(sanitize_env_value "$val")"
    if [[ "$key" =~ _POSTGRES_HOST$ ]] && [[ "$val" == *:* ]]; then
      host="${val%%:*}"
      port_hint="${val##*:}"
      port_var="${key%_HOST}_PORT"
      if [[ -n "$port_hint" && "$port_hint" =~ ^[0-9]+$ ]]; then
        if ! grep -q "^${port_var}=" "$dst" 2>/dev/null; then
          printf '%s=%s\n' "$port_var" "$port_hint" >>"$dst"
        fi
        echo "Normalized ${key}: hostname only (${host}); port belongs in ${port_var}=${port_hint}" >&2
      else
        echo "WARN: ${key} contained ':' but port suffix '${port_hint}' is not numeric — using host '${host}'" >&2
      fi
      val="$host"
    fi
    printf '%s=%s\n' "$key" "$val" >>"$dst"
  done <"$src"
  chmod 600 "$dst"
}

load_deploy_variables() {
  local normalized="$1"
  set -a
  # shellcheck source=/dev/null
  source "$normalized"
  set +a
  sanitize_loaded_postgres_hosts
  export IMAGE_TAG
  if [[ -z "${ECR_IMAGE_PREFIX:-}" ]]; then
    if [[ -z "${AWS_REGION:-}" ]]; then
      echo "ECR_IMAGE_PREFIX is unset and AWS_REGION is missing — cannot derive registry." >&2
      exit 1
    fi
    local account
    account="$(aws sts get-caller-identity --query Account --output text)"
    export ECR_IMAGE_PREFIX="${account}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY_PREFIX}"
    echo "Derived ECR_IMAGE_PREFIX=${ECR_IMAGE_PREFIX}"
  fi
  if [[ -z "${ECR_IMAGE_PREFIX//[[:space:]]/}" ]] || [[ -z "${IMAGE_TAG//[[:space:]]/}" ]]; then
    echo "ECR_IMAGE_PREFIX and IMAGE_TAG must be non-empty (got ECR_IMAGE_PREFIX='${ECR_IMAGE_PREFIX}' IMAGE_TAG='${IMAGE_TAG}')" >&2
    exit 1
  fi
}

sanitize_loaded_postgres_hosts() {
  local name val port_var port_val
  for name in IDENTITY_POSTGRES_HOST USAGE_POSTGRES_HOST BILLING_POSTGRES_HOST TEAM_POSTGRES_HOST; do
    val="${!name:-}"
    [[ -z "$val" ]] && continue
    val="$(sanitize_env_value "$val")"
    export "$name=$val"
  done
  for name in IDENTITY_POSTGRES_PORT USAGE_POSTGRES_PORT BILLING_POSTGRES_PORT TEAM_POSTGRES_PORT; do
    val="${!name:-}"
    [[ -z "$val" ]] && continue
    export "$name=$(sanitize_env_value "$val")"
  done
}

log_postgres_host_summary() {
  echo "Postgres hosts for compose (hostname only, port 5432 via *_POSTGRES_PORT):"
  echo "  IDENTITY_POSTGRES_HOST=${IDENTITY_POSTGRES_HOST:-<unset>}"
  echo "  USAGE_POSTGRES_HOST=${USAGE_POSTGRES_HOST:-<unset>}"
  echo "  BILLING_POSTGRES_HOST=${BILLING_POSTGRES_HOST:-<unset>}"
  echo "  TEAM_POSTGRES_HOST=${TEAM_POSTGRES_HOST:-<unset>}"
}

write_compose_env_file() {
  local normalized_source="$1"
  local dest_path="$2"
  local tmp
  tmp="$(mktemp)"
  {
    echo "# Generated by on-instance-compose-roll.sh — do not commit"
    echo "IMAGE_TAG=${IMAGE_TAG}"
    echo "ECR_IMAGE_PREFIX=${ECR_IMAGE_PREFIX}"
    while IFS= read -r line || [[ -n "$line" ]]; do
      [[ "$line" =~ ^[[:space:]]*# ]] && continue
      line="$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
      [[ -z "$line" ]] && continue
      [[ "$line" =~ ^(IMAGE_TAG|ECR_IMAGE_PREFIX)= ]] && continue
      printf '%s\n' "$line"
    done <"$normalized_source"
  } >"$tmp"
  chmod 600 "$tmp"
  mv -f "$tmp" "$dest_path"
}

# Compose auto-loads `.env` from the project directory; manual `docker compose` without --env-file needs this.
publish_compose_env_files() {
  local merged_path="$1"
  cp -f "$merged_path" "${DEPLOY_ROOT}/.env"
  chmod 600 "${DEPLOY_ROOT}/.env"
  chmod +x "${DEPLOY_ROOT}/scripts/deploy/docker-compose-prod.sh" 2>/dev/null || true
  echo "Published compose env: ${COMPOSE_ENV_FILE} and ${DEPLOY_ROOT}/.env"
  echo "Manual compose: cd ${DEPLOY_ROOT} && sudo ./scripts/deploy/docker-compose-prod.sh logs -f"
}

verify_rds_reachability() {
  local host="${IDENTITY_POSTGRES_HOST:-${USAGE_POSTGRES_HOST:-}}"
  if [[ -z "${host//[[:space:]]/}" ]]; then
    echo "WARN: IDENTITY_POSTGRES_HOST / USAGE_POSTGRES_HOST unset — RDS check skipped" >&2
    return 0
  fi
  if ! getent hosts "$host" >/dev/null 2>&1; then
    echo "RDS DNS failed for $host (check VPC DNS and .env.deploy host values)" >&2
    return 1
  fi
  if command -v nc >/dev/null 2>&1; then
    if ! nc -z -w 5 "$host" 5432 2>/dev/null; then
      echo "RDS TCP 5432 unreachable from this host ($host) — check staging RDS SG allows EC2 instance SG (terraform enable_staging_rds) and logical DBs exist (scripts/deploy/rds-staging-create-logical-dbs.sh)" >&2
      return 1
    fi
  fi
  echo "RDS reachability OK ($host:5432)"
}

validate_compose_interpolation() {
  local sample
  sample="$(compose_cmd config 2>&1 | head -n 5 || true)"
  if compose_cmd config >/dev/null 2>&1; then
    :
  else
    echo "docker compose config failed — env may be invalid:" >&2
    echo "$sample" >&2
    exit 1
  fi
  if ! compose_cmd config 2>/dev/null | grep -q "${ECR_IMAGE_PREFIX}/identity-service:${IMAGE_TAG}"; then
    echo "Compose config does not reference expected image ${ECR_IMAGE_PREFIX}/identity-service:${IMAGE_TAG}" >&2
    compose_cmd config 2>/dev/null | grep -E '^\s+image:' | head -n 5 >&2 || true
    exit 1
  fi
  echo "Compose interpolation OK (identity-service image tag present)"
}

stop_bootstrap_compose() {
  docker rm -f aio-bootstrap-web-edge 2>/dev/null || true
  local boot_env="${DEPLOY_STATE_DIR}/bootstrap.env"
  if [[ ! -f "${DEPLOY_ROOT}/docker-compose-prod.bootstrap.yml" ]] || [[ ! -f "$boot_env" ]]; then
    return 0
  fi
  if docker compose -p aio-bootstrap -f docker-compose-prod.bootstrap.yml --env-file "$boot_env" ps -q 2>/dev/null | grep -q .; then
    echo "Stopping bootstrap web-edge (aio-bootstrap) before full stack deploy"
    docker compose -p aio-bootstrap -f docker-compose-prod.bootstrap.yml --env-file "$boot_env" down --remove-orphans 2>/dev/null || true
  fi
}

compose_cmd() {
  # Export critical vars so interpolation works even if --env-file is ignored by a plugin bug.
  env IMAGE_TAG="$IMAGE_TAG" ECR_IMAGE_PREFIX="$ECR_IMAGE_PREFIX" \
    docker compose -f "$COMPOSE_FILE" --env-file "$COMPOSE_ENV_FILE" "$@"
}

prepare_compose_environment() {
  local raw normalized
  raw="$(mktemp "${DEPLOY_STATE_DIR}/env-raw.XXXXXX")"
  normalized="$(mktemp "${DEPLOY_STATE_DIR}/env-src.XXXXXX")"
  normalize_env_source_copy "$ENV_DEPLOY_SOURCE" "$raw"
  sanitize_env_deploy_file "$raw" "$normalized"
  rm -f "$raw"
  load_deploy_variables "$normalized"
  log_postgres_host_summary
  write_compose_env_file "$normalized" "$COMPOSE_ENV_FILE"
  publish_compose_env_files "$COMPOSE_ENV_FILE"
  rm -f "$normalized"
  verify_rds_reachability
  validate_compose_interpolation
}

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
  prepare_compose_environment
  (cd "$DEPLOY_ROOT" && compose_cmd pull && compose_cmd up -d --force-recreate)
}

cd "$DEPLOY_ROOT"
stop_bootstrap_compose

ENV_DEPLOY_SOURCE="$(resolve_env_deploy_path)"
ensure_env_deploy_readable "$ENV_DEPLOY_SOURCE"
if [[ -x "${DEPLOY_ROOT}/scripts/deploy/validate-env-deploy.sh" ]]; then
  bash "${DEPLOY_ROOT}/scripts/deploy/validate-env-deploy.sh" "$ENV_DEPLOY_SOURCE"
fi
COMPOSE_ENV_FILE="${DEPLOY_STATE_DIR}/compose.env"
prepare_compose_environment

if [[ -n "${AWS_REGION:-}" ]]; then
  ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
  REGISTRY="${ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"
fi

if ! compose_cmd pull; then
  echo "docker compose pull failed" >&2
  rollback || true
  exit 1
fi
if ! compose_cmd up -d --force-recreate --remove-orphans; then
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
  echo "If Java services show RDS errors, verify EC2 can reach Postgres (SG + private RDS in VPC):" >&2
  echo "  nc -zv \"\${IDENTITY_POSTGRES_HOST:-<rds-endpoint>}\" 5432" >&2
  rollback || true
  exit 1
fi

printf '%s' "$IMAGE_TAG" > "$STATE_FILE"
echo "Deploy OK; recorded last-success-sha=$IMAGE_TAG"
