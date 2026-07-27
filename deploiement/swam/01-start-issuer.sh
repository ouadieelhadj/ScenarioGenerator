#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

swam_start_module sg-swam-issuer SWAM_NETWORK_1 \
  "$SWAM_ISSUER_URL/api/swam/issuer/health"
echo "SWAM Issuer pret : $SWAM_ISSUER_URL"
