#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

for name in CARD_ISSUING_DB_PASSWORD WAY_POS_DB_PASSWORD WAY_POS_LMK_FILE \
  WAY_POS_PAN_PEPPER WAY_POS_OUTBOX_KEY_HEX WAY_POS_TAK_HEX \
  WAY_POS_TAMK_HEX WAY_POS_TPMK_HEX; do
  require_var "$name"
done
[[ -f "$WAY_POS_LMK_FILE" ]] || fail "LMK WayPos introuvable"
export JAVA_TOOL_OPTIONS="${ISSUING_E2E_JAVA_TOOL_OPTIONS:--Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC}"

export CARD_ISSUING_CORE_BANKING_SANDBOX_ENABLED=true
export CARD_ISSUING_LOG_FILE="$LOG_DIR/card-issuing-app.log"
start_jar sg-card-issuing card-issuing \
  "$ISSUING_URL/api/issuing/v1/health" \
  --spring.profiles.active=connected-e2e --server.address=127.0.0.1

export WAY_POS_LOG_FILE="$LOG_DIR/serverpos-app.log"
export WAY_POS_DB_URL="jdbc:postgresql://${DB_HOST:-localhost}:${DB_PORT:-5432}/${DB_NAME:-scenariogenerator}"
export WAY_POS_DB_USER="${WAY_POS_DB_USER:-way_pos_user}"
export DMAS_LMK_FILE="$WAY_POS_LMK_FILE"
start_jar sg-way-pos-server serverpos \
  "$SERVER_URL/api/routing/v1/health" \
  --spring.profiles.active=connected-e2e --server.address=127.0.0.1

export WAY_POS_SERVER_HOST=127.0.0.1
export WAY_POS_SERVER_ISO_PORT="${WAY_POS_ISO_PORT:-8531}"
export WAY_POS_SIMULATOR_REST_PORT="${WAY_POS_SIMULATOR_REST_PORT:-8532}"
export WAY_POS_TERMINAL_ID="${WAY_POS_TERMINAL_ID:-TERM0001}"
export WAY_POS_MERCHANT_ID="${WAY_POS_MERCHANT_ID:-MERCHANT0000001}"
export WAY_POS_CURRENCY="${WAY_POS_CURRENCY:-${ISSUING_E2E_CURRENCY:-504}}"
export WAY_POS_MAC_MODE="${WAY_POS_MAC_MODE:-BIN}"
export WAY_POS_SIMULATOR_LOG_FILE="$LOG_DIR/pos-simulator-app.log"
start_jar sg-way-pos-simulator pos-simulator \
  "$SIMULATOR_URL/api/simulator/v1/health" \
  --spring.profiles.active=connected-e2e --server.address=127.0.0.1

printf '[POS E2E] Services demarres.\n'
