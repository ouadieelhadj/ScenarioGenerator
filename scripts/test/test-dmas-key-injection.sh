#!/usr/bin/env bash
# ============================================================
#  test-key-injection.sh — LOT A
#
#  Injecte la MEME PEK des deux cotes et verifie que les KCV
#  concordent : c'est la preuve que les deux detiennent bien la
#  meme cle, sans etre passes par le reseau.
# ============================================================
REPO="/f/ScenarioGenerator"
LOGS="$REPO/logs"
PG="/f/MoneyCore/pgsql/bin"
export JAVA_HOME="/f/MoneyCore/jdk-26_windows-x64_bin/jdk-26.0.1"
JAVA="$JAVA_HOME/bin/java.exe"

LOGIN="${1:-admin}"
PASSWORD="${2:-Admin123!}"

# KEK et PEK de test
KEK="13AED5DA1F32347523C708C11F2608FD"
PEK="BC4AEA2F5BB3FD1504624F8623835D5B"
MGID="TESTGRP01"

echo "=== [1/6] Arret et redemarrage ==="
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
    [ $i -eq 45 ] && { echo "    ECHEC mastercard"; tail -15 "$LOGS/mc-dmas-mastercard.log"; exit 1; }
done
echo "    mastercard demarre"

"$JAVA" -jar "$REPO/sg-mc-dmas-member/target/sg-mc-dmas-member-1.0.0-SNAPSHOT.jar" > /dev/null 2>&1 &
for i in $(seq 1 45); do
    sleep 1
    grep -q "Started SgMcDmasMemberApplication" "$LOGS/mc-dmas-member.log" 2>/dev/null && break
    [ $i -eq 45 ] && { echo "    ECHEC member"; tail -15 "$LOGS/mc-dmas-member.log"; exit 1; }
done
echo "    member demarre"
sleep 2

echo ""
echo "=== [2/6] Authentification ==="
TOK_M=$(curl -s -X POST http://localhost:8084/auth/login -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
TOK_S=$(curl -s -X POST http://localhost:8501/auth/login -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -z "$TOK_M" ] && { echo "    ECHEC auth membre"; exit 1; }
echo "    tokens obtenus"

echo ""
echo "=== [3/6] Bootstrap de la KEK des deux cotes ==="
echo "--- membre ---"
curl -s -X POST http://localhost:8084/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_M" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEK\"}"
echo ""
echo "--- reseau ---"
curl -s -X POST http://localhost:8501/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_S" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEK\"}"
echo ""

echo ""
echo "=== [4/6] Injection de la MEME PEK des deux cotes ==="
echo "    PEK en clair : $PEK"
echo ""
echo "--- membre ---"
curl -s -X POST "http://localhost:8084/api/admin/dmas/keys/inject?clear=$PEK&memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_M"
echo ""
echo "--- reseau ---"
curl -s -X POST "http://localhost:8501/api/admin/dmas/keys/inject?clear=$PEK&memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_S"
echo ""

echo ""
echo "=== [5/6] Etat des cles ==="
echo "--- membre ---"
curl -s "http://localhost:8084/api/admin/dmas/keys/current?memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_M"
echo ""
echo "--- reseau ---"
curl -s "http://localhost:8501/api/admin/dmas/keys/current?memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_S"
echo ""

echo ""
echo "=== [6/6] Verification en base ==="
"$PG/psql.exe" -U postgres -h 127.0.0.1 -d scenariogenerator -c "
SELECT 'membre' AS cote, member_group_id, key_type, kcv, status, key_length
FROM mc_dmas_member_keys WHERE status='ACTIVE'
UNION ALL
SELECT 'reseau', member_group_id, key_type, kcv, status, key_length
FROM mc_dmas_mastercard_keys WHERE status='ACTIVE';"

echo ""
echo "Les deux KCV doivent etre IDENTIQUES : meme cle des deux cotes."
