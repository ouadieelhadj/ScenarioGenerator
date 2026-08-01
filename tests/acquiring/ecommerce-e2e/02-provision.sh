#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh" "${1:-}"

for name in ISSUING_E2E_CURRENCY ISSUING_E2E_BALANCE_MINOR; do require_var "$name"; done
wait_http "$ISSUING_URL/api/issuing/v1/health" card-issuing
wait_http "$ACQUIRING_URL/api/acquiring/v1/health" acquiring

issuer_id="${ECOMMERCE_LOCAL_ISSUER_ID:-002202}"
case "$ROUTE" in
  LOCAL_ISSUING)
    require_var ISSUING_E2E_PAN; require_var ISSUING_E2E_EXPIRY
    payment_pan="$ISSUING_E2E_PAN"; payment_expiry="$ISSUING_E2E_EXPIRY"
    issuer_ready="$(psql_value "SELECT count(*) FROM issuing_payment_identifier WHERE issuer_id='$issuer_id' AND status='ACTIVE';")"
    if [[ "$issuer_ready" == "0" ]]; then
      bash "$ROOT/tests/issuing/provision-connected-e2e.sh"
    else
      psql_value "UPDATE issuing_card_product SET ecommerce_enabled=TRUE, updated_at=now() WHERE issuer_id='$issuer_id' AND status='ACTIVE' RETURNING id;" >/dev/null
    fi
    interface_code=00000
    ;;
  DMAS_MASTERCARD)
    for name in DMAS_E2E_PAN DMAS_E2E_EXPIRY; do require_var "$name"; done
    payment_pan="$DMAS_E2E_PAN"; payment_expiry="$DMAS_E2E_EXPIRY"
    interface_code=DMAS_MEMBER
    psql_value "INSERT INTO mc_dmas_cards(pan,pin,balance,currency,expiry,status,bank_code,created_at,updated_at) VALUES ('$payment_pan','0000',$ISSUING_E2E_BALANCE_MINOR,'$ISSUING_E2E_CURRENCY','$payment_expiry','ACTIVE','022905',now(),now()) ON CONFLICT (pan) DO UPDATE SET balance=EXCLUDED.balance,currency=EXCLUDED.currency,expiry=EXCLUDED.expiry,status='ACTIVE',updated_at=now() RETURNING id;" >/dev/null
    ;;
  SWAM)
    for name in SWAM_E2E_PAN SWAM_E2E_EXPIRY; do require_var "$name"; done
    payment_pan="$SWAM_E2E_PAN"; payment_expiry="$SWAM_E2E_EXPIRY"
    interface_code=SWAM_MEMBER
    psql_value "INSERT INTO issuer_swam_cards(pan,pin,balance,currency,expiry,status,created_at,updated_at) VALUES ('$payment_pan','0000',$ISSUING_E2E_BALANCE_MINOR,'$ISSUING_E2E_CURRENCY','$payment_expiry','ACTIVE',now(),now()) ON CONFLICT (pan) DO UPDATE SET balance=EXCLUDED.balance,currency=EXCLUDED.currency,expiry=EXCLUDED.expiry,status='ACTIVE',updated_at=now() RETURNING id;" >/dev/null
    ;;
esac
payment_bin="${payment_pan:0:6}"
psql_value "DELETE FROM pos_bin_routes WHERE bin_from='$payment_bin' AND bin_to='$payment_bin'; INSERT INTO pos_bin_routes(bin_from,bin_to,interface_code,priority,active) VALUES ('$payment_bin','$payment_bin','$interface_code',1000,TRUE) RETURNING id;" >/dev/null
RESPONSE="$RUNTIME/provision-response.json"
post_json() {
  local path="$1" body="$2"
  shift 2
  local code
  code="$(curl -sS -o "$RESPONSE" -w '%{http_code}' -X POST "$ACQUIRING_URL$path" \
    -H 'Content-Type: application/json' --data-binary "$body" "$@")"
  [[ "$code" =~ ^2 ]] || fail "POST $path retourne HTTP $code"
}
json_field() {
  python - "$RESPONSE" "$1" <<'PY'
import json,sys
with open(sys.argv[1], encoding="utf-8") as stream: value=json.load(stream)
for part in sys.argv[2].split('.'): value=value[part]
print(value)
PY
}

acquirer=ACQECOM
product_row="$(psql_value "SELECT id::text||'|'||status FROM acceptance_product_version WHERE acquirer_id='$acquirer' AND product_code='ECOM-E2E' AND product_version=1 LIMIT 1;")"
if [[ -z "$product_row" ]]; then
  post_json /api/admin/acquiring/v1/products \
    "{\"acquirerId\":\"$acquirer\",\"productCode\":\"ECOM-E2E\",\"productVersion\":1,\"channel\":\"ECOMMERCE\",\"currency\":\"$ISSUING_E2E_CURRENCY\"}" \
    -H 'X-Caller-ID: ECOM_MAKER' -H 'X-Correlation-ID: ecom-product-create'
  product_id="$(json_field id)"; product_status="$(json_field status)"
else IFS='|' read -r product_id product_status <<<"$product_row"; fi
if [[ "$product_status" == "DRAFT" ]]; then
  post_json "/api/admin/acquiring/v1/products/$product_id/submit" ''
  product_status="$(json_field status)"
fi
if [[ "$product_status" == "PENDING_APPROVAL" ]]; then
  post_json "/api/admin/acquiring/v1/products/$product_id/approve" '' \
    -H 'X-Caller-ID: ECOM_CHECKER' -H 'X-Correlation-ID: ecom-product-approve'
