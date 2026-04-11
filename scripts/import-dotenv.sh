#!/usr/bin/env bash
# shellcheck disable=SC1091
# Usage: source ./import-dotenv.sh && import_dotenv "/path/to/.env"
set -euo pipefail

import_dotenv() {
  local envfile="$1"
  if [[ ! -f "$envfile" ]]; then
    echo "Env file not found: $envfile" >&2
    return 1
  fi
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line//$'\r'/}"
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line// }" ]] && continue
    [[ "$line" != *=* ]] && continue
    local key="${line%%=*}"
    key="$(echo -n "$key" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    local value="${line#*=}"
    value="${value#"${value%%[![:space:]]*}"}"
    if [[ ${#value} -ge 2 ]]; then
      if [[ "$value" == \"*\" && "$value" == *\" ]]; then
        value="${value:1:${#value}-2}"
      elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
        value="${value:1:${#value}-2}"
      fi
    fi
    export "$key=$value"
  done <"$envfile"
}
