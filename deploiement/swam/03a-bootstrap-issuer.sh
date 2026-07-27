#!/usr/bin/env bash
set -euo pipefail
set +H
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

swam_require_secret SWAM_E2E_KEK_CLEAR
swam_wait "$SWAM_ISSUER_URL/api/swam/issuer/health" "SWAM Issuer"

body="{\"memberGroupId\":\"$SWAM_MEMBER_GROUP_ID\",\"kekClear\":\"$SWAM_E2E_KEK_CLEAR\"}"
response="$(swam_post "$SWAM_ISSUER_URL/api/admin/swam/kek/bootstrap" \
  -H 'Content-Type: application/json' -d "$body")"
grep -Eq '"side"[[:space:]]*:[[:space:]]*"ISSUER"' <<<"$response" || {
  echo "[FAIL] Bootstrap KEK switch rejeté" >&2
  exit 1
}
kcv="$(sed -n 's/.*"kcv"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$response")"
[[ -n "$kcv" ]] || {
  echo "[FAIL] KCV switch absent" >&2
  exit 1
}

echo "[OK] Paramétrage KEK switch terminé — KCV=$kcv"
