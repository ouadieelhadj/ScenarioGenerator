#!/usr/bin/env bash
set -euo pipefail
set +H
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

cleanup() {
  [[ "${SWAM_KEEP_RUNNING:-false}" == "true" ]] ||
    bash "$SCRIPT_DIR/06-stop-swam.sh" || true
}
trap cleanup EXIT

bash "$SCRIPT_DIR/01-start-issuer.sh"
bash "$SCRIPT_DIR/02-start-member.sh"
bash "$SCRIPT_DIR/03-bootstrap-keys.sh"
bash "$SCRIPT_DIR/04-run-purchases.sh"
bash "$SCRIPT_DIR/05-run-lis-clearing.sh"

echo "RESULTAT : SWAM FULL E2E PASSED"
