#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

swam_start_module sg-swam-acquirer SWAM_MEMBER_A \
  "$SWAM_MEMBER_URL/api/admin/swam/health"
echo "SWAM Membre pret : $SWAM_MEMBER_URL"
