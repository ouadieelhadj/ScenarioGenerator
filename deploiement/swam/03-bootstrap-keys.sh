#!/usr/bin/env bash
set -euo pipefail
set +H
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

swam_require_secret SWAM_E2E_KEK_CLEAR
export SWAM_E2E_KEK_CLEAR

bash "$SCRIPT_DIR/03a-bootstrap-issuer.sh"
bash "$SCRIPT_DIR/03b-bootstrap-member.sh"
bash "$SCRIPT_DIR/03c-signon-and-key-exchange.sh"

echo "[OK] Paramétrages unitaires et échange de clés terminés"
