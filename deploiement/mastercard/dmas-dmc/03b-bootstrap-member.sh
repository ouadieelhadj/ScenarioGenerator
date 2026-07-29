#!/usr/bin/env bash
set -euo pipefail
set +H
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

dmas_require_secret DMAS_KEK_CLEAR "KEK/ZMK claire de test DMAS"
dmas_require_secret DMAS_MDK_CLEAR "MDK claire de test DMAS"
token="$(dmas_login "$DMAS_MEMBER_URL")"
[[ -n "$token" ]] || { echo "[FAIL] Authentification DMAS membre" >&2; exit 1; }

kek_response="$(dmas_auth_post "$DMAS_MEMBER_URL" \
  "/api/admin/dmas/kek/bootstrap" "$token" \
  -H 'Content-Type: application/json' \
  -d "{\"memberGroupId\":\"$DMAS_MEMBER_GROUP_ID\",\"kekClear\":\"$DMAS_KEK_CLEAR\"}")"
grep -q '"kcv"' <<<"$kek_response" || {
  echo "[FAIL] Bootstrap KEK membre rejete : $kek_response" >&2
  exit 1
}

mdk_response="$(dmas_auth_post "$DMAS_MEMBER_URL" \
  "/api/admin/dmas/mdk/bootstrap" "$token" \
  -H 'Content-Type: application/json' \
  -d "{\"mdkClear\":\"$DMAS_MDK_CLEAR\",\"bank\":\"$DMAS_MEMBER_BANK_CODE\"}")"
grep -q '"kcv"' <<<"$mdk_response" || {
  echo "[FAIL] Bootstrap MDK membre rejete : $mdk_response" >&2
  exit 1
}

echo "[OK] KEK et MDK formees unitairement cote membre"
