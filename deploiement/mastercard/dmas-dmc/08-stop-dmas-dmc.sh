#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

failed=0
dmas_stop_module sg-dmcs-issuer "$(dmas_url_port "$DMCS_ISSUER_URL")" || failed=1
dmas_stop_module sg-dmcs-acquirer "$(dmas_url_port "$DMCS_ACQUIRER_URL")" || failed=1
dmas_stop_module sg-mc-dmas-member "$(dmas_url_port "$DMAS_MEMBER_URL")" || failed=1
dmas_stop_module sg-mc-dmas-mastercard "$(dmas_url_port "$DMAS_MASTERCARD_URL")" || failed=1

if [[ "$failed" -ne 0 ]]; then
  echo "[FAIL] Un port cible reste occupe. Aucun autre processus Java n'a ete arrete." >&2
  exit 1
fi
echo "[OK] Processus DMAS/DMCS de cette livraison arretes"
