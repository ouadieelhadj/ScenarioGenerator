#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

for name in WAY_POS_TAK_HEX WAY_POS_TAMK_HEX WAY_POS_TPMK_HEX; do
  require_var "$name"
done
export WAY_POS_TAMK_ID="${WAY_POS_TAMK_ID:-00}"
export WAY_POS_TPMK_ID="${WAY_POS_TPMK_ID:-00}"
export WAY_POS_SERVER_HOST="${WAY_POS_SERVER_HOST:-127.0.0.1}"
export WAY_POS_SERVER_ISO_PORT="${WAY_POS_SERVER_ISO_PORT:-${WAY_POS_ISO_PORT:-8531}}"
export WAY_POS_TERMINAL_ID="${WAY_POS_TERMINAL_ID:-TERM0001}"
export WAY_POS_MERCHANT_ID="${WAY_POS_MERCHANT_ID:-MERCHANT0000001}"
export WAY_POS_CURRENCY="${WAY_POS_CURRENCY:-504}"
export WAY_POS_MAC_MODE="${WAY_POS_MAC_MODE:-BIN}"

wait_http "$SERVER_BASE_URL/api/routing/v1/health" "ServerPOS" 5

SIMULATOR_JAR="${WAY_POS_SIMULATOR_JAR:-$ROOT/sg-way-pos-simulator/target/sg-way-pos-simulator-1.0.0-SNAPSHOT.jar}"
[[ -f "$SIMULATOR_JAR" ]] \
  || fail "JAR simulateur absent. Exécutez d'abord le build Maven."
[[ -x "$JAVA" ]] || fail "Java absent ou non exécutable: $JAVA"

PID_FILE="$WAY_POS_PID_DIR/pos-simulator.pid"
LOG_FILE="$WAY_POS_LOG_DIR/pos-simulator-console.log"
check_not_running "$PID_FILE" "POS Simulator"
export JAVA_TOOL_OPTIONS="${WAY_POS_SIMULATOR_JAVA_TOOL_OPTIONS:-${ISSUING_E2E_JAVA_TOOL_OPTIONS:--Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC}}"

nohup "$JAVA" -jar "$SIMULATOR_JAR" \
  --spring.profiles.active=connected-e2e \
  --server.address=127.0.0.1 </dev/null >"$LOG_FILE" 2>&1 &
printf '%s\n' "$!" >"$PID_FILE"
disown || true

if ! wait_http "$SIMULATOR_BASE_URL/api/simulator/v1/health" "POS Simulator" 90; then
  tail -n 80 "$LOG_FILE" >&2 || true
  exit 1
fi
printf 'POS Simulator lancé. PID=%s, cible ISO=%s:%s, log=%s\n' \
  "$(<"$PID_FILE")" "$WAY_POS_SERVER_HOST" \
  "$WAY_POS_SERVER_ISO_PORT" "$LOG_FILE"