fi

merchant_row="$(psql_value "SELECT id::text||'|'||status FROM merchant WHERE acquirer_id='$acquirer' AND registration_number='ECOM-REG-1' LIMIT 1;")"
if [[ -z "$merchant_row" ]]; then
  post_json /api/admin/acquiring/v1/merchants \
    "{\"acquirerId\":\"$acquirer\",\"legalName\":\"Ecommerce Test Merchant\",\"tradingName\":\"Ecom Test\",\"registrationNumber\":\"ECOM-REG-1\",\"country\":\"MA\",\"mcc\":\"5411\"}" \
    -H 'X-Caller-ID: ECOM_MAKER' -H 'Idempotency-Key: ecom-merchant-v1' \
    -H 'X-Correlation-ID: ecom-merchant-create'
  merchant_id="$(json_field id)"; merchant_status="$(json_field status)"
else IFS='|' read -r merchant_id merchant_status <<<"$merchant_row"; fi
if [[ "$merchant_status" == "DRAFT" ]]; then
  post_json "/api/admin/acquiring/v1/merchants/$merchant_id/submit" ''
  merchant_status="$(json_field status)"
fi
if [[ "$merchant_status" == "PENDING_APPROVAL" ]]; then
  post_json "/api/admin/acquiring/v1/merchants/$merchant_id/approve" '' \
    -H 'X-Caller-ID: ECOM_CHECKER' -H 'X-Correlation-ID: ecom-merchant-approve'
fi

contract_row="$(psql_value "SELECT id::text||'|'||status FROM payment_contract WHERE institution_id='$acquirer' AND external_reference='ECOM-CONTRACT' LIMIT 1;")"
if [[ -z "$contract_row" ]]; then
  post_json /api/admin/acquiring/v1/contracts/merchant \
    "{\"acquirerId\":\"$acquirer\",\"externalReference\":\"ECOM-CONTRACT\",\"merchantId\":\"$merchant_id\",\"settlementAccountReference\":\"ECOM-SETTLEMENT-1\",\"productId\":\"$product_id\",\"mid\":\"ECOMMID00000001\",\"mcc\":\"5411\",\"settlementCurrency\":\"$ISSUING_E2E_CURRENCY\",\"channel\":\"ECOMMERCE\"}" \
    -H 'X-Caller-ID: ECOM_MAKER' -H 'Idempotency-Key: ecom-contract-v1' \
    -H 'X-Correlation-ID: ecom-contract-create'
  contract_id="$(json_field id)"; contract_status="$(json_field status)"
else IFS='|' read -r contract_id contract_status <<<"$contract_row"; fi
if [[ "$contract_status" == "DRAFT" ]]; then
  post_json "/api/admin/acquiring/v1/contracts/$contract_id/submit?acquirerId=$acquirer" '' \
    -H 'X-Correlation-ID: ecom-contract-submit'
  contract_status="$(json_field status)"
fi
if [[ "$contract_status" == "PENDING_APPROVAL" ]]; then
  post_json "/api/admin/acquiring/v1/contracts/$contract_id/approve?acquirerId=$acquirer" '' \
    -H 'X-Caller-ID: ECOM_CHECKER' -H 'X-Correlation-ID: ecom-contract-approve'
fi

store_row="$(psql_value "SELECT id::text||'|'||status FROM ecommerce_store WHERE merchant_id='$merchant_id' AND store_code='ECOM-STORE-1' LIMIT 1;")"
if [[ -z "$store_row" ]]; then
  post_json /api/admin/acquiring/v1/ecommerce/stores \
    "{\"merchantId\":\"$merchant_id\",\"storeCode\":\"ECOM-STORE-1\",\"name\":\"Ecommerce Test Store\",\"allowedDomain\":\"shop.example.test\",\"returnUrl\":\"https://shop.example.test/return\",\"notificationUrl\":\"https://shop.example.test/notify\"}" \
    -H 'X-Correlation-ID: ecom-store-create'
  store_id="$(json_field id)"; store_status="$(json_field status)"
else IFS='|' read -r store_id store_status <<<"$store_row"; fi
if [[ "$store_status" == "DRAFT" ]]; then
  post_json "/api/admin/acquiring/v1/ecommerce/stores/$store_id/ready" ''
  store_status="$(json_field status)"
fi
if [[ "$store_status" == "READY" ]]; then
  post_json "/api/admin/acquiring/v1/ecommerce/stores/$store_id/activate" '' \
    -H 'X-Correlation-ID: ecom-store-activate'
fi

profile_id="$(psql_value "SELECT id::text FROM ecommerce_acceptance_profile WHERE acquirer_id='$acquirer' AND logical_terminal_id='ECOM0001' AND active LIMIT 1;")"
if [[ -z "$profile_id" ]]; then
  post_json /api/admin/acquiring/v1/ecommerce/profiles \
    "{\"acquirerId\":\"$acquirer\",\"storeId\":\"$store_id\",\"contractId\":\"$contract_id\",\"logicalTerminalId\":\"ECOM0001\",\"currency\":\"$ISSUING_E2E_CURRENCY\",\"captureMode\":\"IMMEDIATE\"}" \
    -H 'X-Correlation-ID: ecom-profile-create'
  profile_id="$(json_field id)"
fi
printf '%s\n' "$profile_id" >"$RUNTIME/profile-id"
printf '[ECOM E2E] Profil %s et route BIN %s prets. 3DS=NOT_PERFORMED.\n' "$profile_id" "$ROUTE"
