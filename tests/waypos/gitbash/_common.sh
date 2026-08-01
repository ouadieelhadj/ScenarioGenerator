#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
export ROOT

CONFIG_FILE="${WAY_POS_E2E_CONFIG_FILE:-$ROOT/runtime/issuing-connected-e2e/connected-e2e.env}"
if [[ -f "$CONFIG_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$CONFIG_FILE"
  set +a
fi

# shellcheck disable=SC1091
source "$ROOT/deploiement/common/runtime/platform-env.sh"

WAY_POS_RUNTIME="${WAY_POS_RUNTIME:-$ROOT/runtime/way-pos-gitbash}"
WAY_POS_LOG_DIR="${WAY_POS_LOG_DIR:-$WAY_POS_RUNTIME/logs}"
WAY_POS_PID_DIR="${WAY_POS_PID_DIR:-$WAY_POS_RUNTIME/pids}"
SERVER_BASE_URL="${WAY_POS_SERVER_BASE_URL:-http://127.0.0.1:${WAY_POS_REST_PORT:-8530}}"
SIMULATOR_BASE_URL="${WAY_POS_SIMULATOR_BASE_URL:-http://127.0.0.1:${WAY_POS_SIMULATOR_REST_PORT:-8532}}"
mkdir -p "$WAY_POS_LOG_DIR" "$WAY_POS_PID_DIR"

fail() {
  printf 'ERREUR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Commande requise absente: $1"
}

require_var() {
  [[ -n "${!1-}" ]] || fail "Variable requise absente: $1"
}

wait_http() {
  local url="$1" name="$2" attempts="${3:-60}"
  local i
  for ((i=1; i<=attempts; i++)); do
    if curl --silent --fail --connect-timeout 2 --max-time 3 "$url" >/dev/null; then
      printf '%s est prêt: %s\n' "$name" "$url"
      return 0
    fi
    sleep 1
  done
  fail "$name ne répond pas: $url"
}

check_not_running() {
  local pid_file="$1" name="$2"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(tr -d '\r\n' <"$pid_file")"
    if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
      fail "$name est déjà lancé (PID $pid)"
    fi
  fi
}

require_command curl
require_command python
