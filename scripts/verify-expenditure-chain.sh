#!/usr/bin/env bash
# Quick check: billing-service direct + api-gateway -> billing for GET /api/v1/expenditure/api-keys.
# See verify-expenditure-chain.ps1 header for env vars (JWT path when GATEWAY_DEV_MODE=false).

set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$REPO_ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

BILLING_PORT="${BILLING_SERVICE_PORT:-8095}"
GW_PORT="${API_GATEWAY_PORT:-8080}"
SECRET="${GATEWAY_SHARED_SECRET:-local-dev-gateway-shared-secret-do-not-use-in-prod}"
USER_ID="expenditure-chain-verify"
PATH_Q="/api/v1/expenditure/api-keys"
FAILED=0

GW_DEV_RAW="${GATEWAY_DEV_MODE:-true}"
GW_DEV_LC="$(echo "$GW_DEV_RAW" | tr '[:upper:]' '[:lower:]')"

code1="$(curl -s -o /dev/null -w "%{http_code}" -H "X-User-Id: ${USER_ID}" -H "X-Gateway-Auth: ${SECRET}" "http://127.0.0.1:${BILLING_PORT}${PATH_Q}" || true)"
echo "1) Direct billing-service :${BILLING_PORT}${PATH_Q}"
if [[ "$code1" == "200" ]]; then
  echo "   OK HTTP $code1"
else
  echo "   FAIL HTTP $code1 — ensure billing is up and billing_db reachable (see docs/billing-service-overview §6.4)"
  FAILED=1
fi

echo "2) Via api-gateway :${GW_PORT}${PATH_Q}"

fetch_jwt() {
  if [[ -n "${EXPENDITURE_VERIFY_GATEWAY_JWT:-}" ]]; then
    echo "   Using EXPENDITURE_VERIFY_GATEWAY_JWT" >&2
    printf '%s' "$EXPENDITURE_VERIFY_GATEWAY_JWT"
    return 0
  fi
  if [[ -z "${EXPENDITURE_VERIFY_LOGIN_EMAIL:-}" || -z "${EXPENDITURE_VERIFY_LOGIN_PASSWORD:-}" ]]; then
    return 1
  fi
  local base="${EXPENDITURE_VERIFY_IDENTITY_URL:-}"
  if [[ -z "$base" ]]; then
    base="${IDENTITY_SERVICE_URL:-http://127.0.0.1:8090}"
  fi
  base="${base%/}"
  if ! command -v python3 >/dev/null 2>&1; then
    echo "   WARN python3 not found — set EXPENDITURE_VERIFY_GATEWAY_JWT or install python3 for login-based JWT" >&2
    return 1
  fi
  echo "   Fetching JWT via identity login (${base}/api/auth/login)" >&2
  python3 - "$base" <<'PY'
import json, os, sys, urllib.error, urllib.request
base = sys.argv[1]
email = os.environ.get("EXPENDITURE_VERIFY_LOGIN_EMAIL", "")
password = os.environ.get("EXPENDITURE_VERIFY_LOGIN_PASSWORD", "")
req = urllib.request.Request(
    f"{base}/api/auth/login",
    data=json.dumps({"email": email, "password": password}).encode(),
    headers={"Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        body = json.loads(r.read().decode())
    tok = body.get("data", {}).get("accessToken")
    if not tok:
        print("login: missing data.accessToken", file=sys.stderr)
        sys.exit(1)
    print(tok, end="")
except urllib.error.HTTPError as e:
    print("login HTTP", e.code, file=sys.stderr)
    sys.exit(1)
PY
}

case "$GW_DEV_LC" in
  true|1|yes|"")
    echo "   (GATEWAY_DEV_MODE=true: X-User-Id only)"
    code2="$(curl -s -o /dev/null -w "%{http_code}" -H "X-User-Id: ${USER_ID}" "http://127.0.0.1:${GW_PORT}${PATH_Q}" || true)"
    if [[ "$code2" == "200" ]]; then
      echo "   OK HTTP $code2"
    elif [[ "$code2" == "000" ]]; then
      echo "   FAIL connection error — start api-gateway-service (port ${GW_PORT}) or fix GATEWAY_BILLING_URI"
      FAILED=1
    elif [[ "$code2" == "502" || "$code2" == "503" ]]; then
      echo "   FAIL HTTP $code2 — gateway cannot reach billing; align GATEWAY_BILLING_URI with BILLING_SERVICE_PORT (default 8095)"
      FAILED=1
    else
      echo "   FAIL HTTP $code2 — check gateway logs"
      FAILED=1
    fi
    ;;
  *)
    echo "   (GATEWAY_DEV_MODE=false: JWT required)"
    JWT="$(fetch_jwt || true)"
    if [[ -z "$JWT" ]]; then
      echo "   SKIPPED — set EXPENDITURE_VERIFY_GATEWAY_JWT or EXPENDITURE_VERIFY_LOGIN_EMAIL + EXPENDITURE_VERIFY_LOGIN_PASSWORD"
      echo "            Ensure GATEWAY_JWT_SECRET matches identity JWT_SECRET."
    else
      code2="$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${JWT}" "http://127.0.0.1:${GW_PORT}${PATH_Q}" || true)"
      if [[ "$code2" == "200" ]]; then
        echo "   OK HTTP $code2"
      elif [[ "$code2" == "000" ]]; then
        echo "   FAIL connection error — start api-gateway-service (port ${GW_PORT})"
        FAILED=1
      elif [[ "$code2" == "401" || "$code2" == "403" ]]; then
        echo "   FAIL HTTP $code2 — JWT rejected (align GATEWAY_JWT_SECRET with identity JWT_SECRET)"
        FAILED=1
      elif [[ "$code2" == "502" || "$code2" == "503" ]]; then
        echo "   FAIL HTTP $code2 — gateway cannot reach billing"
        FAILED=1
      else
        echo "   FAIL HTTP $code2 — check gateway and billing logs"
        FAILED=1
      fi
    fi
    ;;
esac

exit "$FAILED"
