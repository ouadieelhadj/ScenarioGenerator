#!/usr/bin/env bash
set -euo pipefail
set +H

# SWAM SID + LIS bilateral E2E
# Route A: member acquirer -> switch issuer
# Route B: switch -> member on the very same permanent SID connection.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib-swam.sh
source "$SCRIPT_DIR/lib-swam.sh"

MVN="${MVN:-$MAVEN}"
MAVEN_REPO="${MAVEN_REPO:-$ROOT/tmp/m2repo}"
DB="${DB:-$DB_NAME}"
DB_PASSWORD="${DB_PASSWORD:-}"
BUSINESS_DATE="${BUSINESS_DATE:-$(date +%F)}"
MEMBER_CODE="${MEMBER_CODE:-000123}"
SWITCH_CODE="${SWITCH_CODE:-000088}"
MEMBER_ID="${MEMBER_ID:-TESTGRP01}"
MEMBER_LIS="http://localhost:${SWAM_LIS_MEMBER_HTTP_PORT:-8521}"
SWITCH_LIS="http://localhost:${SWAM_LIS_SWITCH_HTTP_PORT:-8522}"
MEMBER_PURCHASE_BASE="${SWAM_MEMBER_PURCHASE_BASE:-http://localhost:8094/api/admin/swam}"
SWITCH_PURCHASE_BASE="${SWAM_SWITCH_PURCHASE_BASE:-http://localhost:8511/api/admin/swam}"
MEMBER_PAN="${SWAM_MEMBER_CARD_PAN:-5321962145453355}"
SWITCH_PAN="${SWAM_SWITCH_CARD_PAN:-5321962145453348}"
KEK_CLEAR="${SWAM_E2E_KEK_CLEAR:-}"
RUN_SID_BOOTSTRAP="${RUN_SID_BOOTSTRAP:-true}"
MANAGE_LIS_SERVICES="${SWAM_LIS_MANAGE_SERVICES:-true}"
PASS=0
E2E_RUN_ID="${E2E_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
E2E_RUNTIME="${SWAM_E2E_RUNTIME:-$ROOT/runtime/e2e/$E2E_RUN_ID}"
if command -v cygpath >/dev/null 2>&1; then
  E2E_RUNTIME="$(cygpath -m "$E2E_RUNTIME")"
fi
export SWAM_LIS_MEMBER_OUTPUT="${SWAM_LIS_MEMBER_OUTPUT:-$E2E_RUNTIME/member/outgoing}"
export SWAM_LIS_MEMBER_INCOMING="${SWAM_LIS_MEMBER_INCOMING:-$E2E_RUNTIME/member/incoming}"
export SWAM_LIS_SWITCH_OUTPUT="${SWAM_LIS_SWITCH_OUTPUT:-$E2E_RUNTIME/switch/outgoing}"
export SWAM_LIS_SWITCH_INCOMING="${SWAM_LIS_SWITCH_INCOMING:-$E2E_RUNTIME/switch/incoming}"

