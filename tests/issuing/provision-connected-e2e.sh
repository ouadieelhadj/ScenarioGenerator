#!/usr/bin/env bash

set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ISSUING_E2E_CONFIG_FILE="${ISSUING_E2E_CONFIG_FILE:-$ROOT/runtime/issuing-connected-e2e/connected-e2e.env}"
if [[ -f "$ISSUING_E2E_CONFIG_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ISSUING_E2E_CONFIG_FILE"
  set +a
fi
ISSUING_URL="${ISSUING_URL:-http://127.0.0.1:8540}"

fail() {
  echo "[Issuing provision] FAIL - $*" >&2
  exit 1
}

for name in ISSUING_E2E_PAN ISSUING_E2E_EXPIRY \
  ISSUING_E2E_CURRENCY ISSUING_E2E_BALANCE_MINOR; do
  [[ -n "${!name-}" ]] || fail "$name est obligatoire"
done
[[ "$ISSUING_E2E_PAN" =~ ^[0-9]{12,19}$ ]] || fail "format PAN invalide"
[[ "$ISSUING_E2E_EXPIRY" =~ ^[0-9]{4}$ ]] || fail "format expiration invalide"
[[ "$ISSUING_E2E_CURRENCY" =~ ^[0-9]{3}$ ]] || fail "devise invalide"
[[ "$ISSUING_E2E_BALANCE_MINOR" =~ ^[0-9]+$ ]] || fail "solde invalide"
command -v curl >/dev/null 2>&1 || fail "curl est obligatoire"
command -v python >/dev/null 2>&1 || fail "Python est obligatoire"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/issuing-provision.XXXXXX")"
RESPONSE="$TMP_DIR/response.json"
cleanup() {
  rm -f -- "$RESPONSE"
  rmdir -- "$TMP_DIR" 2>/dev/null || true
}
trap cleanup EXIT

json_field() {
  local field="$1"
  python - "$RESPONSE" "$field" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    value = json.load(source)
for part in sys.argv[2].split('.'):
    value = value[part]
print(value)
PY
}

request() {
  local method="$1" path="$2" body="$3"
  shift 3
  local code
  local -a args=(
    -sS --connect-timeout 2 --max-time 15
    -o "$RESPONSE" -w '%{http_code}' -X "$method"
    "$ISSUING_URL$path"
  )
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' --data-binary "$body")
  fi
  args+=("$@")
  code="$(curl "${args[@]}")"
  [[ "$code" =~ ^2 ]] || fail "$method $path retourne HTTP $code"
}

headers_create() {
  local issuer="$1" kind="$2"
  printf '%s\n' \
    -H "X-Caller-ID: E2E_MAKER" \
    -H "Idempotency-Key: e2e-$issuer-$kind-v1" \
    -H "X-Correlation-ID: e2e-$issuer-$kind-correlation"
}

approve_product() {
  local issuer="$1" id="$2"
  request POST "/api/admin/issuing/v1/products/$id/approve" "" \
    -H "X-Issuer-ID: $issuer" -H 'X-Caller-ID: E2E_CHECKER' \
    -H "X-Correlation-ID: e2e-$issuer-product-approve"
}

activate_product() {
  local issuer="$1" id="$2"
  request POST "/api/admin/issuing/v1/products/$id/activate" "" \
    -H "X-Issuer-ID: $issuer" \
    -H "X-Correlation-ID: e2e-$issuer-product-activate"
}

provision_interface() {
  local issuer="$1" type="$2" base_path="$3"
  local key="${type,,}" id status body
  body="$(printf '{"issuerId":"%s","interfaceType":"%s","interfaceVersion":1,"direction":"INBOUND","protocol":"REST","host":"127.0.0.1","port":8540,"basePath":"%s","connectTimeoutMs":2000,"readTimeoutMs":5000,"tlsProfile":null,"secretReference":null,"parameters":{}}' \
    "$issuer" "$type" "$base_path")"
  mapfile -t create_headers < <(headers_create "$issuer" "interface-$key")
  request POST /api/admin/issuing/v1/interfaces "$body" "${create_headers[@]}"
  id="$(json_field id)"
  status="$(json_field status)"
  if [[ "$status" == "DRAFT" ]]; then
    request POST "/api/admin/issuing/v1/interfaces/$id/approve" "" \
      -H "X-Issuer-ID: $issuer" -H 'X-Caller-ID: E2E_CHECKER' \
      -H "X-Correlation-ID: e2e-$issuer-$key-approve"
    status="$(json_field status)"
  fi
  if [[ "$status" == "APPROVED" ]]; then
    request POST "/api/admin/issuing/v1/interfaces/$id/activate" "" \
      -H "X-Issuer-ID: $issuer" \
      -H "X-Correlation-ID: e2e-$issuer-$key-activate"
    status="$(json_field status)"
  fi
  [[ "$status" == "ACTIVE" ]] || fail "interface $issuer/$type non active"
}

provision_issuer() {
  local issuer="$1" channel="$2"
  local product_id product_status contract_id contract_status card_id body
  local funding_id="E2E-FUNDING-$issuer"

  body="$(printf '{"issuerId":"%s","productCode":"CONNECTED-E2E","productVersion":1,"cardType":"DEBIT","currency":"%s","purchaseEnabled":true,"cashEnabled":true,"ecommerceEnabled":true}' \
    "$issuer" "$ISSUING_E2E_CURRENCY")"
  mapfile -t product_headers < <(headers_create "$issuer" product)
  request POST /api/admin/issuing/v1/products "$body" "${product_headers[@]}"
  product_id="$(json_field id)"
  product_status="$(json_field status)"
  if [[ "$product_status" == "DRAFT" ]]; then
    approve_product "$issuer" "$product_id"
    product_status="$(json_field status)"
  fi
  if [[ "$product_status" == "APPROVED" ]]; then
    activate_product "$issuer" "$product_id"
    product_status="$(json_field status)"
  fi
  [[ "$product_status" == "ACTIVE" ]] || fail "produit $issuer non actif"

  body="$(printf '{"issuerId":"%s","externalReference":"CONNECTED-E2E","customerId":"E2E-CUSTOMER","cardholderId":"E2E-HOLDER","fundingContractId":"%s","productId":"%s"}' \
    "$issuer" "$funding_id" "$product_id")"
  mapfile -t contract_headers < <(headers_create "$issuer" contract)
  request POST /api/admin/issuing/v1/contracts "$body" "${contract_headers[@]}"
  contract_id="$(json_field id)"
  contract_status="$(json_field status)"
  if [[ "$contract_status" == "DRAFT" ]]; then
    request POST "/api/admin/issuing/v1/contracts/$contract_id/submit" "" \
      -H "X-Issuer-ID: $issuer" \
      -H "X-Correlation-ID: e2e-$issuer-contract-submit"
    contract_status="$(json_field status)"
  fi
  if [[ "$contract_status" == "PENDING_APPROVAL" ]]; then
    request POST "/api/admin/issuing/v1/contracts/$contract_id/approve" "" \
      -H "X-Issuer-ID: $issuer" -H 'X-Caller-ID: E2E_CHECKER' \
      -H "X-Correlation-ID: e2e-$issuer-contract-approve"
    contract_status="$(json_field status)"
  fi
  [[ "$contract_status" == "ACTIVE" ]] || fail "contrat $issuer non actif"

  body="$(printf '{"pan":"%s","expiryYymm":"%s"}' \
    "$ISSUING_E2E_PAN" "$ISSUING_E2E_EXPIRY")"
  request POST "/api/admin/issuing/v1/contracts/$contract_id/cards" "$body" \
    -H "X-Issuer-ID: $issuer" -H 'X-Caller-ID: E2E_MAKER' \
    -H "Idempotency-Key: e2e-$issuer-card-v1" \
    -H "X-Correlation-ID: e2e-$issuer-card-correlation"
  card_id="$(json_field id)"
  if [[ "$(json_field status)" == "INACTIVE" ]]; then
    request POST "/api/admin/issuing/v1/cards/$card_id/activate" "" \
      -H "X-Issuer-ID: $issuer" \
      -H "X-Correlation-ID: e2e-$issuer-card-activate"
  fi
  [[ "$(json_field status)" == "ACTIVE" ]] || fail "carte $issuer non active"

  body="$(printf '{"issuerId":"%s","currency":"%s","availableBalanceMinor":%s,"status":"ACTIVE"}' \
    "$issuer" "$ISSUING_E2E_CURRENCY" "$ISSUING_E2E_BALANCE_MINOR")"
  request PUT "/api/sandbox/core-banking/v1/accounts?fundingContractId=$funding_id" "$body"

  provision_interface "$issuer" "$channel" /api/issuing/v1
  provision_interface "$issuer" CORE_BANKING /api/sandbox/core-banking/v1
  echo "[Issuing provision] OK - $issuer / $channel"
}

curl -fsS --connect-timeout 2 --max-time 5 \
  "$ISSUING_URL/api/issuing/v1/health" >/dev/null \
  || fail "service Issuing indisponible"

provision_issuer BANK1 SERVER_POS
provision_issuer 300853 SWAM
provision_issuer 002202 DMAS

echo "[Issuing provision] SUCCESS - donnees et endpoints multi-canal actifs"
