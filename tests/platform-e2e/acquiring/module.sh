#!/usr/bin/env bash

# shellcheck disable=SC1091
source "$ROOT/deploiement/common/runtime/platform-env.sh"
POS_DIR="$ROOT/tests/acquiring/pos-e2e"
ECOM_DIR="$ROOT/tests/acquiring/ecommerce-e2e"
CHANNEL="${ACQUIRING_TEST_CHANNEL:-POS}"
ROUTE="${ECOMMERCE_ROUTE:-LOCAL_ISSUING}"

acquiring_delegate() {
  local pos_script="$1" ecom_script="$2"
  case "$CHANNEL" in
    POS) bash "$POS_DIR/$pos_script" ;;
    ECOMMERCE) bash "$ECOM_DIR/$ecom_script" "$ROUTE" ;;
    *) fail "ACQUIRING_TEST_CHANNEL doit valoir POS ou ECOMMERCE" ;;
  esac
}

stage_check_prerequisites() {
  require_git_bash
  for command in bash curl python; do require_command "$command"; done
  require_file "$MAVEN"
  for name in DB_PASSWORD CARD_ISSUING_DB_PASSWORD ISSUING_E2E_PAN \
    ISSUING_E2E_EXPIRY; do require_var "$name"; done
  if [[ "$CHANNEL" == "POS" ]]; then
    for name in WAY_POS_DB_PASSWORD WAY_POS_LMK_FILE WAY_POS_PAN_PEPPER \
      WAY_POS_OUTBOX_KEY_HEX WAY_POS_TAK_HEX WAY_POS_TAMK_HEX \
      WAY_POS_TPMK_HEX; do require_var "$name"; done
    require_file "$WAY_POS_LMK_FILE"
  elif [[ "$ROUTE" == "DMAS_MASTERCARD" ]]; then
    require_var DMAS_E2E_PAN; require_var DMAS_E2E_EXPIRY
  elif [[ "$ROUTE" == "SWAM" ]]; then
    require_var SWAM_E2E_PAN; require_var SWAM_E2E_EXPIRY
  fi
  info "Prerequis acquisition $CHANNEL, route $ROUTE disponibles"
}

stage_build() { acquiring_delegate 00-build.sh 00-build-and-install.sh; }
stage_start() { acquiring_delegate 01-start.sh 01-start.sh; }
stage_bootstrap_and_provision() { acquiring_delegate 02-provision.sh 02-provision.sh; }
stage_run_tests() { acquiring_delegate 03-purchase.sh 03-purchase.sh; }
stage_tail_logs() { acquiring_delegate 04-tail-logs.sh 04-tail-logs.sh; }
stage_stop() { acquiring_delegate 05-stop.sh 05-stop.sh; }

stage_check_results() {
  local response
  if [[ "$CHANNEL" == "POS" ]]; then
    response="${POS_ACQUIRING_E2E_RUNTIME:-$ROOT/runtime/acquiring-pos-e2e}/pos-purchase-response.json"
    require_file "$response"
    python - "$response" <<'PY'
import json, sys
r=json.load(open(sys.argv[1], encoding="utf-8"))
assert r.get("approved") is True and r.get("responseCode") == "00", r
print("[ACQUIRING E2E] OK - achat TPE approuve, RC=00")
PY
  else
    response="${ECOMMERCE_E2E_RUNTIME:-$ROOT/runtime/acquiring-ecommerce-e2e}/ecommerce-purchase-response.json"
    require_file "$response"
    python - "$response" "$ROUTE" <<'PY'
import json, sys
r=json.load(open(sys.argv[1], encoding="utf-8"))
assert r.get("status") == "APPROVED" and r.get("responseCode") == "00", r
assert r.get("networkRoute") == sys.argv[2], r
print("[ACQUIRING E2E] OK - achat e-commerce approuve, route="+sys.argv[2])
PY
  fi
}

module_run_all() {
  info "Parcours TPE/ServerPOS"
  ACQUIRING_TEST_CHANNEL=POS bash "$MODULE_DIR/00-check-prerequisites.sh"
  if [[ "${PLATFORM_E2E_SKIP_BUILD:-false}" == "true" ]]; then
    bash "$POS_DIR/01-start.sh"
    bash "$POS_DIR/02-provision.sh"
    bash "$POS_DIR/03-purchase.sh"
    bash "$POS_DIR/05-stop.sh"
  else
    bash "$POS_DIR/run-all.sh"
  fi
  ACQUIRING_TEST_CHANNEL=POS bash "$MODULE_DIR/05-check-results.sh"
  info "Parcours e-commerce local"
  ACQUIRING_TEST_CHANNEL=ECOMMERCE ECOMMERCE_ROUTE=LOCAL_ISSUING \
    bash "$MODULE_DIR/00-check-prerequisites.sh"
  ECOMMERCE_E2E_SKIP_BUILD="${PLATFORM_E2E_SKIP_BUILD:-false}" \
    bash "$ECOM_DIR/run-all.sh" LOCAL_ISSUING
  ACQUIRING_TEST_CHANNEL=ECOMMERCE ECOMMERCE_ROUTE=LOCAL_ISSUING \
    bash "$MODULE_DIR/05-check-results.sh"
}
