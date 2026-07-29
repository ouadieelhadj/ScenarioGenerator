#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

dmas_require_secret DB_PASSWORD "Mot de passe PostgreSQL"
token="$(dmas_login "$DMAS_MEMBER_URL")"
[[ -n "$token" ]] || {
  echo "[FAIL] Authentification DMAS membre" >&2
  exit 1
}

original="$(dmas_psql --tuples-only --no-align --field-separator='|' --command="
SELECT pan, lpad(de004_amount::text, 12, '0'), de011_stan,
       de007_transmission_datetime
  FROM mc_dmas_member_transactions
 WHERE approved=true
   AND clearing_eligible=true
   AND reversed=false
 ORDER BY id DESC
 LIMIT 1;" | tr -d '\r')"

IFS='|' read -r pan amount original_stan original_dt <<<"$original"
[[ "$pan" =~ ^[0-9]{12,19}$ &&
   "$amount" =~ ^[0-9]{12}$ &&
   "$original_stan" =~ ^[0-9]{6}$ &&
   "$original_dt" =~ ^[0-9]{10}$ ]] || {
  echo "[FAIL] Autorisation originale introuvable pour le reversal : $original" >&2
  exit 1
}

reversal="$(dmas_auth_post "$DMAS_MEMBER_URL" \
  "/api/admin/dmas/reversal" "$token" \
  -H 'Content-Type: application/json' \
  -d "{\"pan\":\"$pan\",\"amount\":\"$amount\",\"processingCode\":\"000000\",\"originalStan\":\"$original_stan\",\"originalDt\":\"$original_dt\",\"advice\":false}")"
grep -Eq '"reversed"[[:space:]]*:[[:space:]]*true' <<<"$reversal" || {
  echo "[FAIL] Reversal 0400/0410 refuse : $reversal" >&2
  exit 1
}

states="$(dmas_psql --tuples-only --no-align --command="
SELECT
 (SELECT count(*) FROM mc_dmas_member_transactions
   WHERE pan='$pan' AND de011_stan='$original_stan'
     AND de007_transmission_datetime='$original_dt'
     AND reversed=true AND clearing_eligible=false),
 (SELECT count(*) FROM mc_dmas_issuer_transactions
   WHERE pan='$pan' AND de011_stan='$original_stan'
     AND de007_transmission_datetime='$original_dt'
     AND reversed=true AND clearing_eligible=false);" | tr -d '[:space:]')"
[[ "$states" == "1|1" ]] || {
  echo "[FAIL] Reversal non reporte dans les deux journaux : $states" >&2
  exit 1
}

reversal_again="$(dmas_auth_post "$DMAS_MEMBER_URL" \
  "/api/admin/dmas/reversal" "$token" \
  -H 'Content-Type: application/json' \
  -d "{\"pan\":\"$pan\",\"amount\":\"$amount\",\"processingCode\":\"000000\",\"originalStan\":\"$original_stan\",\"originalDt\":\"$original_dt\",\"advice\":false}")"
grep -Eq '"reversed"[[:space:]]*:[[:space:]]*true' <<<"$reversal_again" || {
  echo "[FAIL] Second reversal non idempotent : $reversal_again" >&2
  exit 1
}

advice_amount="${DMAS_ADVICE_TEST_AMOUNT:-000000000200}"
advice="$(dmas_auth_post "$DMAS_MEMBER_URL" \
  "/api/admin/dmas/advice" "$token" \
  -H 'Content-Type: application/json' \
  -d "{\"pan\":\"$pan\",\"amount\":\"$advice_amount\",\"processingCode\":\"000000\",\"terminalId\":\"TERM0001\",\"acceptorId\":\"MERCHANT0000001\"}")"
grep -Eq '"acknowledged"[[:space:]]*:[[:space:]]*true' <<<"$advice" || {
  echo "[FAIL] Advice 0120/0130 refuse : $advice" >&2
  exit 1
}
advice_stan="$(sed -n 's/.*"de011_stan"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$advice")"
[[ "$advice_stan" =~ ^[0-9]{6}$ ]] || {
  echo "[FAIL] STAN advice absent : $advice" >&2
  exit 1
}

advice_states="$(dmas_psql --tuples-only --no-align --command="
SELECT
 (SELECT count(*) FROM mc_dmas_member_transactions
   WHERE pan='$pan' AND de011_stan='$advice_stan'
     AND mti_request='0120' AND mti_response='0130'
     AND approved=true AND clearing_eligible=true),
 (SELECT count(*) FROM mc_dmas_issuer_transactions
   WHERE pan='$pan' AND de011_stan='$advice_stan'
     AND mti_request='0120' AND mti_response='0130'
     AND approved=true AND clearing_eligible=true);" | tr -d '[:space:]')"
[[ "$advice_states" == "1|1" ]] || {
  echo "[FAIL] Advice non journalise des deux cotes : $advice_states" >&2
  exit 1
}

echo "[OK] Reversal 0400/0410 reporte dans les deux journaux"
echo "[OK] Double reversal idempotent"
echo "[OK] Advice 0120/0130 eligible au clearing dans les deux journaux"
