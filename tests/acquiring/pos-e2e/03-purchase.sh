#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

for name in ISSUING_E2E_PAN ISSUING_E2E_EXPIRY; do require_var "$name"; done
wait_http "$SIMULATOR_URL/api/simulator/v1/health" pos-simulator
amount_minor="${POS_E2E_AMOUNT_MINOR:-1000}"
[[ "$amount_minor" =~ ^[0-9]+$ ]] || fail "POS_E2E_AMOUNT_MINOR invalide"
amount="$(printf '%012d' "$amount_minor")"
response="$RUNTIME/pos-purchase-response.json"

python - "$ISSUING_E2E_PAN" "$ISSUING_E2E_EXPIRY" "$amount" \
  "${WAY_POS_TERMINAL_ID:-TERM0001}" "${WAY_POS_MERCHANT_ID:-MERCHANT0000001}" <<'PY' |
import json,sys
print(json.dumps({"mti":"0800","processingCode":"930000",
  "terminalId":sys.argv[4],"merchantId":sys.argv[5],"macEnabled":False}))
PY
curl -fsS -X POST "$SIMULATOR_URL/api/simulator/v1/transactions" \
  -H 'Content-Type: application/json' --data-binary @- >/dev/null

python - "$ISSUING_E2E_PAN" "$ISSUING_E2E_EXPIRY" "$amount" \
  "${WAY_POS_TERMINAL_ID:-TERM0001}" "${WAY_POS_MERCHANT_ID:-MERCHANT0000001}" <<'PY' |
import json,sys
print(json.dumps({"mti":"0200","processingCode":"000000","pan":sys.argv[1],
  "expiry":sys.argv[2],"amount":sys.argv[3],"entryMode":"010","conditionCode":"00",
  "terminalId":sys.argv[4],"merchantId":sys.argv[5],"macEnabled":False}))
PY
curl -fsS -X POST "$SIMULATOR_URL/api/simulator/v1/transactions" \
  -H 'Content-Type: application/json' --data-binary @- >"$response"

python - "$response" <<'PY'
import json,sys
with open(sys.argv[1], encoding="utf-8") as stream: result=json.load(stream)
if result.get("responseCode") != "00" or result.get("approved") is not True:
    raise SystemExit("[POS E2E] ERREUR - achat refuse: RC="+str(result.get("responseCode")))
print("[POS E2E] ACHAT APPROUVE - RC=00, auth="+str(result.get("authorizationCode")))
PY
