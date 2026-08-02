#!/usr/bin/env bash

# shellcheck disable=SC1091
source "$ROOT/deploiement/common/runtime/platform-env.sh"
VISA_SCRIPT="$ROOT/tests/visa/e2e/run-visa-online-base2.sh"
VISA_RUNTIME="${VISA_E2E_RUNTIME:-$ROOT/runtime/visa-e2e}"

stage_check_prerequisites() {
  require_git_bash
  for command in bash curl python; do require_command "$command"; done
  require_file "$MAVEN"; require_file "$JAVA"; require_file "$VISA_SCRIPT"
  info "Le sandbox Visa autonome ne requiert aucun secret"
}
stage_build() {
  "$MAVEN" -o -nsu -f "$ROOT/pom.xml" \
    -pl sg-visa-mastercard-gateway-simulator,sg-visa-online-member,sg-visa-visanet-simulator,sg-visa-base2-member,sg-visa-base2-network-simulator \
    -am package -Dmaven.test.skip=true -Dmaven.repo.local="$MAVEN_REPO"
}
stage_start() { info "Demarrage gere de facon atomique par 04-run-tests.sh"; }
stage_bootstrap_and_provision() { info "Aucun bootstrap secret requis par le sandbox Visa"; }
stage_run_tests() {
  VISA_E2E_SKIP_BUILD=true bash "$VISA_SCRIPT" 2>&1 | tee "$MODULE_RUNTIME/test-output.log"
}
stage_check_results() {
  local auth="$VISA_RUNTIME/results/authorization-response.json"
  local presentment="$VISA_RUNTIME/results/presentment-response.json"
  require_file "$auth"; require_file "$presentment"
  python - "$auth" "$presentment" <<'PY'
import json, sys
a=json.load(open(sys.argv[1], encoding="utf-8"))
p=json.load(open(sys.argv[2], encoding="utf-8"))
assert a.get("status") == "APPROVED" and a.get("networkResponseCode") == "00", a
assert p.get("networkStatus") == "ACCEPTED" and p.get("recordCount") == 5, p
print("[VISA E2E] OK - Online RC=00 et Base II accepte (5 records)")
PY
}
stage_tail_logs() { bash "$ROOT/tests/visa/e2e/tail-logs.sh"; }
stage_stop() { info "Le harnais Visa arrete uniquement les PID qu'il a crees"; }
