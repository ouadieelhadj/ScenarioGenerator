#!/usr/bin/env bash

set -Eeuo pipefail

SERVER_BASE_URL="http://localhost:8530"
SIMULATOR_BASE_URL="http://localhost:8532"
ALLOW_EXISTING_PROVISIONING=false

usage() {
    cat <<'EOF'
Usage:
  ./tests/waypos/Invoke-WayPosE2E.sh [options]

Options:
  --server-base-url URL       WayPosServer REST URL (default: http://localhost:8530)
  --simulator-base-url URL    wayPosSimulator REST URL (default: http://localhost:8532)
  --allow-existing-provisioning
                              Accept HTTP 409 for an already verified terminal/card
  -h, --help                  Show this help
EOF
}

while (($# > 0)); do
    case "$1" in
        --server-base-url)
            (($# >= 2)) || { echo "Missing value for $1" >&2; exit 2; }
            SERVER_BASE_URL="$2"
            shift 2
            ;;
        --simulator-base-url)
            (($# >= 2)) || { echo "Missing value for $1" >&2; exit 2; }
            SIMULATOR_BASE_URL="$2"
            shift 2
            ;;
        --allow-existing-provisioning)
            ALLOW_EXISTING_PROVISIONING=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

fail() {
    echo "[WayPos E2E] ERROR - $*" >&2
    exit 1
}

find_python() {
    local candidate
    for candidate in python python3; do
        if command -v "$candidate" >/dev/null 2>&1 \
                && "$candidate" -c 'import json' >/dev/null 2>&1; then
            printf '%s' "$candidate"
            return 0
        fi
    done
    return 1
}

command -v curl >/dev/null 2>&1 \
    || fail "curl is required. Use Invoke-WayPosE2E.ps1 if curl is unavailable."
command -v timeout >/dev/null 2>&1 \
    || fail "timeout is required. Use Invoke-WayPosE2E.ps1 if it is unavailable."
PYTHON_BIN="$(find_python)" \
    || fail "Python 3 is required for safe JSON validation. Use Invoke-WayPosE2E.ps1 otherwise."

TMP_DIR=""
umask 077
temp_roots=("${TMPDIR-}" "${TMP-}" "${TEMP-}" "/tmp" "$PWD/tmp")
for temp_root in "${temp_roots[@]}"; do
    [[ -n "$temp_root" ]] || continue
    if [[ "$temp_root" =~ ^[A-Za-z]:[\\/].* ]] \
            && command -v cygpath >/dev/null 2>&1; then
        temp_root="$(cygpath -u "$temp_root")"
    fi
    if [[ "$temp_root" == "$PWD/tmp" && ! -d "$temp_root" ]]; then
        mkdir -p -- "$temp_root" 2>/dev/null || true
    fi
    [[ -d "$temp_root" ]] || continue
    for attempt in {1..10}; do
        candidate="$temp_root/waypos-e2e.$$.$RANDOM"
        if mkdir -- "$candidate" 2>/dev/null; then
            TMP_DIR="$candidate"
            break 2
        fi
    done
done
[[ -n "$TMP_DIR" ]] || fail "Unable to create a private temporary directory"
case "$TMP_DIR" in
    */waypos-e2e.*) ;;
    *)
        fail "Unexpected temporary directory: $TMP_DIR"
        ;;
esac
cleanup() {
    case "$TMP_DIR" in
        */waypos-e2e.*)
            rm -rf -- "$TMP_DIR"
            ;;
        *)
            echo "[WayPos E2E] Refusing to remove unexpected path" >&2
            ;;
    esac
}
trap cleanup EXIT

if [[ ! -d "$TMP_DIR" ]]; then
    fail "Private temporary directory disappeared before use"
fi

RESPONSE_FILE="$TMP_DIR/response.json"
RESULTS_FILE="$TMP_DIR/results.tsv"
: >"$RESULTS_FILE"

required_names=(
    WAY_POS_DB_PASSWORD
    WAY_POS_LMK_FILE
    WAY_POS_OUTBOX_KEY_HEX
    WAY_POS_PAN_PEPPER
    WAY_POS_TERMINAL_ID
    WAY_POS_MERCHANT_ID
    WAY_POS_CURRENCY
    WAY_POS_MAC_MODE
    WAY_POS_TAK_HEX
    WAY_POS_MASTER_KEY_ID
    WAY_POS_MASTER_KEY_TYPE
    WAY_POS_MASTER_KEY_HEX
    WAY_POS_E2E_TAK_UNDER_LMK
    WAY_POS_E2E_TAK_KCV
    WAY_POS_E2E_TAK_LENGTH
    WAY_POS_E2E_TPK_UNDER_LMK
    WAY_POS_E2E_TPK_KCV
    WAY_POS_E2E_TPK_LENGTH
    WAY_POS_E2E_PVK_A_UNDER_LMK
    WAY_POS_E2E_PVK_A_KCV
    WAY_POS_E2E_PVK_B_UNDER_LMK
    WAY_POS_E2E_PVK_B_KCV
    WAY_POS_E2E_PAN
    WAY_POS_E2E_EXPIRY
    WAY_POS_E2E_AMOUNT
    WAY_POS_E2E_AVAILABLE_BALANCE
    WAY_POS_E2E_PIN_BLOCK_HEX
    WAY_POS_E2E_PIN_PVV
    WAY_POS_E2E_PIN_PVKI
    WAY_POS_E2E_MDK_UNDER_LMK
    WAY_POS_E2E_MDK_KCV
    WAY_POS_E2E_MDK_LENGTH
    WAY_POS_E2E_PAN_SEQUENCE
    WAY_POS_E2E_ARPC_ARC_HEX
    WAY_POS_E2E_EMV_EOD_HEX
    WAY_POS_E2E_EMV_REPEAT_HEX
    WAY_POS_E2E_EMV_REVERSAL_HEX
    WAY_POS_E2E_EMV_ADVICE_HEX
    WAY_POS_E2E_NEXT_TAK_ID
    WAY_POS_E2E_NEXT_TAK_X917_BLOCK_HEX
    WAY_POS_E2E_NEXT_TAK_UNDER_LMK
    WAY_POS_E2E_NEXT_TAK_KCV
    WAY_POS_E2E_NEXT_TAK_LENGTH
)

echo "[WayPos E2E] Validating real-environment prerequisites..."

missing_names=()
for name in "${required_names[@]}"; do
    if [[ -z "${!name-}" ]]; then
        missing_names+=("$name")
    fi
done
if ((${#missing_names[@]} > 0)); then
    printf -v missing_csv '%s, ' "${missing_names[@]}"
    fail "Missing required environment variables: ${missing_csv%, }"
fi

assert_regex() {
    local name="$1"
    local value="${!name}"
    local pattern="$2"
    [[ "$value" =~ $pattern ]] \
        || fail "Invalid format for environment variable: $name"
}

assert_regex WAY_POS_TERMINAL_ID '^[A-Za-z0-9]{8}$'
assert_regex WAY_POS_MERCHANT_ID '^[A-Za-z0-9]{15}$'
assert_regex WAY_POS_CURRENCY '^[0-9]{3}$'
assert_regex WAY_POS_MAC_MODE '^(BIN|HEX)$'
assert_regex WAY_POS_OUTBOX_KEY_HEX '^[0-9A-Fa-f]{64}$'
assert_regex WAY_POS_TAK_HEX '^([0-9A-Fa-f]{16}|[0-9A-Fa-f]{32})$'
assert_regex WAY_POS_MASTER_KEY_ID '^[A-Za-z0-9_-]+$'
assert_regex WAY_POS_MASTER_KEY_TYPE '^[A-Za-z0-9_-]+$'
assert_regex WAY_POS_MASTER_KEY_HEX \
    '^([0-9A-Fa-f]{16}|[0-9A-Fa-f]{32}|[0-9A-Fa-f]{48})$'
assert_regex WAY_POS_E2E_PAN '^[0-9]{13,19}$'
assert_regex WAY_POS_E2E_EXPIRY '^[0-9]{4}$'
assert_regex WAY_POS_E2E_AMOUNT '^[0-9]{12}$'
assert_regex WAY_POS_E2E_AVAILABLE_BALANCE '^[0-9]+$'
assert_regex WAY_POS_E2E_PIN_BLOCK_HEX '^[0-9A-Fa-f]{16}$'
assert_regex WAY_POS_E2E_PIN_PVV '^[0-9]{4}$'
assert_regex WAY_POS_E2E_PIN_PVKI '^[0-9]$'
assert_regex WAY_POS_E2E_PAN_SEQUENCE '^[0-9]{2}$'
assert_regex WAY_POS_E2E_ARPC_ARC_HEX '^[0-9A-Fa-f]{4}$'
assert_regex WAY_POS_E2E_NEXT_TAK_ID '^[A-Za-z0-9_-]+$'

for name in \
    WAY_POS_E2E_TAK_UNDER_LMK WAY_POS_E2E_TPK_UNDER_LMK \
    WAY_POS_E2E_PVK_A_UNDER_LMK WAY_POS_E2E_PVK_B_UNDER_LMK \
    WAY_POS_E2E_MDK_UNDER_LMK WAY_POS_E2E_NEXT_TAK_UNDER_LMK \
    WAY_POS_E2E_NEXT_TAK_X917_BLOCK_HEX WAY_POS_E2E_EMV_EOD_HEX \
    WAY_POS_E2E_EMV_REPEAT_HEX WAY_POS_E2E_EMV_REVERSAL_HEX \
    WAY_POS_E2E_EMV_ADVICE_HEX; do
    assert_regex "$name" '^([0-9A-Fa-f]{2})+$'
done

for name in \
    WAY_POS_E2E_TAK_KCV WAY_POS_E2E_TPK_KCV \
    WAY_POS_E2E_PVK_A_KCV WAY_POS_E2E_PVK_B_KCV \
    WAY_POS_E2E_MDK_KCV WAY_POS_E2E_NEXT_TAK_KCV; do
    assert_regex "$name" '^[0-9A-Fa-f]{6}$'
done

for name in \
    WAY_POS_E2E_TAK_LENGTH WAY_POS_E2E_TPK_LENGTH \
    WAY_POS_E2E_NEXT_TAK_LENGTH; do
    assert_regex "$name" '^(8|16)$'
done
assert_regex WAY_POS_E2E_MDK_LENGTH '^(16|24)$'

lmk_path="$WAY_POS_LMK_FILE"
if [[ "$lmk_path" =~ ^[A-Za-z]:[\\/].* ]] \
        && command -v cygpath >/dev/null 2>&1; then
    lmk_path="$(cygpath -u "$lmk_path")"
fi
[[ -f "$lmk_path" ]] \
    || fail "WAY_POS_LMK_FILE does not reference an existing file"

test_tcp_port() {
    local host="$1"
    local port="$2"
    timeout 3 bash -c "exec 3<>/dev/tcp/${host}/${port}" \
        >/dev/null 2>&1
}

test_tcp_port localhost 5432 \
    || fail "PostgreSQL is not reachable on localhost:5432"

wait_health() {
    local uri="$1"
    local service="$2"
    if ! curl --silent --show-error --fail \
            --connect-timeout 3 --max-time 5 \
            --output "$RESPONSE_FILE" "$uri"; then
        fail "$service is not reachable at $uri"
    fi
    "$PYTHON_BIN" - "$RESPONSE_FILE" "$service" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    payload = json.load(stream)
if payload.get("status") != "UP":
    raise SystemExit(f"{sys.argv[2]} health is not UP")
PY
}

wait_health "$SERVER_BASE_URL/api/routing/v1/health" "WayPosServer"
wait_health "$SIMULATOR_BASE_URL/api/simulator/v1/health" "wayPosSimulator"

status_allowed() {
    local actual="$1"
    shift
    local expected
    for expected in "$@"; do
        [[ "$actual" == "$expected" ]] && return 0
    done
    return 1
}

post_json() {
    local uri="$1"
    local body="$2"
    shift 2
    local status
    : >"$RESPONSE_FILE"
    status="$(curl --silent --show-error \
        --connect-timeout 5 --max-time 65 \
        --output "$RESPONSE_FILE" --write-out '%{http_code}' \
        --request POST --header 'Content-Type: application/json' \
        --data-binary "$body" "$uri")" \
        || fail "HTTP POST failed for $uri. No secret was printed."
    status_allowed "$status" "$@" \
        || fail "Unexpected HTTP status $status from $uri. No secret was printed."
}

post_empty() {
    local uri="$1"
    local status
    : >"$RESPONSE_FILE"
    status="$(curl --silent --show-error \
        --connect-timeout 5 --max-time 65 \
        --output "$RESPONSE_FILE" --write-out '%{http_code}' \
        --request POST "$uri")" \
        || fail "HTTP POST failed for $uri. No secret was printed."
    status_allowed "$status" 200 201 202 \
        || fail "Unexpected HTTP status $status from $uri. No secret was printed."
}

provision_statuses=(200 201 202)
if $ALLOW_EXISTING_PROVISIONING; then
    provision_statuses+=(409)
fi

echo "[WayPos E2E] Provisioning terminal and HSM-protected keys..."

terminal_json="$(printf \
    '{"terminalId":"%s","merchantId":"%s","extendedSet":true,"macData":"%s","macRequired":true,"initialBatchId":"000001"}' \
    "$WAY_POS_TERMINAL_ID" "$WAY_POS_MERCHANT_ID" "$WAY_POS_MAC_MODE")"
post_json "$SERVER_BASE_URL/api/admin/waypos/v1/terminals" \
    "$terminal_json" "${provision_statuses[@]}"

for key_type in TAK TPK; do
    if [[ "$key_type" == "TAK" ]]; then
        under_lmk="$WAY_POS_E2E_TAK_UNDER_LMK"
        kcv="$WAY_POS_E2E_TAK_KCV"
        key_length="$WAY_POS_E2E_TAK_LENGTH"
    else
        under_lmk="$WAY_POS_E2E_TPK_UNDER_LMK"
        kcv="$WAY_POS_E2E_TPK_KCV"
        key_length="$WAY_POS_E2E_TPK_LENGTH"
    fi
    key_json="$(printf \
        '{"keyType":"%s","keyUnderLmk":"%s","kcv":"%s","keyLength":%s}' \
        "$key_type" "$under_lmk" "$kcv" "$key_length")"
    post_json \
        "$SERVER_BASE_URL/api/admin/waypos/v1/terminals/$WAY_POS_TERMINAL_ID/working-keys" \
        "$key_json" 200 201 202
done

for key_side in A B; do
    if [[ "$key_side" == "A" ]]; then
        key_code="LOCAL_PVK_A"
        under_lmk="$WAY_POS_E2E_PVK_A_UNDER_LMK"
        kcv="$WAY_POS_E2E_PVK_A_KCV"
    else
        key_code="LOCAL_PVK_B"
        under_lmk="$WAY_POS_E2E_PVK_B_UNDER_LMK"
        kcv="$WAY_POS_E2E_PVK_B_KCV"
    fi
    key_json="$(printf \
        '{"keyCode":"%s","keyType":"PVK","keyUnderLmk":"%s","kcv":"%s","keyLength":8}' \
        "$key_code" "$under_lmk" "$kcv")"
    post_json "$SERVER_BASE_URL/api/admin/waypos/v1/security-keys" \
        "$key_json" 200 201 202
done

card_json="$(printf \
    '{"pan":"%s","expiryYymm":"%s","currency":"%s","availableBalance":%s,"pinPvv":"%s","pinPvki":%s,"mdkUnderLmk":"%s","mdkKcv":"%s","mdkLength":%s,"panSequenceNumber":"%s","arpcArcHex":"%s"}' \
    "$WAY_POS_E2E_PAN" "$WAY_POS_E2E_EXPIRY" "$WAY_POS_CURRENCY" \
    "$WAY_POS_E2E_AVAILABLE_BALANCE" "$WAY_POS_E2E_PIN_PVV" \
    "$WAY_POS_E2E_PIN_PVKI" "$WAY_POS_E2E_MDK_UNDER_LMK" \
    "$WAY_POS_E2E_MDK_KCV" "$WAY_POS_E2E_MDK_LENGTH" \
    "$WAY_POS_E2E_PAN_SEQUENCE" "$WAY_POS_E2E_ARPC_ARC_HEX")"
post_json "$SERVER_BASE_URL/api/admin/waypos/v1/cards" \
    "$card_json" "${provision_statuses[@]}"

bin="${WAY_POS_E2E_PAN:0:6}"
route_json="$(printf \
    '{"binFrom":"%s","binTo":"%s","interfaceCode":"00000","priority":1}' \
    "$bin" "$bin")"
post_json "$SERVER_BASE_URL/api/admin/waypos/v1/bin-routes" \
    "$route_json" 200 201 202

next_tak_json="$(printf \
    '{"terminalId":"%s","keyType":"TAK","keyId":"%s","algorithm":"T","kcv":"%s","masterKeyId":"%s","masterKeyType":"%s","ansiX917BlockHex":"%s","keyUnderLmk":"%s","keyLength":%s,"actionCode":"0","replacementKeyId":null}' \
    "$WAY_POS_TERMINAL_ID" "$WAY_POS_E2E_NEXT_TAK_ID" \
    "$WAY_POS_E2E_NEXT_TAK_KCV" "$WAY_POS_MASTER_KEY_ID" \
    "$WAY_POS_MASTER_KEY_TYPE" "$WAY_POS_E2E_NEXT_TAK_X917_BLOCK_HEX" \
    "$WAY_POS_E2E_NEXT_TAK_UNDER_LMK" "$WAY_POS_E2E_NEXT_TAK_LENGTH")"
post_json "$SERVER_BASE_URL/api/admin/waypos/v1/terminal-keys" \
    "$next_tak_json" 200 201 202

echo "[WayPos E2E] Executing dynamic ANSI X9.17 key change..."
post_empty "$SIMULATOR_BASE_URL/api/simulator/v1/key-change?confirm=true"
"$PYTHON_BIN" - "$RESPONSE_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    payload = json.load(stream)
checks = (
    payload.get("responseCode") == "00",
    payload.get("responseMacVerified") is True,
    payload.get("confirmationSent") is True,
    payload.get("confirmationResponseCode") == "00",
    payload.get("confirmationMacVerified") is True,
    all(item.get("status") == "0"
        for item in payload.get("importedKeyStatuses", [])),
)
if not all(checks):
    raise SystemExit(
        "Dynamic key change was not fully accepted and MAC-verified")
PY

invoke_scenario() {
    local name="$1"
    local emv_data_hex="$2"
    local scenario_json
    scenario_json="$(printf \
        '{"pan":"%s","expiry":"%s","amount":"%s","targetPan":null,"pinBlockHex":"%s","emvDataHex":"%s","terminalId":"%s","merchantId":"%s","macEnabled":true,"batchId":null,"cardControlType":null}' \
        "$WAY_POS_E2E_PAN" "$WAY_POS_E2E_EXPIRY" "$WAY_POS_E2E_AMOUNT" \
        "$WAY_POS_E2E_PIN_BLOCK_HEX" "$emv_data_hex" \
        "$WAY_POS_TERMINAL_ID" "$WAY_POS_MERCHANT_ID")"
    post_json "$SIMULATOR_BASE_URL/api/simulator/v1/scenarios/$name" \
        "$scenario_json" 200 201 202
    "$PYTHON_BIN" - "$RESPONSE_FILE" "$name" >>"$RESULTS_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    payload = json.load(stream)
name = sys.argv[2]
if payload.get("completed") is not True:
    status = payload.get("status", "NO_RESPONSE")
    raise SystemExit(f"Scenario {name} failed with status {status}")
codes = ",".join(
    str(step.get("responseCode", ""))
    for step in payload.get("steps", []))
print("\t".join((
    name,
    str(payload.get("status", "")),
    codes,
    str(payload.get("batchId", "")),
)))
PY
}

echo "[WayPos E2E] Executing PIN/ARQC/ARPC and lifecycle scenarios..."
# EOD runs first so its reconciliation totals refer to a clean batch.
invoke_scenario PURCHASE_EOD "$WAY_POS_E2E_EMV_EOD_HEX"
invoke_scenario PURCHASE_REPEAT "$WAY_POS_E2E_EMV_REPEAT_HEX"
invoke_scenario PURCHASE_REVERSAL "$WAY_POS_E2E_EMV_REVERSAL_HEX"
invoke_scenario AUTHORIZATION_FINAL_ADVICE "$WAY_POS_E2E_EMV_ADVICE_HEX"

echo "[WayPos E2E] SUCCESS - no clear key, PIN or PAN was printed."
printf '%-30s %-16s %-20s %s\n' \
    "SCENARIO" "STATUS" "RESPONSE_CODES" "BATCH_ID"
while IFS=$'\t' read -r scenario status codes batch_id; do
    printf '%-30s %-16s %-20s %s\n' \
        "$scenario" "$status" "$codes" "$batch_id"
done <"$RESULTS_FILE"
