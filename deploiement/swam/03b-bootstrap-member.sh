#!/usr/bin/env bash
set -euo pipefail
set +H
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

swam_require_secret SWAM_E2E_KEK_CLEAR
swam_wait "$SWAM_MEMBER_URL/api/admin/swam/health" "SWAM Membre"

body="{\"memberGroupId\":\"$SWAM_MEMBER_GROUP_ID\",\"kekClear\":\"$SWAM_E2E_KEK_CLEAR\"}"
response="$(swam_post "$SWAM_MEMBER_URL/api/admin/swam/kek/bootstrap" \
  -H 'Content-Type: application/json' -d "$body")"
grep -Eq '"side"[[:space:]]*:[[:space:]]*"ACQUIRER"' <<<"$response" || {
  echo "[FAIL] Bootstrap KEK membre rejeté" >&2
  exit 1
}
kcv="$(sed -n 's/.*"kcv"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$response")"
[[ -n "$kcv" ]] || {
  echo "[FAIL] KCV membre absent" >&2
  exit 1
}

echo "[OK] Paramétrage KEK membre terminé — KCV=$kcv"
