#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

[[ -f "$DMAS_MASTERCARD_LMK_FILE" ]] || {
  echo "[FAIL] LMK Mastercard absente : $DMAS_MASTERCARD_LMK_FILE" >&2
  exit 1
}
dmas_start_module sg-mc-dmas-mastercard \
  "$(dmas_url_port "$DMAS_MASTERCARD_URL")" \
  "--sg.interface=$DMAS_MASTERCARD_INTERFACE" \
  "--dmas.lmk.file=$DMAS_MASTERCARD_LMK_FILE"
echo "[OK] DMAS Mastercard pret : $DMAS_MASTERCARD_URL"
