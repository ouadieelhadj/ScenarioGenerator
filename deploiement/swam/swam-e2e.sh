#!/usr/bin/env bash
set +H

# ================================================================
# swam-e2e.sh — Test E2E SWAM complet
# Flux : demarrage -> KEK bootstrap -> sign-on -> key exchange
#        -> achat sans PIN -> achat PIN correct -> achat PIN incorrect
#        -> fonds insuffisants -> signoff
# Resultat attendu : PASSED
# ================================================================

ROOT="/d/MoneyCore/ScenarioGenerator"
JAVA="/d/MoneyCore/jdk-21.0.11/bin/java.exe"
MVN="${MVN:-mvn}"
PSQL="/d/MoneyCore/PostgreSQL/18/bin/psql.exe"
JAVA_HOME_DIR="/d/MoneyCore/jdk-21.0.11"
MAVEN_REPO="${MAVEN_REPO:-D:/MoneyCore/ScenarioGenerator/tmp/m2repo}"

ISS_PORT=8511 ; ISS_HEALTH="http://localhost:${ISS_PORT}/api/swam/issuer/health"
ACQ_PORT=8094 ; ACQ_HEALTH="http://localhost:${ACQ_PORT}/api/admin/swam/health"
BASE="http://localhost:${ACQ_PORT}/api/admin/swam"
ISS_BOOT="http://localhost:${ISS_PORT}/api/admin/swam/kek/bootstrap"
ACQ_BOOT="http://localhost:${ACQ_PORT}/api/admin/swam/kek/bootstrap"

MGID="TESTGRP01"
KEKCLEAR="0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
PAN_OK="5321962145453348"   # ACTIVE, solde suffisant
PAN_LOW="5321000000000011"  # ACTIVE, solde 500
PIN_OK="1234"
PIN_BAD="9999"
ISS_LOG="/tmp/swam-issuer-e2e.log"
ACQ_LOG="/tmp/swam-acquirer-e2e.log"

PASS=0; FAIL=0

