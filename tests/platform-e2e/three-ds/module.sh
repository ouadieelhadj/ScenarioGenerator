#!/usr/bin/env bash

# shellcheck disable=SC1091
source "$ROOT/deploiement/common/runtime/platform-env.sh"
ECOM_DIR="$ROOT/tests/acquiring/ecommerce-e2e"
ECOM_RUNTIME="${ECOMMERCE_E2E_RUNTIME:-$ROOT/runtime/acquiring-ecommerce-e2e}"
export THREE_DS_ENABLED=true
export THREE_DS_PROGRAM="${THREE_DS_PROGRAM:-MASTERCARD}"
export THREE_DS_ISSUER_MODE=MEMBER

stage_check_prerequisites() {
  require_git_bash
  for command in bash curl python; do require_command "$command"; done
  require_file "$MAVEN"
  for name in DB_PASSWORD CARD_ISSUING_DB_PASSWORD ISSUING_E2E_PAN \
    ISSUING_E2E_EXPIRY THREE_DS_SANDBOX_HMAC_KEY \
    THREE_DS_NETWORK_SANDBOX_HMAC_KEY THREE_DS_SANDBOX_CHALLENGE_OTP; do
    require_var "$name"
  done
  [[ ${#THREE_DS_SANDBOX_HMAC_KEY} -ge 32 ]] || fail "Cle HMAC membre trop courte"
  [[ ${#THREE_DS_NETWORK_SANDBOX_HMAC_KEY} -ge 32 ]] || fail "Cle HMAC reseau trop courte"
}

stage_build() { bash "$ECOM_DIR/00-build-and-install.sh" LOCAL_ISSUING; }
stage_start() { bash "$ECOM_DIR/01-start.sh" LOCAL_ISSUING; }
stage_bootstrap_and_provision() { bash "$ECOM_DIR/02-provision.sh" LOCAL_ISSUING; }
stage_run_tests() {
  export MERCHANT_SITE_TYPE=NATIONAL THREE_DS_FLOW=FRICTIONLESS
  bash "$ECOM_DIR/03-purchase.sh" LOCAL_ISSUING
  export MERCHANT_SITE_TYPE=NATIONAL THREE_DS_FLOW=CHALLENGE
  bash "$ECOM_DIR/03-purchase.sh" LOCAL_ISSUING
  export MERCHANT_SITE_TYPE=INTERNATIONAL THREE_DS_FLOW=CHALLENGE
  bash "$ECOM_DIR/03-purchase.sh" LOCAL_ISSUING
  info "Trois scenarios 3DS executes"
}
stage_check_results() {
  local response="$ECOM_RUNTIME/ecommerce-purchase-response.json"
  require_file "$response"
  python - "$response" <<'PY'
import json, sys
r=json.load(open(sys.argv[1], encoding="utf-8"))
assert r.get("status") == "APPROVED" and r.get("responseCode") == "00", r
assert r.get("authenticationStatus") == "AUTHENTICATED", r
print("[3DS E2E] OK - dernier scenario authentifie et autorise, RC=00")
PY
}
stage_tail_logs() { bash "$ECOM_DIR/04-tail-logs.sh" LOCAL_ISSUING; }
stage_stop() { bash "$ECOM_DIR/05-stop.sh" LOCAL_ISSUING; }
