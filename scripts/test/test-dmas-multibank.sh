#!/usr/bin/env bash
# ============================================================
#  test-dmas-multibank.sh — DEUX BANQUES, DEUX JVM
#
#      JVM 1 : sg-mc-dmas-mastercard --sg.interface=MC_1,MC_2
#              ISOServer:8500 et ISOServer:8503
#
#      JVM 2 : sg-mc-dmas-member --sg.interface=BANK_A,BANK_B
#              deux liaisons permanentes
#
#  Verifie :
#    - deux ISOServer et deux sockets simultanes
#    - chaque banque signe et echange sa cle independamment
#    - les cles ne se melangent PAS (KCV differents)
#    - le statut de chaque interface est tenu a jour
# ============================================================
REPO="/f/ScenarioGenerator"
LOGS="$REPO/logs"
PG="/f/MoneyCore/pgsql/bin"
export JAVA_HOME="/f/MoneyCore/jdk-26_windows-x64_bin/jdk-26.0.1"
JAVA="$JAVA_HOME/bin/java.exe"

LOGIN="${1:-admin}"
PASSWORD="${2:-Admin123!}"
KEK_A="13AED5DA1F32347523C708C11F2608FD"
KEK_B="404142434445464748494A4B4C4D4E4F"

export PGPASSWORD=postgres123
PSQL="$PG/psql.exe -U postgres -h 127.0.0.1 -d scenariogenerator"

MEMBER_JAR="$REPO/sg-mc-dmas-member/target/sg-mc-dmas-member-1.0.0-SNAPSHOT.jar"
MASTER_JAR="$REPO/sg-mc-dmas-mastercard/target/sg-mc-dmas-mastercard-1.0.0-SNAPSHOT.jar"

echo "=== [1/8] Arret et purge ==="
taskkill //F //IM java.exe > /dev/null 2>&1
sleep 3
$PSQL -c "SELECT 1" > /dev/null 2>&1 || {
    "$PG/pg_ctl.exe" -D /f/MoneyCore/pgsql/data -l /f/MoneyCore/pgsql/data/pg.log start > /dev/null 2>&1
    sleep 5
}
rm -f "$LOGS"/mc-multi-*.log
$PSQL -q -c "
DELETE FROM mc_dmas_member_keys     WHERE key_type='PEK';
DELETE FROM mc_dmas_mastercard_keys WHERE key_type='PEK';
UPDATE mc_dmas_interface SET status='OFF';" > /dev/null 2>&1
echo "    PEK purgees, statuts a OFF"

echo ""
echo "=== [2/8] JVM 1 : les deux Mastercard ==="
"$JAVA" -jar "$MASTER_JAR" \
    --sg.interface=DMAS_MASTERCARD_1,DMAS_MASTERCARD_2 \
    --logging.file.name="$LOGS/mc-multi-network.log" > /dev/null 2>&1 &
for i in $(seq 1 50); do
    sleep 1
    grep -q "Started SgMcDmasMastercard" "$LOGS/mc-multi-network.log" 2>/dev/null && break
    [ $i -eq 50 ] && { echo "    ECHEC"; tail -15 "$LOGS/mc-multi-network.log"; exit 1; }
done
echo "    demarree"
grep -E "ISOServer demarre|serveurs ISO" "$LOGS/mc-multi-network.log" | sed 's|^.*: |    |'

echo ""
echo "=== [3/8] JVM 2 : les deux banques ==="
"$JAVA" -jar "$MEMBER_JAR" \
    --sg.interface=DMAS_BANK_A,DMAS_BANK_B \
    --logging.file.name="$LOGS/mc-multi-member.log" > /dev/null 2>&1 &
for i in $(seq 1 50); do
    sleep 1
    grep -q "Started SgMcDmasMember" "$LOGS/mc-multi-member.log" 2>/dev/null && break
    [ $i -eq 50 ] && { echo "    ECHEC"; tail -15 "$LOGS/mc-multi-member.log"; exit 1; }
done
echo "    demarree"
grep "MULTI-BANQUE" "$LOGS/mc-multi-member.log" | sed 's|^.*: |    |'
sleep 2

echo ""
echo "=== [4/8] Ports a l'ecoute ==="
netstat -ano 2>/dev/null | grep -E ":(8084|8500|8503)[[:space:]]" \
    | grep LISTENING | sed 's|^|    |'

