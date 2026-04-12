#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"

usage() {
  echo "Usage: $0 <identity-service|usage-service|team-service|billing-service|api-gateway-service|proxy-service> [-- gradle args...]" >&2
  exit 1
}

[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE — copy .env.example to .env" >&2; exit 1; }

svc="${1:-}"
[[ -n "$svc" ]] || usage
shift || true

if [[ "${1:-}" == "--" ]]; then
  shift
fi

# shellcheck source=import-dotenv.sh
source "$SCRIPT_DIR/import-dotenv.sh"
import_dotenv "$ENV_FILE"

case "$svc" in
  identity-service) export SERVER_PORT="${SERVER_PORT:-8090}" ;;
  usage-service) export USAGE_SERVICE_PORT="${USAGE_SERVICE_PORT:-8092}" ;;
  team-service) export TEAM_SERVICE_PORT="${TEAM_SERVICE_PORT:-8094}" ;;
  billing-service) export BILLING_SERVICE_PORT="${BILLING_SERVICE_PORT:-8095}" ;;
esac

SERVICE_DIR="$REPO_ROOT/services/$svc"
[[ -d "$SERVICE_DIR" ]] || { echo "Unknown service directory: $SERVICE_DIR" >&2; exit 1; }

cd "$SERVICE_DIR"
exec ./gradlew bootRun "$@"
