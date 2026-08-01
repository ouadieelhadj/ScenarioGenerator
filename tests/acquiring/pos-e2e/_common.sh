#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
CONFIG_FILE="${ACQUIRING_E2E_CONFIG_FILE:-$ROOT/runtime/issuing-connected-e2e/connected-e2e.env}"
if [[ -f "$CONFIG_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$CONFIG_FILE"
  set +a
fi
# shellcheck source=../../../deploiement/common/runtime/platform-env.sh
source "$ROOT/deploiement/common/runtime/platform-env.sh"

RUNTIME="${POS_ACQUIRING_E2E_RUNTIME:-$ROOT/runtime/acquiring-pos-e2e}"
LOG_DIR="$RUNTIME/logs"
PID_DIR="$RUNTIME/pids"
ISSUING_URL="${CARD_ISSUING_BASE_URL:-http://127.0.0.1:8540}"
SERVER_URL="${WAY_POS_SERVER_BASE_URL:-http://127.0.0.1:8530}"
SIMULATOR_URL="${WAY_POS_SIMULATOR_BASE_URL:-http://127.0.0.1:8532}"
mkdir -p "$LOG_DIR" "$PID_DIR"

fail() { printf '[POS E2E] ERREUR - %s\n' "$*" >&2; exit 1; }
require_var() { [[ -n "${!1-}" ]] || fail "Variable requise absente: $1"; }
require_command() { command -v "$1" >/dev/null 2>&1 || fail "Commande absente: $1"; }

wait_http() {
  local url="$1" label="$2"
  for _ in $(seq 1 90); do
    curl -fsS --connect-timeout 2 --max-time 3 "$url" >/dev/null 2>&1 && {
      printf '[POS E2E] UP - %s\n' "$label"
      return 0
    }
    sleep 1
  done
  fail "$label indisponible: $url"
}

pid_alive() {
  [[ "$1" =~ ^[0-9]+$ ]] && kill -0 "$1" 2>/dev/null
}

start_jar() {
  local module="$1" label="$2" health="$3"
  shift 3
  local jar="$ROOT/$module/target/$module-1.0.0-SNAPSHOT.jar"
  local pid_file="$PID_DIR/$module.pid" log_file="$LOG_DIR/$label.log" pid
  [[ -f "$jar" ]] || fail "JAR absent: $jar; lancez 00-build.sh"
  if curl -fsS --connect-timeout 2 --max-time 3 "$health" >/dev/null 2>&1; then
    fail "$label utilise deja son port; arretez l'instance avant ce test"
  fi
  if [[ -f "$pid_file" ]] && pid_alive "$(tr -d '[:space:]' <"$pid_file")"; then
    fail "$label est deja lance par ce parcours"
  fi
  nohup "$JAVA" -jar "$jar" "$@" >"$log_file" 2>&1 &
  pid=$!
  printf '%s\n' "$pid" >"$pid_file"
  if ! wait_http "$health" "$label"; then
    tail -80 "$log_file" >&2 || true
    exit 1
  fi
}

stop_module() {
  local module="$1" label="$2" pid_file pid win_pid
  pid_file="$PID_DIR/$module.pid"
  [[ -f "$pid_file" ]] || return 0
  pid="$(tr -d '[:space:]' <"$pid_file")"
  if pid_alive "$pid"; then
    kill "$pid" 2>/dev/null || true
    sleep 1
  fi
  if pid_alive "$pid"; then
    win_pid="$(ps -W | awk -v target="$pid" '$1 == target {print $4; exit}')"
    [[ "$win_pid" =~ ^[0-9]+$ ]] && taskkill.exe //PID "$win_pid" //T //F >/dev/null 2>&1 || true
  fi
  rm -f -- "$pid_file"
  printf '[POS E2E] STOP - %s\n' "$label"
}

psql_value() {
  require_var DB_PASSWORD
  PGPASSWORD="$DB_PASSWORD" "$PSQL" --no-password -h "${DB_HOST:-localhost}" \
    -p "${DB_PORT:-5432}" -U "${DB_USER:-postgres}" \
    -d "${DB_NAME:-scenariogenerator}" -tA -c "$1" | tr -d '\r\n'
}

require_command curl
require_command python
