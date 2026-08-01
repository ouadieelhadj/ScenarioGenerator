#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh" "${1:-}"

require_var ISSUING_E2E_CURRENCY
wait_http "$SIMULATOR_URL/api/merchant-site-simulator/v1/health" merchant-site-simulator
[[ -f "$RUNTIME/profile-id" ]] || fail "Profil absent; lancez 02-provision.sh"
profile_id="$(tr -d '[:space:]' <"$RUNTIME/profile-id")"
case "$ROUTE" in
  LOCAL_ISSUING)
    require_var ISSUING_E2E_PAN; require_var ISSUING_E2E_EXPIRY
    payment_pan="$ISSUING_E2E_PAN"; payment_expiry="$ISSUING_E2E_EXPIRY"
    ;;
  DMAS_MASTERCARD)
    require_var DMAS_E2E_PAN; require_var DMAS_E2E_EXPIRY
    payment_pan="$DMAS_E2E_PAN"; payment_expiry="$DMAS_E2E_EXPIRY"
    ;;
  SWAM)
    require_var SWAM_E2E_PAN; require_var SWAM_E2E_EXPIRY
    payment_pan="$SWAM_E2E_PAN"; payment_expiry="$SWAM_E2E_EXPIRY"
    ;;
esac
amount_minor="${ECOMMERCE_E2E_AMOUNT_MINOR:-1000}"
three_ds_flow="${THREE_DS_FLOW:-NOT_REQUESTED}"
site_type="${MERCHANT_SITE_TYPE:-NATIONAL}"
three_ds_program="${THREE_DS_PROGRAM:-MASTERCARD}"
issuer_mode="${THREE_DS_ISSUER_MODE:-MEMBER}"
challenge_data="${THREE_DS_CHALLENGE_DATA:-${THREE_DS_SANDBOX_CHALLENGE_OTP:-}}"
transaction_id="$(python -c 'import uuid; print(uuid.uuid4())')"
response="$RUNTIME/ecommerce-purchase-response.json"

python - "$transaction_id" "$profile_id" "$payment_pan" \
  "$payment_expiry" "$ISSUING_E2E_CURRENCY" "$amount_minor" "$ROUTE" \
  "$site_type" "$three_ds_program" "$three_ds_flow" "$issuer_mode" "$challenge_data" <<'PY' |
import json,sys
tx,profile,pan,expiry,currency,amount,route,site,program,flow,issuer,challenge=sys.argv[1:]
print(json.dumps({"transactionId":tx,"correlationId":"corr-"+tx,
  "idempotencyKey":"idem-"+tx,"acquirerId":"ACQECOM","profileId":profile,
  "merchantOrderId":"ORDER-"+tx[:12],"amountMinor":int(amount),
  "currency":currency,"pan":pan,"expiry":expiry,"networkRoute":route,
  "siteType":site,"threeDsProgram":None if flow=="NOT_REQUESTED" else program,
  "threeDsFlow":flow,"issuerMode":None if flow=="NOT_REQUESTED" else issuer,
  "challengeData":None if flow!="CHALLENGE" else challenge}))
PY
curl -fsS -X POST "$SIMULATOR_URL/api/merchant-site-simulator/v1/purchases" \
  -H 'Content-Type: application/json' --data-binary @- >"$response"

python - "$response" "$ROUTE" "$three_ds_flow" <<'PY'
import json,sys
with open(sys.argv[1], encoding="utf-8") as stream: result=json.load(stream)
if result.get("status") != "APPROVED" or result.get("responseCode") != "00":
    raise SystemExit("[ECOM E2E] ERREUR - achat refuse: "+str(result))
expected="NOT_PERFORMED" if sys.argv[3]=="NOT_REQUESTED" else "AUTHENTICATED"
if result.get("authenticationStatus") != expected:
    raise SystemExit("[ECOM E2E] ERREUR - statut 3DS inattendu")
if result.get("networkRoute") != sys.argv[2]:
    raise SystemExit("[ECOM E2E] ERREUR - route inattendue")
print("[ECOM E2E] ACHAT APPROUVE - route="+sys.argv[2]+", RC=00, 3DS="+expected+", auth="+str(result.get("authorizationCode")))
PY
