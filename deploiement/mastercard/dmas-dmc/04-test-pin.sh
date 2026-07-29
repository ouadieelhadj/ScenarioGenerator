#!/usr/bin/env bash
set -euo pipefail
set +H
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

dmas_require_secret DMAS_TEST_PIN "PIN de la carte de test"
dmas_require_secret DB_PASSWORD "Mot de passe PostgreSQL"
pan="${DMAS_TEST_PAN:-}"
if [[ -z "$pan" ]]; then
  pan="$(dmas_psql --tuples-only --no-align --command="
SELECT pan FROM mc_dmas_cards
 WHERE active=true AND bank_code='$DMAS_MEMBER_BANK_CODE'
 ORDER BY id LIMIT 1;" | tr -d '[:space:]')"
fi
[[ "$pan" =~ ^[0-9]{12,19}$ ]] || {
  echo "[FAIL] Aucune carte de test active trouvee; definir DMAS_TEST_PAN." >&2
  exit 1
}

token="$(dmas_login "$DMAS_MEMBER_URL")"
response="$(dmas_auth_post "$DMAS_MEMBER_URL" "/api/admin/dmas/auth" "$token" \
  -H 'Content-Type: application/json' \
  -d "{\"type\":\"purchase\",\"pan\":\"$pan\",\"amount\":\"${DMAS_TEST_AMOUNT:-000000000100}\",\"pin\":\"$DMAS_TEST_PIN\",\"terminalId\":\"TERM0001\",\"acceptorId\":\"MERCHANT0000001\",\"entryMode\":\"CARD_PRESENT\",\"transport\":\"jpos\"}")"

grep -Eq '"approved"[[:space:]]*:[[:space:]]*true' <<<"$response" || {
  echo "[FAIL] Achat PIN refuse : $response" >&2
  exit 1
}
grep -Eq '"pin_included"[[:space:]]*:[[:space:]]*true' <<<"$response" || {
  echo "[FAIL] Achat approuve sans DE52" >&2
  exit 1
}

leaked="$(dmas_psql --tuples-only --no-align --command="
SELECT count(*) FROM information_schema.columns
 WHERE table_schema='public'
   AND table_name IN ('mc_dmas_member_transactions','mc_dmas_issuer_transactions')
   AND column_name IN ('de052_pin','de52','pin_block');" | tr -d '[:space:]')"
[[ "$leaked" == "0" ]] || {
  echo "[FAIL] Une colonne PIN/DE52 existe dans les journaux clearing" >&2
  exit 1
}

echo "[OK] Achat PIN approuve via la liaison permanente"
echo "[OK] DE52 utilise en ligne et absent des journaux DMAS de clearing"
