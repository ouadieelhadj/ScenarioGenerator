#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

[[ -f "$DMAS_MEMBER_LMK_FILE" ]] || {
  echo "[FAIL] LMK membre absente : $DMAS_MEMBER_LMK_FILE" >&2
  exit 1
}
dmas_start_module sg-mc-dmas-member \
  "$(dmas_url_port "$DMAS_MEMBER_URL")" \
  "--sg.interface=$DMAS_MEMBER_INTERFACE" \
  "--dmas.lmk.file=$DMAS_MEMBER_LMK_FILE"
echo "[OK] DMAS membre pret : $DMAS_MEMBER_URL"
