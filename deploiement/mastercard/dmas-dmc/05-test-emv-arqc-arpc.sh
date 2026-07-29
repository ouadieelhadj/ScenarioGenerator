#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

dmas_require_secret DB_PASSWORD "Mot de passe PostgreSQL"
pan="${DMAS_TEST_PAN:-}"
if [[ -z "$pan" ]]; then
  pan="$(dmas_psql --tuples-only --no-align --command="
SELECT pan FROM mc_dmas_cards
 WHERE active=true AND bank_code='$DMAS_MEMBER_BANK_CODE'
 ORDER BY id LIMIT 1;" | tr -d '[:space:]')"
fi
[[ "$pan" =~ ^[0-9]{12,19}$ ]] || {
  echo "[FAIL] Carte EMV de test introuvable" >&2
  exit 1
}

token="$(dmas_login "$DMAS_MEMBER_URL")"
start="$(dmas_auth_post "$DMAS_MEMBER_URL" "/api/admin/dmas/loadtest" "$token" \
  -H 'Content-Type: application/json' \
  -d "{\"pan\":\"$pan\",\"amount\":\"${DMAS_TEST_AMOUNT:-000000000100}\",\"count\":1,\"concurrency\":1,\"timeoutSeconds\":15,\"withEmv\":true,\"mti\":\"0100\"}")"
load_id="$(sed -n 's/.*"loadTestId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$start")"
[[ -n "$load_id" ]] || { echo "[FAIL] Test EMV non lance : $start" >&2; exit 1; }

status=""
for _ in $(seq 1 30); do
  status="$(curl -fsS "$DMAS_MEMBER_URL/api/admin/dmas/loadtest/$load_id/status?details=true" \
    -H "Authorization: Bearer $token")"
  grep -q '"status":"COMPLETED"' <<<"${status// /}" && break
  sleep 1
done
grep -Eq '"approved"[[:space:]]*:[[:space:]]*1' <<<"$status" || {
  echo "[FAIL] Autorisation EMV non approuvee : $status" >&2
  exit 1
}
grep -Eq '"errors"[[:space:]]*:[[:space:]]*0' <<<"$status" || {
  echo "[FAIL] Erreur pendant le test EMV : $status" >&2
  exit 1
}

mastercard_log="$DMAS_DMC_LOG_DIR/sg-mc-dmas-mastercard.log"
for _ in $(seq 1 10); do
  if grep -q 'ARQC VALIDE' "$mastercard_log" 2>/dev/null &&
     grep -q 'DE55 reponse tag 91' "$mastercard_log" 2>/dev/null; then
    echo "[OK] ARQC recalcule et valide cote Mastercard"
    echo "[OK] ARPC retourne dans le tag 91 du DE55"
    exit 0
  fi
  sleep 1
done
echo "[FAIL] Traces normatives ARQC/ARPC absentes : $mastercard_log" >&2
exit 1