ok(){ echo "  [OK] $1"; PASS=$((PASS+1)); }
die(){ echo "  [FAIL] $*" >&2; exit 1; }
[[ -n "$DB_PASSWORD" ]] || die "DB_PASSWORD obligatoire"
db(){ PGPASSWORD="$DB_PASSWORD" "$PSQL" -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB" -tAc "$1"; }
post(){
  local url="$1"; shift
  local response
  response=$(curl -fsS -X POST "$url" "$@") || die "POST $url"
  printf '%s' "$response"
}
json_has(){ printf '%s' "$1" | grep -q "$2" || die "$3: $1"; ok "$3"; }
wait_up(){
  local url="$1" name="$2"
  for _ in $(seq 1 90); do curl -fsS "$url" >/dev/null 2>&1 && { ok "$name UP"; return; }; sleep 1; done
  die "$name indisponible"
}
stop_port(){
  local port="$1" pid
  for pid in $(netstat -ano 2>/dev/null | awk -v suffix=":$port" \
      '$2 ~ suffix"$" && $4=="LISTENING" {print $5}' | sort -u); do
    taskkill.exe //F //PID "$pid" >/dev/null 2>&1 || true
  done
  for _ in $(seq 1 20); do
    netstat -ano 2>/dev/null | awk -v suffix=":$port" \
      '$2 ~ suffix"$" && $4=="LISTENING" {found=1} END {exit found?0:1}' \
      || return 0
    sleep 1
  done
  die "Port $port toujours occupe apres arret"
}
start_jar(){
  local module="$1" log="$2" iface="${3:-}"; local jar pid
  jar=$(find "$ROOT/$module/target" -maxdepth 1 -name '*.jar' ! -name '*.original' | head -1)
  [ -n "$jar" ] || die "JAR absent: $module"
  if [ -n "$iface" ]; then
    nohup "$JAVA" -jar "$jar" "--sg.interface=$iface" </dev/null >"$log" 2>&1 &
  else
    nohup "$JAVA" -jar "$jar" </dev/null >"$log" 2>&1 &
  fi
  pid=$!
  if [ -n "${SWAM_PID_DIR:-}" ]; then
    mkdir -p "$SWAM_PID_DIR"
    echo "$pid" >"$SWAM_PID_DIR/$module.pid"
  fi
  disown 2>/dev/null || true
}
purchase_five(){
  local base="$1" pan="$2" label="$3"
  for i in $(seq 1 5); do
    local amount response
    amount=$(printf '%012d' $((1000+i)))
    response=$(post "$base/financial?pan=$pan&amount=$amount")
    json_has "$response" '"approved":true' "$label achat $i approuve"
  done
}
latest_path(){
  local side="$1"
  db "SELECT storage_path FROM ${side}_lis_file WHERE direction='OUTGOING' ORDER BY id DESC LIMIT 1;"
}
to_gitbash_path(){
  printf '%s' "$1" | sed -E 's#^([A-Za-z]):#/\L\1#; s#\\#/#g'
}

echo "=== SWAM LIS bilateral E2E - $BUSINESS_DATE ==="
cd "$ROOT"
export JAVA_HOME="$JAVA_HOME_DIR"

if [ "$RUN_SID_BOOTSTRAP" = true ]; then
  [ -n "$KEK_CLEAR" ] || die "SWAM_E2E_KEK_CLEAR obligatoire pour le bootstrap des cles"
  PGPASSWORD="$DB_PASSWORD" "$PSQL" -v ON_ERROR_STOP=1 -U "$DB_USER" \
    -h "$DB_HOST" -p "$DB_PORT" -d "$DB" \
    -f "$ROOT/sql/16b_swam_sid_clearing_fields.sql" >/dev/null
  ok "Migration journaux SID pour clearing"
  stop_port 8510
  stop_port 8511
  stop_port 8094
  "$JAVA_HOME_DIR/bin/jps.exe" -l 2>/dev/null | \
    grep -E 'sg-swam-(issuer|acquirer|lis-member|lis-switch).*\.jar' | awk '{print $1}' | \
    while read -r pid; do taskkill //PID "$pid" //F >/dev/null 2>&1 || true; done || true
  sleep 2
  "$MVN" -q "-Dmaven.repo.local=$MAVEN_REPO" \
    -pl sg-swam-issuer,sg-swam-acquirer -am package -DskipTests
  start_jar sg-swam-issuer /tmp/swam-issuer-e2e.log SWAM_NETWORK_1
  wait_up "http://localhost:8511/api/swam/issuer/health" "SWAM switch issuer"
  start_jar sg-swam-acquirer /tmp/swam-acquirer-e2e.log SWAM_MEMBER_A
  wait_up "http://localhost:8094/api/admin/swam/health" "SWAM membre"
  KEK_BODY="{\"memberGroupId\":\"$MEMBER_ID\",\"kekClear\":\"$KEK_CLEAR\"}"
  post "http://localhost:8511/api/admin/swam/kek/bootstrap" \
    -H 'Content-Type: application/json' -d "$KEK_BODY" >/dev/null
  post "http://localhost:8094/api/admin/swam/kek/bootstrap" \
    -H 'Content-Type: application/json' -d "$KEK_BODY" >/dev/null
  json_has "$(post "$MEMBER_PURCHASE_BASE/network/signon")" \
    '"success":true' "Connexion SID, KEK et sign-on"
  for _ in $(seq 1 20); do
    KEY_MATCH=$(db "SELECT (i.kcv=a.kcv)::text FROM swam_iss_keys i JOIN swam_acq_keys a
      ON i.member_group_id=a.member_group_id AND i.key_type=a.key_type
      WHERE i.member_group_id='$MEMBER_ID' AND i.key_type='PEK'
      AND i.status='ACTIVE' AND a.status='ACTIVE' LIMIT 1;")
    { [ "$KEY_MATCH" = t ] || [ "$KEY_MATCH" = true ]; } && break
    sleep 1
  done
  { [ "${KEY_MATCH:-}" = t ] || [ "${KEY_MATCH:-}" = true ]; } \
    || die "ZPK poussee non concordante"
  ok "ZPK poussee sur la liaison permanente"
  db "TRUNCATE TABLE swam_acq_transactions, swam_iss_transactions RESTART IDENTITY;" >/dev/null
  ok "Journaux SID reinitialises avant les 10 achats de clearing"
fi

purchase_five "$MEMBER_PURCHASE_BASE" "$SWITCH_PAN" "membre vers switch"

json_has "$(curl -fsS "$SWITCH_PURCHASE_BASE/connection")" \
  '"mode":"SINGLE_PERMANENT_BIDIRECTIONAL"' "Liaison SID unique bidirectionnelle"
purchase_five "$SWITCH_PURCHASE_BASE" "$MEMBER_PAN" "switch vers membre"

PGPASSWORD="$DB_PASSWORD" "$PSQL" -v ON_ERROR_STOP=1 -U "$DB_USER" \
  -h "$DB_HOST" -p "$DB_PORT" -d "$DB" \
  -f "$ROOT/sql/17_swam_lis_clearing.sql" >/dev/null
ok "Schema clearing installe"
db "TRUNCATE TABLE member_lis_business_day, switch_lis_business_day RESTART IDENTITY CASCADE;" >/dev/null
ok "Donnees clearing E2E reinitialisees"

if [[ "$MANAGE_LIS_SERVICES" == "true" ]]; then
  "$MVN" -q "-Dmaven.repo.local=$MAVEN_REPO" \
    -pl sg-swam-lis-member,sg-swam-lis-switch -am package -DskipTests
  stop_port "${SWAM_LIS_MEMBER_HTTP_PORT:-8521}"
  stop_port "${SWAM_LIS_SWITCH_HTTP_PORT:-8522}"
  start_jar sg-swam-lis-member /tmp/swam-lis-member.log
  start_jar sg-swam-lis-switch /tmp/swam-lis-switch.log
fi
wait_up "$MEMBER_LIS/api/clearing/health" "LIS membre"
wait_up "$SWITCH_LIS/api/clearing/health" "LIS switch"

json_has "$(post "$MEMBER_LIS/api/clearing/eod?businessDate=$BUSINESS_DATE&requestedBy=e2e")" \
  '"status":"COMPLETED"' "EOD membre"
json_has "$(post "$SWITCH_LIS/api/clearing/eod?businessDate=$BUSINESS_DATE&requestedBy=e2e")" \
  '"status":"COMPLETED"' "EOD switch"

json_has "$(post "$MEMBER_LIS/api/clearing/lis/outgoing?businessDate=$BUSINESS_DATE&destinationBankCode=$SWITCH_CODE")" \
  '"status":"GENERATED"' "LIS outgoing membre"
MEMBER_FILE=$(to_gitbash_path "$(latest_path member)")
json_has "$(post "$SWITCH_LIS/api/clearing/lis/outgoing?businessDate=$BUSINESS_DATE&destinationBankCode=$MEMBER_CODE")" \
  '"status":"GENERATED"' "LIS outgoing switch"
SWITCH_FILE=$(to_gitbash_path "$(latest_path switch)")

json_has "$(post "$SWITCH_LIS/api/clearing/lis/incoming" -F "file=@$MEMBER_FILE")" \
  '"status":"PROCESSED"' "Integration LIS membre cote switch"
json_has "$(post "$MEMBER_LIS/api/clearing/lis/incoming" -F "file=@$SWITCH_FILE")" \
  '"status":"PROCESSED"' "Integration LIS switch cote membre"

MEMBER_TX=$(db "SELECT id FROM member_clearing_transaction WHERE incoming_lis_file_id IS NOT NULL ORDER BY id LIMIT 1;")
SWITCH_TX=$(db "SELECT id FROM switch_clearing_transaction WHERE incoming_lis_file_id IS NOT NULL ORDER BY id LIMIT 1;")
[ -n "$MEMBER_TX" ] && [ -n "$SWITCH_TX" ] || die "Transactions rapprochables absentes"

CB_MEMBER=$(post "$MEMBER_LIS/api/clearing/chargebacks" -H 'Content-Type: application/json' \
  -d "{\"clearingTransactionId\":$MEMBER_TX,\"reasonCode\":\"1001\",\"amount\":1001,\"currency\":\"504\",\"counterpartyMember\":\"$SWITCH_CODE\",\"createdBy\":\"e2e\",\"manualReason\":\"E2E member chargeback\"}")
json_has "$CB_MEMBER" '"status":"READY_TO_SEND"' "Chargeback emis membre"
CB_SWITCH=$(post "$SWITCH_LIS/api/clearing/chargebacks" -H 'Content-Type: application/json' \
  -d "{\"clearingTransactionId\":$SWITCH_TX,\"reasonCode\":\"1002\",\"amount\":1002,\"currency\":\"504\",\"counterpartyMember\":\"$MEMBER_CODE\",\"createdBy\":\"e2e\",\"manualReason\":\"E2E switch chargeback\"}")
json_has "$CB_SWITCH" '"status":"READY_TO_SEND"' "Chargeback emis switch"

# Second cycle files carry both emitted chargebacks.
post "$MEMBER_LIS/api/clearing/lis/outgoing?businessDate=$BUSINESS_DATE&destinationBankCode=$SWITCH_CODE" >/dev/null
MEMBER_CB_FILE=$(to_gitbash_path "$(latest_path member)")
post "$SWITCH_LIS/api/clearing/lis/outgoing?businessDate=$BUSINESS_DATE&destinationBankCode=$MEMBER_CODE" >/dev/null
SWITCH_CB_FILE=$(to_gitbash_path "$(latest_path switch)")
json_has "$(post "$SWITCH_LIS/api/clearing/lis/incoming" -F "file=@$MEMBER_CB_FILE")" \
  '"status":"PROCESSED"' "Chargeback membre recu par switch"
json_has "$(post "$MEMBER_LIS/api/clearing/lis/incoming" -F "file=@$SWITCH_CB_FILE")" \
  '"status":"PROCESSED"' "Chargeback switch recu par membre"

RECEIVED_MEMBER=$(db "SELECT id FROM member_chargeback WHERE direction='RECEIVED' AND status='RECEIVED' ORDER BY id DESC LIMIT 1;")
[ -n "$RECEIVED_MEMBER" ] || die "Chargeback recu membre absent"
json_has "$(post "$MEMBER_LIS/api/clearing/chargebacks/$RECEIVED_MEMBER/representation" \
  -H 'Content-Type: application/json' -d '{"createdBy":"e2e","justification":"E2E representation"}')" \
  '"status":"READY_TO_SEND"' "Representation creee membre"
post "$MEMBER_LIS/api/clearing/lis/outgoing?businessDate=$BUSINESS_DATE&destinationBankCode=$SWITCH_CODE" >/dev/null
REP_FILE=$(to_gitbash_path "$(latest_path member)")
json_has "$(post "$SWITCH_LIS/api/clearing/lis/incoming" -F "file=@$REP_FILE")" \
  '"status":"PROCESSED"' "Representation integree cote switch"

json_has "$(post "$MEMBER_LIS/api/clearing/accounting/post?businessDate=$BUSINESS_DATE")" \
  '"status":"BALANCED"' "Comptabilisation membre equilibree"
json_has "$(post "$SWITCH_LIS/api/clearing/accounting/post?businessDate=$BUSINESS_DATE")" \
  '"status":"BALANCED"' "Comptabilisation switch equilibree"

UNBALANCED=$(db "SELECT count(*) FROM (
 SELECT clearing_transaction_id, sum(debit)-sum(credit) balance
 FROM member_accounting_entry GROUP BY clearing_transaction_id
 UNION ALL
 SELECT clearing_transaction_id, sum(debit)-sum(credit)
 FROM switch_accounting_entry GROUP BY clearing_transaction_id) x WHERE balance<>0;")
[ "$UNBALANCED" = "0" ] || die "Ecritures non equilibrees: $UNBALANCED"
ok "Toutes les ecritures sont equilibrees"

echo "RESULTAT : PASSED ($PASS controles)"
