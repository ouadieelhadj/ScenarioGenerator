#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

MEMBER_PAN="${SWAM_MEMBER_CARD_PAN:-5321962145453348}"
SWITCH_PAN="${SWAM_SWITCH_CARD_PAN:-5321962145453348}"
COUNT="${SWAM_PURCHASE_COUNT:-5}"

purchase_many() {
  local base="$1" pan="$2" label="$3"
  for i in $(seq 1 "$COUNT"); do
    amount="$(printf '%012d' $((1000+i)))"
    response="$(swam_post "$base/financial?pan=$pan&amount=$amount")"
    grep -q '"approved":true' <<<"$response" || {
      echo "[FAIL] $label achat $i : $response" >&2
      exit 1
    }
    echo "[OK] $label achat $i"
  done
}

swam_wait "$SWAM_MEMBER_URL/api/admin/swam/health" "SWAM Membre"
purchase_many "$SWAM_MEMBER_URL/api/admin/swam" "$SWITCH_PAN" "membre vers issuer"

connection="$(curl -fsS "$SWAM_ISSUER_URL/api/admin/swam/connection")"
grep -q '"mode":"SINGLE_PERMANENT_BIDIRECTIONAL"' <<<"$connection" || {
  echo "[FAIL] Liaison SID non bidirectionnelle : $connection" >&2
  exit 1
}
purchase_many "$SWAM_ISSUER_URL/api/admin/swam" "$MEMBER_PAN" "issuer vers membre"
echo "[OK] $COUNT achats valides dans chaque sens"
