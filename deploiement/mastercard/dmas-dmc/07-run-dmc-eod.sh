#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

business_date="${DMC_BUSINESS_DATE:-$(date +%F)}"
auth=(-u "$DMCS_BASIC_USER:$DMCS_BASIC_PASSWORD")

acq_first="$(curl -fsS -X POST \
  "$DMCS_ACQUIRER_URL/api/dmcs/eod?businessDate=$business_date" "${auth[@]}")"
iss_first="$(curl -fsS -X POST \
  "$DMCS_ISSUER_URL/api/dmcs/eod?businessDate=$business_date" "${auth[@]}")"
acq_second="$(curl -fsS -X POST \
  "$DMCS_ACQUIRER_URL/api/dmcs/eod?businessDate=$business_date" "${auth[@]}")"
iss_second="$(curl -fsS -X POST \
  "$DMCS_ISSUER_URL/api/dmcs/eod?businessDate=$business_date" "${auth[@]}")"

grep -Eq '"eligible"[[:space:]]*:[[:space:]]*[1-9][0-9]*' <<<"$acq_first" || {
  echo "[FAIL] Aucun journal DMAS membre eligible : $acq_first" >&2
  exit 1
}
grep -Eq '"eligible"[[:space:]]*:[[:space:]]*[1-9][0-9]*' <<<"$iss_first" || {
  echo "[FAIL] Aucun journal DMAS issuer eligible : $iss_first" >&2
  exit 1
}
grep -Eq '"created"[[:space:]]*:[[:space:]]*0' <<<"$acq_second" || {
  echo "[FAIL] EOD acquirer non idempotent : $acq_second" >&2
  exit 1
}
grep -Eq '"created"[[:space:]]*:[[:space:]]*0' <<<"$iss_second" || {
  echo "[FAIL] EOD issuer non idempotent : $iss_second" >&2
  exit 1
}

dmas_require_secret DB_PASSWORD "Mot de passe PostgreSQL"
ownership="$(dmas_psql --tuples-only --no-align --command="
SELECT
 (SELECT count(*) FROM dmcs_acquirer_clearing_transactions
   WHERE business_date='$business_date' AND source_type='LOCAL_AUTH'),
 (SELECT count(*) FROM dmcs_issuer_clearing_transactions
   WHERE business_date='$business_date' AND source_type='LOCAL_AUTH');" |
  tr -d '[:space:]')"
IFS='|' read -r acq_count iss_count <<<"$ownership"
[[ "${acq_count:-0}" -gt 0 && "${iss_count:-0}" -gt 0 ]] || {
  echo "[FAIL] Tables clearing non alimentees : $ownership" >&2
  exit 1
}

echo "[OK] EOD DMC acquirer : $acq_first"
echo "[OK] EOD DMC issuer   : $iss_first"
echo "[OK] EOD idempotents des deux cotes"
echo "[OK] Tables clearing proprietaires alimentees : acquirer=$acq_count issuer=$iss_count"
echo "[INFO] L'outgoing First Presentment reste bloque tant que la regle DE31/ARN n'est pas validee."
