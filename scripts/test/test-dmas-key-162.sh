#!/usr/bin/env bash
# ============================================================
#  test-key-162.sh — echange de cle DMAS, mecanisme 162
#
#  Deroule les 5 etapes et verifie que les deux cotes
#  detiennent la meme cle a l'arrivee.
# ============================================================
REPO="/f/ScenarioGenerator"
LOGS="$REPO/logs"
PG="/f/MoneyCore/pgsql/bin"
export JAVA_HOME="/f/MoneyCore/jdk-26_windows-x64_bin/jdk-26.0.1"
JAVA="$JAVA_HOME/bin/java.exe"

LOGIN="${1:-admin}"
PASSWORD="${2:-Admin123!}"
KEK="13AED5DA1F32347523C708C11F2608FD"
MGID="TESTGRP01"

echo "=== [1/7] Redemarrage ==="
taskkill //F //IM java.exe > /dev/null 2>&1
sleep 3
export PGPASSWORD=postgres123
"$PG/psql.exe" -U postgres -h 127.0.0.1 -d scenariogenerator -c "SELECT 1" > /dev/null 2>&1 || {
    "$PG/pg_ctl.exe" -D /f/MoneyCore/pgsql/data -l /f/MoneyCore/pgsql/data/pg.log start > /dev/null 2>&1
    sleep 5
}
rm -f "$LOGS/mc-dmas-member.log" "$LOGS/mc-dmas-mastercard.log"

"$JAVA" -jar "$REPO/sg-mc-dmas-mastercard/target/sg-mc-dmas-mastercard-1.0.0-SNAPSHOT.jar" > /dev/null 2>&1 &
for i in $(seq 1 45); do
    sleep 1
    grep -q "Started SgMcDmasMastercardApplication" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null && break
    [ $i -eq 45 ] && { echo "    ECHEC reseau"; tail -15 "$LOGS/mc-dmas-mastercard.log"; exit 1; }
done
echo "    reseau demarre"

"$JAVA" -jar "$REPO/sg-mc-dmas-member/target/sg-mc-dmas-member-1.0.0-SNAPSHOT.jar" > /dev/null 2>&1 &
for i in $(seq 1 45); do
    sleep 1
    grep -q "Started SgMcDmasMemberApplication" "$LOGS/mc-dmas-member.log" 2>/dev/null && break
    [ $i -eq 45 ] && { echo "    ECHEC membre"; tail -15 "$LOGS/mc-dmas-member.log"; exit 1; }
done
echo "    membre demarre"
sleep 2

echo ""
echo "=== [2/7] Authentification ==="
TOK_M=$(curl -s -X POST http://localhost:8084/auth/login -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
TOK_S=$(curl -s -X POST http://localhost:8501/auth/login -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -z "$TOK_M" ] && { echo "    ECHEC"; exit 1; }
echo "    tokens obtenus"

echo ""
echo "=== [3/7] Bootstrap de la KEK des deux cotes ==="
curl -s -X POST http://localhost:8084/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_M" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEK\"}"
echo ""
curl -s -X POST http://localhost:8501/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_S" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEK\"}"
echo ""

echo ""
echo "=== [4/7] Purge des PEK existantes ==="
"$PG/psql.exe" -U postgres -h 127.0.0.1 -d scenariogenerator -q -c "
DELETE FROM mc_dmas_member_keys     WHERE key_type='PEK';
DELETE FROM mc_dmas_mastercard_keys WHERE key_type='PEK';"
echo "    table nettoyee — on part de zero"

echo ""
echo "=== [5/7] Sign-on ==="
curl -s -X POST http://localhost:8084/api/admin/dmas/network/signon \
    -H "Authorization: Bearer $TOK_M"
echo ""

echo ""
echo "=== [6/7] Sollicitation d'echange de cle (0800 DE70=162) ==="
curl -s -X POST "http://localhost:8084/api/admin/dmas/keys/solicit?memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_M"
echo ""
echo ""
echo "    attente de la livraison et de l'acquittement..."
sleep 5

echo ""
echo "=== [7/7] RESULTAT ==="
echo ""
echo "--- Cle cote MEMBRE ---"
curl -s "http://localhost:8084/api/admin/dmas/keys/current?memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_M"
echo ""
echo ""
echo "--- Cle cote RESEAU ---"
curl -s "http://localhost:8501/api/admin/dmas/keys/current?memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_S"
echo ""

echo ""
echo "--- EN BASE ---"
"$PG/psql.exe" -U postgres -h 127.0.0.1 -d scenariogenerator -c "
SELECT 'membre' AS cote, key_type, kcv, status, key_length
FROM mc_dmas_member_keys WHERE key_type='PEK'
UNION ALL
SELECT 'reseau', key_type, kcv, status, key_length
FROM mc_dmas_mastercard_keys WHERE key_type='PEK'
ORDER BY 1;"

echo ""
echo "--- ENCHAINEMENT DES 5 ETAPES (membre) ---"
grep "DMAS-KEX\|JPOS-CLI" "$LOGS/mc-dmas-member.log" 2>/dev/null | tail -14 | sed 's|^|  |'
echo ""
echo "--- ENCHAINEMENT DES 5 ETAPES (reseau) ---"
grep "DMAS-KEXS\|JPOS-SRV\|DMAS-ISS" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null | tail -14 | sed 's|^|  |'

echo ""
echo "============================================================"
echo "  Les deux KCV doivent etre IDENTIQUES et le statut ACTIVE"
echo "  cote membre (RECEIVED signifierait que le 0820 n'est pas"
echo "  arrive)."
echo "============================================================"
