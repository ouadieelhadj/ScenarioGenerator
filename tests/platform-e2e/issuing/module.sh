#!/usr/bin/env bash

# shellcheck disable=SC1091
source "$ROOT/deploiement/common/runtime/platform-env.sh"
POS_DIR="$ROOT/tests/acquiring/pos-e2e"
POS_RUNTIME="${POS_ACQUIRING_E2E_RUNTIME:-$ROOT/runtime/acquiring-pos-e2e}"

stage_check_prerequisites() {
  require_git_bash
  for command in bash curl python; do require_command "$command"; done
  require_file "$MAVEN"
  require_file "$JAVA"
  for name in DB_PASSWORD CARD_ISSUING_DB_PASSWORD WAY_POS_DB_PASSWORD \
    WAY_POS_LMK_FILE WAY_POS_PAN_PEPPER WAY_POS_OUTBOX_KEY_HEX \
    WAY_POS_TAK_HEX WAY_POS_TAMK_HEX WAY_POS_TPMK_HEX \
    ISSUING_E2E_PAN ISSUING_E2E_EXPIRY; do
    require_var "$name"
  done
  require_file "$WAY_POS_LMK_FILE"
  info "Prerequis Issuing/ServerPOS disponibles"
}

stage_build() { bash "$POS_DIR/00-build.sh"; }
stage_start() { bash "$POS_DIR/01-start.sh"; }
stage_bootstrap_and_provision() { bash "$POS_DIR/02-provision.sh"; }
stage_run_tests() { bash "$POS_DIR/03-purchase.sh"; }

stage_check_results() {
  local response="$POS_RUNTIME/pos-purchase-response.json"
  require_file "$response"
  python - "$response" <<'PY'
import json, sys
result = json.load(open(sys.argv[1], encoding="utf-8"))
if result.get("responseCode") != "00" or result.get("approved") is not True:
    raise SystemExit("Decision Issuing inattendue")
print("[ISSUING E2E] OK - achat local approuve, RC=00")
PY
}

stage_tail_logs() { bash "$POS_DIR/04-tail-logs.sh"; }
stage_stop() { bash "$POS_DIR/05-stop.sh"; }
