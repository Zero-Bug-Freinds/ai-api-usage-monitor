#!/usr/bin/env bash
set -euo pipefail
# Java services: ./gradlew bootRun. notification-service (NestJS): pnpm run start:dev.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"

usage() {
  echo "Usage: $0 <identity-service|usage-service|team-service|billing-service|api-gateway-service|proxy-service|agent-service|notification-service> [-- gradle args...]" >&2
  echo "  notification-service runs Nest (pnpm start:dev), not Gradle bootRun." >&2
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
  identity-service)
    export SERVER_PORT="${SERVER_PORT:-8090}"
    if [[ -z "${JWT_SECRET:-}" && -n "${GATEWAY_JWT_SECRET:-}" ]]; then
      export JWT_SECRET="$GATEWAY_JWT_SECRET"
    fi
    ;;
  usage-service) export USAGE_SERVICE_PORT="${USAGE_SERVICE_PORT:-8092}" ;;
  team-service) export TEAM_SERVICE_PORT="${TEAM_SERVICE_PORT:-8094}" ;;
  billing-service) export BILLING_SERVICE_PORT="${BILLING_SERVICE_PORT:-8095}" ;;
  agent-service) export AI_AGENT_SERVICE_PORT="${AI_AGENT_SERVICE_PORT:-8096}" ;;
  notification-service) export PORT="${PORT:-${NOTIFICATION_SERVICE_PORT:-8096}}" ;;
esac

SERVICE_DIR="$REPO_ROOT/services/$svc"
[[ -d "$SERVICE_DIR" ]] || { echo "Unknown service directory: $SERVICE_DIR" >&2; exit 1; }

cd "$SERVICE_DIR"
if [[ "$svc" == "notification-service" ]]; then
  exec pnpm run start:dev "$@"
fi
exec ./gradlew bootRun "$@"
