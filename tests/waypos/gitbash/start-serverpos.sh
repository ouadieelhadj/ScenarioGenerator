#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

require_var WAY_POS_DB_PASSWORD
require_var WAY_POS_PAN_PEPPER
require_var WAY_POS_OUTBOX_KEY_HEX
require_var WAY_POS_LMK_FILE
[[ -f "$WAY_POS_LMK_FILE" ]] \
  || fail "LMK WayPos absente: configurez WAY_POS_LMK_FILE"

default_server_jar="$ROOT/sg-way-pos-server/target/sg-way-pos-server-1.0.0-SNAPSHOT.jar"
bootstrap_server_jar="$ROOT/sg-way-pos-server/target/sg-way-pos-server-1.0.0-SNAPSHOT-bootstrap.jar"
bootstrap2_server_jar="$ROOT/sg-way-pos-server/target/sg-way-pos-server-1.0.0-SNAPSHOT-bootstrap2.jar"
if [[ ! -f "$default_server_jar" && -f "$bootstrap2_server_jar" ]]; then
  default_server_jar="$bootstrap2_server_jar"
elif [[ ! -f "$default_server_jar" && -f "$bootstrap_server_jar" ]]; then
  default_server_jar="$bootstrap_server_jar"
fi
SERVER_JAR="${WAY_POS_SERVER_JAR:-$default_server_jar}"
[[ -f "$SERVER_JAR" ]] \
  || fail "JAR ServerPOS absent. Exécutez d'abord le build Maven."
[[ -x "$JAVA" ]] || fail "Java absent ou non exécutable: $JAVA"

PID_FILE="$WAY_POS_PID_DIR/serverpos.pid"
LOG_FILE="$WAY_POS_LOG_DIR/serverpos-console.log"
check_not_running "$PID_FILE" "ServerPOS"

export WAY_POS_LOG_FILE="${WAY_POS_LOG_FILE:-$WAY_POS_LOG_DIR/serverpos.log}"
export WAY_POS_DB_URL="${WAY_POS_DB_URL:-jdbc:postgresql://${DB_HOST:-localhost}:${DB_PORT:-5432}/${DB_NAME:-scenariogenerator}}"
export WAY_POS_DB_USER="${WAY_POS_DB_USER:-${DB_USER:-postgres}}"
export DMAS_LMK_FILE="$WAY_POS_LMK_FILE"
export WAY_POS_LOCAL_TEST_BOOTSTRAP_ENABLED="${WAY_POS_LOCAL_TEST_BOOTSTRAP_ENABLED:-true}"
export WAY_POS_RKI_LOG_KCV_ENABLED="${WAY_POS_RKI_LOG_KCV_ENABLED:-true}"
export JAVA_TOOL_OPTIONS="${WAY_POS_JAVA_TOOL_OPTIONS:-${ISSUING_E2E_JAVA_TOOL_OPTIONS:--Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC}}"

way4_rki_args=("--way-pos.rki-generate-tr31-enabled=true")

nohup "$JAVA" -jar "$SERVER_JAR" \
  --spring.profiles.active=connected-e2e \
  --server.address=127.0.0.1 \
  "${way4_rki_args[@]}" </dev/null >"$LOG_FILE" 2>&1 &
printf '%s\n' "$!" >"$PID_FILE"
disown || true

if ! wait_http "$SERVER_BASE_URL/api/routing/v1/health" "ServerPOS" 90; then
  tail -n 80 "$LOG_FILE" >&2 || true
  exit 1
fi
printf 'ServerPOS lancé. PID=%s, ISO=%s:%s, log=%s\n' \
  "$(<"$PID_FILE")" "${WAY_POS_SERVER_HOST:-127.0.0.1}" \
  "${WAY_POS_ISO_PORT:-8531}" "$LOG_FILE"
