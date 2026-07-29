#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

cleanup() {
  if [[ "${DMAS_DMC_KEEP_RUNNING:-false}" != "true" ]]; then
    bash "$SCRIPT_DIR/08-stop-dmas-dmc.sh" || true
  fi
}
trap cleanup EXIT

bash "$SCRIPT_DIR/00-install-database.sh"
"$MAVEN" -f "$ROOT/pom.xml" \
  -pl sg-mc-dmas-member,sg-mc-dmas-mastercard,sg-dmcs-acquirer,sg-dmcs-issuer \
  -am verify -Dmaven.repo.local="$MAVEN_REPO"
bash "$SCRIPT_DIR/01-start-mastercard.sh"
bash "$SCRIPT_DIR/02-start-member.sh"
bash "$SCRIPT_DIR/03a-bootstrap-mastercard.sh"
bash "$SCRIPT_DIR/03b-bootstrap-member.sh"
bash "$SCRIPT_DIR/03c-signon-and-key-exchange.sh"
bash "$SCRIPT_DIR/04-test-pin.sh"
bash "$SCRIPT_DIR/04a-test-advice-reversal.sh"
bash "$SCRIPT_DIR/05-test-emv-arqc-arpc.sh"
bash "$SCRIPT_DIR/06-start-dmcs.sh"
bash "$SCRIPT_DIR/07-run-dmc-eod.sh"

echo "RESULTAT : PASSED"
echo "DMAS : PIN + ARQC/ARPC valides"
echo "DMC  : journaux proprietaires + EOD idempotents + codec jPOS/RDW valide"
