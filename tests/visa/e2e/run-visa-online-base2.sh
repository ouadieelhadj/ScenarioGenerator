#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RUNTIME="${VISA_E2E_RUNTIME:-$ROOT/runtime/visa-e2e}"
LOG_DIR="$RUNTIME/logs"
PID_DIR="$RUNTIME/pids"
RESULT_DIR="$RUNTIME/results"
MAVEN="${MAVEN_CMD:-D:/MoneyCore/idea-2026.1.3.win/plugins/maven/lib/maven3/bin/mvn.cmd}"
MAVEN_REPO="${MAVEN_REPO:-D:/MoneyCore/.m2/repository}"
JAVA="${JAVA_CMD:-D:/MoneyCore/idea-2026.1.3.win/jbr/bin/java.exe}"
mkdir -p "$LOG_DIR" "$PID_DIR" "$RESULT_DIR"

fail() { printf '[VISA E2E] ERREUR - %s\n' "$*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || fail "Commande absente: $1"; }
json_get() { python -c 'import json,sys; v=json.load(open(sys.argv[1],encoding="utf-8")); print(v[sys.argv[2]])' "$1" "$2"; }

wait_http() {
  local url="$1" label="$2"
  for _ in $(seq 1 "${VISA_E2E_STARTUP_TIMEOUT_SECONDS:-180}"); do
    curl -fsS --connect-timeout 2 --max-time 3 "$url" >/dev/null 2>&1 && {
      printf '[VISA E2E] UP - %s\n' "$label"; return 0;
    }
    sleep 1
  done
  fail "$label indisponible: $url"
}

start_jar() {
  local module="$1" label="$2" health="$3" jar pid
  jar="$ROOT/$module/target/$module-1.0.0-SNAPSHOT.jar"
  [[ -f "$jar" ]] || fail "JAR absent: $jar"
  curl -fsS --connect-timeout 1 --max-time 2 "$health" >/dev/null 2>&1 \
    && fail "$label utilise deja son port; aucun processus externe ne sera reutilise"
  nohup "$JAVA" -Xms48m -Xmx192m -XX:+UseSerialGC -jar "$jar" \
    --spring.profiles.active=connected-e2e --server.address=127.0.0.1 \
    >"$LOG_DIR/$label.log" 2>&1 &
  pid=$!
  printf '%s\n' "$pid" >"$PID_DIR/$module.pid"
  if ! wait_http "$health" "$label"; then tail -80 "$LOG_DIR/$label.log" >&2 || true; exit 1; fi
}

cleanup() {
  local file pid
  for file in "$PID_DIR"/*.pid; do
    [[ -f "$file" ]] || continue
    pid="$(tr -d '[:space:]' <"$file")"
    [[ "$pid" =~ ^[0-9]+$ ]] && kill "$pid" 2>/dev/null || true
    rm -f -- "$file"
  done
}
trap cleanup EXIT

require_command curl
require_command python
require_command "$JAVA"

if [[ "${VISA_E2E_SKIP_BUILD:-false}" != "true" ]]; then
  "$MAVEN" -o -nsu -f "$ROOT/pom.xml" \
    -pl sg-visa-mastercard-gateway-simulator,sg-visa-online-member,sg-visa-visanet-simulator,sg-visa-base2-member,sg-visa-base2-network-simulator \
    -am package -Dmaven.test.skip=true -Dmaven.repo.local="$MAVEN_REPO"
fi

export VISA_SIMULATOR_DEFAULT_RESPONSE_CODE=00
export VISA_VISANET_SIMULATOR_LOG_FILE="$LOG_DIR/visanet-network.log"
start_jar sg-visa-visanet-simulator visanet-network \
  http://127.0.0.1:8565/api/visa/network/v1/health

export VISA_ONLINE_NETWORK_ENABLED=true
export VISA_ONLINE_NETWORK_BASE_URL=http://127.0.0.1:8565
export VISA_ONLINE_ACQUIRER_ID=123456
export VISA_ONLINE_ACQUIRER_COUNTRY=504
export VISA_ONLINE_DEFAULT_MCC=5999
export VISA_ONLINE_MERCHANT_LOCATION='MERCHANT TEST CASABLANCA MA'
export VISA_ONLINE_MEMBER_LOG_FILE="$LOG_DIR/visa-online-member.log"
start_jar sg-visa-online-member visa-online-member \
  http://127.0.0.1:8564/api/visa/online/v1/health

export CARD_NETWORK_MASTERCARD_ENABLED=false
export CARD_NETWORK_VISA_ENABLED=true
export CARD_NETWORK_VISA_BASE_URL=http://127.0.0.1:8564
export CARD_NETWORK_GATEWAY_LOG_FILE="$LOG_DIR/card-network-gateway.log"
start_jar sg-visa-mastercard-gateway-simulator card-network-gateway \
  http://127.0.0.1:8563/api/routing/v1/health

export VISA_BASE2_NETWORK_SIMULATOR_LOG_FILE="$LOG_DIR/base2-network.log"
start_jar sg-visa-base2-network-simulator base2-network \
  http://127.0.0.1:8567/api/visa/base2/network/v1/health

export VISA_BASE2_NETWORK_ENABLED=true
export VISA_BASE2_NETWORK_BASE_URL=http://127.0.0.1:8567
export VISA_BASE2_CIB=123456
export VISA_BASE2_ACQUIRING_IDENTIFIER=123456
export VISA_BASE2_BUSINESS_ID=12345678
export VISA_BASE2_MEMBER_LOG_FILE="$LOG_DIR/base2-member.log"
start_jar sg-visa-base2-member base2-member \
  http://127.0.0.1:8566/api/visa/base2/v1/health

# Carte publique de sandbox uniquement. Elle ne represente aucune carte reelle.
cat >"$RESULT_DIR/authorization-request.json" <<'JSON'
{"schemaVersion":"1.0","transactionId":"VISA-E2E-TX-001","correlationId":"VISA-E2E-CORR-001","idempotencyKey":"VISA-E2E-IDEM-001","operation":"AUTHORIZATION","sourceMti":"0100","processingCode":"000000","pan":"4111111111111111","expiry":"2912","amount":"000000001000","currency":"504","stan":"000001","rrn":"621512000001","terminalId":"ECOM0001","merchantId":"MID000000000001","attributes":{"cardProgram":"VISA","channel":"ECOMMERCE","eci":"05"}}
JSON

curl -fsS -H 'Content-Type: application/json' --data-binary @"$RESULT_DIR/authorization-request.json" \
  http://127.0.0.1:8563/api/routing/v1/transactions >"$RESULT_DIR/authorization-response.json"
curl -fsS -H 'Content-Type: application/json' --data-binary @"$RESULT_DIR/authorization-request.json" \
  http://127.0.0.1:8563/api/routing/v1/transactions >"$RESULT_DIR/authorization-replay.json"

[[ "$(json_get "$RESULT_DIR/authorization-response.json" status)" == "APPROVED" ]] \
  || fail "Autorisation Visa non approuvee"
[[ "$(json_get "$RESULT_DIR/authorization-response.json" networkResponseCode)" == "00" ]] \
  || fail "DE39 Visa different de 00"
cmp -s "$RESULT_DIR/authorization-response.json" "$RESULT_DIR/authorization-replay.json" \
  || fail "Le rejeu d'autorisation n'est pas idempotent"

python - "$RESULT_DIR/authorization-response.json" "$RESULT_DIR/presentment-request.json" <<'PY'
import json, sys
response = json.load(open(sys.argv[1], encoding='utf-8'))
attrs = response['attributes']
request = {
  'schemaVersion': '1.0', 'transactionId': 'VISA-E2E-TX-001',
  'correlationId': 'VISA-E2E-CORR-001', 'pan': '4111111111111111',
  'purchaseDateMmdd': '0802', 'amountMinor': 1000, 'currency': '504',
  'merchantName': 'MERCHANT TEST', 'merchantCity': 'CASABLANCA',
  'merchantCountry': 'MAR', 'mcc': '5999', 'merchantZip': '20000',
  'merchantState': 'CAS', 'posEntryMode': '10', 'aci': attrs['aci'],
  'authorizationCode': response['authorizationCode'],
  'visaTransactionId': attrs['visaTransactionId'],
  'authorizationResponseCode': response['networkResponseCode'],
  'validationCode': attrs['validationCode']
}
json.dump(request, open(sys.argv[2], 'w', encoding='utf-8'), separators=(',', ':'))
PY

curl -fsS -H 'Content-Type: application/json' --data-binary @"$RESULT_DIR/presentment-request.json" \
  http://127.0.0.1:8566/api/visa/base2/v1/presentments >"$RESULT_DIR/presentment-response.json"
curl -fsS -H 'Content-Type: application/json' --data-binary @"$RESULT_DIR/presentment-request.json" \
  http://127.0.0.1:8566/api/visa/base2/v1/presentments >"$RESULT_DIR/presentment-replay.json"
curl -fsS http://127.0.0.1:8567/api/visa/base2/network/v1/files \
  >"$RESULT_DIR/network-files.json"

[[ "$(json_get "$RESULT_DIR/presentment-response.json" networkStatus)" == "ACCEPTED" ]] \
  || fail "Fichier Base II non accepte"
[[ "$(json_get "$RESULT_DIR/presentment-response.json" recordCount)" == "5" ]] \
  || fail "Nombre de records Base II inattendu"
[[ "$(json_get "$RESULT_DIR/presentment-replay.json" replayed)" == "True" ]] \
  || fail "Le rejeu Base II n'a pas ete identifie"

printf '[VISA E2E] OK - autorisation Visa Online, rejeu idempotent, fichier Base II (5 records) et acquittement reseau valides.\n'
printf '[VISA E2E] Resultats: %s\n' "$RESULT_DIR"
