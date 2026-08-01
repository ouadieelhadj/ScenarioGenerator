#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

for name in ISSUING_E2E_PAN ISSUING_E2E_EXPIRY ISSUING_E2E_CURRENCY \
  ISSUING_E2E_BALANCE_MINOR; do require_var "$name"; done
wait_http "$ISSUING_URL/api/issuing/v1/health" card-issuing
wait_http "$SERVER_URL/api/routing/v1/health" ServerPOS

ready="$(psql_value "SELECT count(*) FROM issuing_payment_identifier WHERE issuer_id='BANK1' AND status='ACTIVE';")"
if [[ "$ready" == "0" ]]; then
  bash "$ROOT/tests/issuing/provision-connected-e2e.sh"
else
  printf '[POS E2E] Issuing BANK1 deja provisionne.\n'
fi

terminal_id="${WAY_POS_TERMINAL_ID:-TERM0001}"
merchant_id="${WAY_POS_MERCHANT_ID:-MERCHANT0000001}"
terminal_json="$(printf '{"terminalId":"%s","merchantId":"%s","extendedSet":true,"macData":"BIN","macRequired":false,"initialBatchId":"000000"}' "$terminal_id" "$merchant_id")"
code="$(curl -sS -o "$RUNTIME/terminal.json" -w '%{http_code}' -X POST \
  "$SERVER_URL/api/admin/waypos/v1/terminals" -H 'Content-Type: application/json' \
  --data-binary "$terminal_json")"
[[ "$code" == "201" || "$code" == "409" ]] || fail "provisionnement terminal HTTP $code"

bin="${ISSUING_E2E_PAN:0:6}"
if ! curl -fsS "$SERVER_URL/api/admin/waypos/v1/bin-routes" |
    python -c 'import json,sys; b=sys.argv[1]; raise SystemExit(0 if any(x.get("active") and x.get("binFrom")==b and x.get("interfaceCode")=="00000" for x in json.load(sys.stdin)) else 1)' "$bin"; then
  curl -fsS -X POST "$SERVER_URL/api/admin/waypos/v1/bin-routes" \
    -H 'Content-Type: application/json' \
    --data-binary "$(printf '{"binFrom":"%s","binTo":"%s","interfaceCode":"00000","priority":100}' "$bin" "$bin")" \
    >/dev/null
fi
printf '[POS E2E] Terminal et route locale prets.\n'
