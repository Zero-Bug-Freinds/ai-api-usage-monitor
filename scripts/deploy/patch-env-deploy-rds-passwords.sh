#!/usr/bin/env bash
# Update RDS-related passwords in .env.deploy (staging single-RDS: same password for all services).
# Run on EC2 as root (SSM). Do not commit RDS_MASTER_PASSWORD.
#
#   export RDS_MASTER_PASSWORD='...'
#   sudo -E bash /opt/ai-api-usage-monitor/scripts/deploy/patch-env-deploy-rds-passwords.sh
#
# Optional: ENV_DEPLOY_FILE=/path/to/.env.deploy DEPLOY_ROOT=/opt/ai-api-usage-monitor

set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/ai-api-usage-monitor}"
ENV_FILE="${ENV_DEPLOY_FILE:-${DEPLOY_ROOT}/.env.deploy}"
RDS_MASTER_PASSWORD="${RDS_MASTER_PASSWORD:?Set RDS_MASTER_PASSWORD}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE" >&2
  exit 1
fi

# URL-encode password for postgresql:// URI (minimal: only chars that break URLs).
urlencode_password() {
  local s="$1"
  local out="" c
  for ((i = 0; i < ${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c" ;;
      *) printf -v hex '%%%02X' "'$c"
        out+="$hex" ;;
    esac
  done
  printf '%s' "$out"
}

ENC_PASS="$(urlencode_password "$RDS_MASTER_PASSWORD")"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^(IDENTITY|USAGE|BILLING|TEAM)_POSTGRES_PASSWORD= ]]; then
    printf '%s=%s\n' "${line%%=*}" "$RDS_MASTER_PASSWORD"
  elif [[ "$line" =~ ^NOTIFICATION_DATABASE_URL=postgresql:// ]]; then
    if [[ "$line" =~ ^NOTIFICATION_DATABASE_URL=postgresql://([^:@/]+):([^@]*)@([^/]+)/(.+)$ ]]; then
      user="${BASH_REMATCH[1]}"
      hostpart="${BASH_REMATCH[3]}"
      dbpath="${BASH_REMATCH[4]}"
      printf 'NOTIFICATION_DATABASE_URL=postgresql://%s:%s@%s/%s\n' "$user" "$ENC_PASS" "$hostpart" "$dbpath"
    else
      echo "WARN: could not parse NOTIFICATION_DATABASE_URL; line left unchanged" >&2
      printf '%s\n' "$line"
    fi
  else
    printf '%s\n' "$line"
  fi
done <"$ENV_FILE" >"$tmp"

chmod 600 "$tmp"
mv -f "$tmp" "$ENV_FILE"
chmod 600 "$ENV_FILE"

echo "Updated RDS passwords in $ENV_FILE"
grep -E '^(IDENTITY|USAGE|BILLING|TEAM)_POSTGRES_PASSWORD=' "$ENV_FILE" | sed 's/=.*/=***/'