echo ""
echo "=== [5/8] Authentification ==="
TOK_M=$(curl -s -X POST http://localhost:8084/auth/login -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
TOK_S=$(curl -s -X POST http://localhost:8501/auth/login -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -z "$TOK_M" ] && { echo "    ECHEC"; exit 1; }
echo "    membre 8084 et reseau 8501"

echo ""
echo "=== [6/8] Sign-on des deux banques ==="
curl -s -X POST http://localhost:8084/api/admin/dmas/network/signon-all \
    -H "Authorization: Bearer $TOK_M"
echo ""
sleep 1
echo ""
echo "--- membres connectes, vus par le reseau ---"
curl -s http://localhost:8501/api/admin/dmas/jpos/sessions -H "Authorization: Bearer $TOK_S"
echo ""

echo ""
echo "=== [7/8] KEK et echange 162, par banque ==="
echo "--- KEK banque A (TESTGRP01) ---"
curl -s -X POST http://localhost:8084/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_M" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"TESTGRP01\",\"kekClear\":\"$KEK_A\"}" > /dev/null
curl -s -X POST http://localhost:8501/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_S" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"TESTGRP01\",\"kekClear\":\"$KEK_A\"}" > /dev/null
echo "    posee des deux cotes"
echo "--- KEK banque B (TESTGRP02) ---"
curl -s -X POST http://localhost:8084/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_M" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"TESTGRP02\",\"kekClear\":\"$KEK_B\"}" > /dev/null
curl -s -X POST http://localhost:8501/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_S" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"TESTGRP02\",\"kekClear\":\"$KEK_B\"}" > /dev/null
echo "    posee des deux cotes"

echo ""
echo "--- sollicitation banque A ---"
curl -s -X POST "http://localhost:8084/api/admin/dmas/keys/solicit?bank=022905" \
    -H "Authorization: Bearer $TOK_M"
echo ""
sleep 4
echo "--- sollicitation banque B ---"
curl -s -X POST "http://localhost:8084/api/admin/dmas/keys/solicit?bank=022906" \
    -H "Authorization: Bearer $TOK_M"
echo ""
sleep 5

echo ""
echo "=== [8/8] RESULTAT ==="
echo ""
echo "--- Statuts ---"
$PSQL -c "SELECT id_interface, bank_code, status FROM mc_dmas_interface ORDER BY 1;"

echo ""
echo "--- Cles, par banque ---"
$PSQL -c "
SELECT 'membre' AS cote, member_group_id, kcv, status
FROM mc_dmas_member_keys WHERE key_type='PEK'
UNION ALL
SELECT 'reseau', member_group_id, kcv, status
FROM mc_dmas_mastercard_keys WHERE key_type='PEK'
ORDER BY 2, 1;"

echo ""
echo "--- VERDICT ---"
TOT=$($PSQL -tAc "
SELECT count(*) FROM (
  SELECT kcv FROM mc_dmas_member_keys     WHERE key_type='PEK' AND status='ACTIVE'
  UNION ALL
  SELECT kcv FROM mc_dmas_mastercard_keys WHERE key_type='PEK' AND status='ACTIVE'
) t;" 2>/dev/null | tr -d ' \r\n')
NB=$($PSQL -tAc "
SELECT count(DISTINCT kcv) FROM (
  SELECT kcv FROM mc_dmas_member_keys     WHERE key_type='PEK' AND status='ACTIVE'
  UNION ALL
  SELECT kcv FROM mc_dmas_mastercard_keys WHERE key_type='PEK' AND status='ACTIVE'
) t;" 2>/dev/null | tr -d ' \r\n')

if [ "$TOT" = "4" ] && [ "$NB" = "2" ]; then
    echo "  OK — 4 cles ACTIVE, 2 KCV distincts"
    echo "  Chaque banque a SA cle, partagee avec SON Mastercard."
else
    echo "  ECHEC — $TOT cle(s) ACTIVE, $NB KCV distinct(s) (attendu 4 et 2)"
fi

echo ""
echo "--- Sockets etablies ---"
netstat -ano 2>/dev/null | grep -E ":(8500|8503)[[:space:]]" | grep ESTABLISHED | sed 's|^|    |'

echo ""
echo "--- Enchainement, cote membre ---"
grep -E "JPOS-CLI:|DMAS-KEX" "$LOGS/mc-multi-member.log" 2>/dev/null | tail -12 | sed 's|^.*: |  |'
echo ""
echo "--- Enchainement, cote reseau ---"
grep -E "JPOS-SRV:|DMAS-KEXS" "$LOGS/mc-multi-network.log" 2>/dev/null | tail -12 | sed 's|^.*: |  |'
