#!/usr/bin/env bash
set -euo pipefail
set +H

export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../common/runtime/platform-env.sh
source "$SCRIPT_DIR/../common/runtime/platform-env.sh"
DB="${DB:-$DB_NAME}"
E2E_LOGIN="${E2E_LOGIN:-admin}"
E2E_PASSWORD="${E2E_PASSWORD:-}"
FRONTEND_PORT="${FRONTEND_PORT:-4200}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
LOG_DIR="$ROOT/tmp/frontend-e2e"
STARTED_BACKEND=false
STARTED_FRONTEND=false

export E2E_LOGIN E2E_PASSWORD
export E2E_BASE_URL="http://localhost:$FRONTEND_PORT"
mkdir -p "$LOG_DIR"

[[ -n "$DB_PASSWORD" ]] || {
  echo "[ERREUR] DB_PASSWORD doit etre exporte avant le test." >&2
  exit 1
}
[[ -n "$E2E_PASSWORD" ]] || {
  echo "[ERREUR] E2E_PASSWORD doit etre exporte avant le test." >&2
  exit 1
}

is_up() { curl -fsS "$1" >/dev/null 2>&1; }
wait_up() {
  local url="$1" label="$2"
  for _ in $(seq 1 90); do
    if is_up "$url"; then echo "  [OK] $label"; return; fi
    sleep 1
  done
  echo "  [FAIL] $label indisponible" >&2
  exit 1
}
cleanup() {
  if [[ "$STARTED_FRONTEND" == true ]] && [[ -f "$LOG_DIR/frontend.pid" ]]; then
    taskkill.exe //F //PID "$(cat "$LOG_DIR/frontend.pid")" >/dev/null 2>&1 || true
  fi
  if [[ "$STARTED_BACKEND" == true ]] && [[ -f "$LOG_DIR/backend.pid" ]]; then
    taskkill.exe //F //PID "$(cat "$LOG_DIR/backend.pid")" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "=== E2E navigateur du portail modulaire ==="

PGPASSWORD="$DB_PASSWORD" "$PSQL" -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB" \
  -v ON_ERROR_STOP=1 -f "$ROOT/sql/18_portal_rbac_workflow.sql" >/dev/null
echo "  [OK] Migration portail"

if ! is_up "http://127.0.0.1:$BACKEND_PORT/api/status"; then
  "$MAVEN" -f "$ROOT/pom.xml" -pl sg-generator-orchestrator -am package \
    -DskipTests -Dmaven.repo.local="$ROOT/tmp/m2repo" >/dev/null
  jar=$(find "$ROOT/sg-generator-orchestrator/target" -maxdepth 1 \
    -name '*.jar' ! -name '*.original' | head -1)
  nohup "$JAVA" -jar "$jar" >"$LOG_DIR/backend.log" 2>&1 &
  echo $! >"$LOG_DIR/backend.pid"
  STARTED_BACKEND=true
fi
wait_up "http://127.0.0.1:$BACKEND_PORT/api/status" "Orchestrateur UP"

cd "$ROOT/sg-frontend"
[[ -d node_modules ]] || "$NPM" ci

if ! is_up "http://127.0.0.1:$FRONTEND_PORT"; then
  nohup "$NPM" start -- --host 127.0.0.1 --port "$FRONTEND_PORT" \
    >"$LOG_DIR/frontend.log" 2>&1 &
  echo $! >"$LOG_DIR/frontend.pid"
  STARTED_FRONTEND=true
fi
wait_up "http://127.0.0.1:$FRONTEND_PORT" "Frontend Angular UP"

"$NPX" playwright install chromium
"$NPX" playwright test
echo "RESULTAT : E2E FRONTEND PASSED"