ok()   { echo "  [OK]  $1"; PASS=$((PASS+1)); }
fail() { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
check_http() { [ "$1" = "$2" ] && ok "$3" || fail "$3 (attendu=$2 recu=$1)"; }
check_json() { echo "$1" | grep -q "$2" && ok "$3" || fail "$3 (valeur '$2' absente de: $1)"; }

db() { PGPASSWORD=postgres123 "$PSQL" -U postgres -h localhost -d scenariogenerator -tAc "$1" 2>/dev/null; }

echo "=================================================================="
echo " SWAM E2E — $(date '+%Y-%m-%d %H:%M:%S')"
echo "=================================================================="

# Arreter d'abord les JARs qui verrouillent target sous Windows.
"$JAVA_HOME_DIR/bin/jps.exe" -l 2>/dev/null | \
  grep -E 'sg-swam-(issuer|acquirer).*\.jar' | awk '{print $1}' | \
  while read -r p; do taskkill //PID "$p" //F 2>/dev/null || true; done
for PORT in 8510 8511 8094; do
  for p in $(netstat -ano 2>/dev/null | grep LISTENING | grep ":$PORT" | awk '{print $NF}' | sort -u); do
    taskkill //PID "$p" //F 2>/dev/null || true
  done
done
sleep 2

# ---------------------------------------------------------------
# 1) Compilation
# ---------------------------------------------------------------
echo; echo "--- 1) Compilation des modules SWAM ---"
cd "$ROOT"
JAVA_HOME="$JAVA_HOME_DIR" "$MVN" -q "-Dmaven.repo.local=$MAVEN_REPO" -pl sg-swam-issuer,sg-swam-acquirer -am package -DskipTests \
  && ok "Compilation" || { fail "Compilation"; exit 1; }

# ---------------------------------------------------------------
# 2) Arret des instances existantes
# ---------------------------------------------------------------
echo; echo "--- 2) Arret des instances existantes ---"
for PORT in 8510 8511 8094; do
  for p in $(netstat -ano 2>/dev/null | grep LISTENING | grep ":$PORT" | awk '{print $NF}' | sort -u); do
    taskkill //PID "$p" //F 2>/dev/null && echo "  kill $p ($PORT)" || true
  done
done
sleep 2

# ---------------------------------------------------------------
# 3) Demarrage des modules
# ---------------------------------------------------------------
start_mod() {
  local dir="$1" health="$2" logf="$3" label="$4" iface="$5"
  local jar; jar="$(find "$ROOT/$dir/target" -maxdepth 1 -name "*.jar" ! -name "*.original" 2>/dev/null | head -1)"
  nohup "$JAVA" -jar "$jar" "--sg.interface=$iface" > "$logf" 2>&1 & disown 2>/dev/null || true
  echo -n "  Attente $label "
  for i in $(seq 1 60); do
    curl -s -o /dev/null -w "%{http_code}" "$health" 2>/dev/null | grep -q "200" && { echo " UP"; return 0; }
    echo -n "."; sleep 1
  done
  echo " TIMEOUT"; tail -n 20 "$logf"; return 1
}

echo; echo "--- 3) Demarrage SWITCH (issuer) ---"
start_mod "sg-swam-issuer"  "$ISS_HEALTH" "$ISS_LOG" "SWITCH" "SWAM_NETWORK_1" && ok "Switch demarre" || { fail "Switch non demarre"; exit 1; }

echo; echo "--- 4) Demarrage MEMBRE (acquereur) ---"
start_mod "sg-swam-acquirer" "$ACQ_HEALTH" "$ACQ_LOG" "MEMBRE" "SWAM_MEMBER_A" && ok "Membre demarre" || { fail "Membre non demarre"; exit 1; }

# ---------------------------------------------------------------
# 5) Bootstrap KEK (issuer + acquereur)
# ---------------------------------------------------------------
echo; echo "--- 5) Bootstrap KEK (issuer + acquereur) ---"
BODY="{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEKCLEAR\"}"

H=$(curl -s -o /tmp/kek-iss.json -w "%{http_code}" -X POST "$ISS_BOOT" -H "Content-Type: application/json" -d "$BODY")
check_http "$H" "200" "Bootstrap KEK issuer"
KCV_ISS=$(grep -o '"kcv":"[^"]*"' /tmp/kek-iss.json | cut -d'"' -f4)

H=$(curl -s -o /tmp/kek-acq.json -w "%{http_code}" -X POST "$ACQ_BOOT" -H "Content-Type: application/json" -d "$BODY")
check_http "$H" "200" "Bootstrap KEK acquereur"
KCV_ACQ=$(grep -o '"kcv":"[^"]*"' /tmp/kek-acq.json | cut -d'"' -f4)

[ "$KCV_ISS" = "$KCV_ACQ" ] && ok "KCV KEK concordants ($KCV_ISS)" || fail "KCV KEK divergents (iss=$KCV_ISS acq=$KCV_ACQ)"

# ---------------------------------------------------------------
# 6) Sign-on
# ---------------------------------------------------------------
echo; echo "--- 6) Sign-on (1804 DE24=801) ---"
R=$(curl -s -X POST "$BASE/network/signon")
check_json "$R" '"success":true' "Sign-on"

# ---------------------------------------------------------------
# 7) Key exchange ZPK + ZAK
# ---------------------------------------------------------------
echo; echo "--- 7) Reception de la ZPK poussee par le switch ---"
ZPK_MATCH=""
for i in $(seq 1 20); do
  ZPK_MATCH=$(db "SELECT (i.kcv=a.kcv)::text FROM swam_iss_keys i JOIN swam_acq_keys a ON i.member_group_id=a.member_group_id AND i.key_type=a.key_type WHERE i.key_type='PEK' AND i.member_group_id='$MGID' AND i.status='ACTIVE' AND a.status='ACTIVE' LIMIT 1;")
  { [ "$ZPK_MATCH" = "t" ] || [ "$ZPK_MATCH" = "true" ]; } && break
  sleep 1
done
[ "$ZPK_MATCH" = "t" ] || [ "$ZPK_MATCH" = "true" ] && ok "ZPK poussee et KCV concordant en base" || fail "ZPK poussee absente ou KCV divergent"

# ---------------------------------------------------------------
# 8) Tests d'achat
# ---------------------------------------------------------------
echo; echo "--- 8) Achat SANS PIN (doit etre approuve) ---"
BAL0=$(db "SELECT balance FROM issuer_swam_cards WHERE pan='$PAN_OK';")
R=$(curl -s -X POST "$BASE/purchase?pan=$PAN_OK&amount=000000010000")
check_json "$R" '"approved":true'     "Achat sans PIN approuve"
check_json "$R" '"de39_action":"000"' "Achat sans PIN DE39=000"
check_json "$R" '"pin_sent":false'    "Achat sans PIN pin_sent=false"
BAL1=$(db "SELECT balance FROM issuer_swam_cards WHERE pan='$PAN_OK';")
[ "$BAL1" = "$((BAL0-10000))" ] && ok "Solde debite (${BAL0}->${BAL1})" || fail "Solde incorrect (attendu $((BAL0-10000)) recu $BAL1)"

echo; echo "--- 9) Achat avec PIN CORRECT (doit etre approuve) ---"
R=$(curl -s -X POST "$BASE/purchase?pan=$PAN_OK&amount=000000010000&pin=$PIN_OK")
check_json "$R" '"approved":true'     "Achat PIN correct approuve"
check_json "$R" '"de39_action":"000"' "Achat PIN correct DE39=000"
check_json "$R" '"pin_sent":true'     "Achat PIN correct pin_sent=true"
BAL2=$(db "SELECT balance FROM issuer_swam_cards WHERE pan='$PAN_OK';")
[ "$BAL2" = "$((BAL1-10000))" ] && ok "Solde debite (${BAL1}->${BAL2})" || fail "Solde incorrect"

echo; echo "--- 10) Achat avec PIN INCORRECT (doit renvoyer DE39=117) ---"
R=$(curl -s -X POST "$BASE/purchase?pan=$PAN_OK&amount=000000010000&pin=$PIN_BAD")
check_json "$R" '"approved":false'    "Achat PIN incorrect refuse"
check_json "$R" '"de39_action":"117"' "Achat PIN incorrect DE39=117"
BAL3=$(db "SELECT balance FROM issuer_swam_cards WHERE pan='$PAN_OK';")
[ "$BAL3" = "$BAL2" ] && ok "Solde inchange apres PIN KO" || fail "Solde modifie malgre PIN KO"

echo; echo "--- 11) Achat FONDS INSUFFISANTS (doit renvoyer DE39=116) ---"
R=$(curl -s -X POST "$BASE/purchase?pan=$PAN_LOW&amount=000000010000")
check_json "$R" '"approved":false'    "Fonds insuffisants refuse"
check_json "$R" '"de39_action":"116"' "Fonds insuffisants DE39=116"

echo; echo "--- 12) Sign-off ---"
R=$(curl -s -X POST "$BASE/network/signoff")
check_json "$R" '"success":true' "Sign-off"

# ---------------------------------------------------------------
# Resultats
# ---------------------------------------------------------------
TOTAL=$((PASS+FAIL))
echo
echo "=================================================================="
if [ "$FAIL" -eq 0 ]; then
  echo " RESULTAT : PASSED ($PASS/$TOTAL tests OK)"
else
  echo " RESULTAT : FAILED ($FAIL echec(s) sur $TOTAL tests)"
  echo " Logs : $ISS_LOG  /  $ACQ_LOG"
fi
echo " ZPK KCV=$KCV_ZPK  ZAK KCV=$KCV_ZAK  KEK KCV=$KCV_ISS"
echo "=================================================================="
