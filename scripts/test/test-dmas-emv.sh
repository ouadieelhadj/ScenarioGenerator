#!/usr/bin/env bash
# ============================================================
#  test-dmas-emv.sh — CHAINE 0100 EMV DE BOUT EN BOUT
#
#  1. demarre reseau + membre (couple A)
#  2. sign-on, KEK, PEK (echange 162)
#  3. MDK des DEUX cotes (membre construit, reseau valide)
#  4. un 0100 avec DE55 via buildAuth0100WithEmv
#  5. verifie [EMV] cote membre et [EMV-VAL] match=true cote reseau
# ============================================================
REPO="/f/ScenarioGenerator"
LOGS="$REPO/logs"
PG="/f/MoneyCore/pgsql/bin"
export JAVA_HOME="/f/MoneyCore/jdk-26_windows-x64_bin/jdk-26.0.1"
JAVA="$JAVA_HOME/bin/java.exe"

LOGIN="${1:-admin}"; PASSWORD="${2:-Admin123!}"
KEK="13AED5DA1F32347523C708C11F2608FD"
MDK="6E46FE409DF704BCA75E7FF270B65E73"     # KCV attendu 944A44
MGID="TESTGRP01"; BANK="022905"

export PGPASSWORD=postgres123
PSQL="$PG/psql.exe -U postgres -h 127.0.0.1 -d scenariogenerator"
MEMBER_JAR="$REPO/sg-mc-dmas-member/target/sg-mc-dmas-member-1.0.0-SNAPSHOT.jar"
MASTER_JAR="$REPO/sg-mc-dmas-mastercard/target/sg-mc-dmas-mastercard-1.0.0-SNAPSHOT.jar"

tok() { curl -s -X POST "http://localhost:$1/auth/login" -H "Content-Type: application/json" \
        -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
        | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'; }

echo "=== [1/7] Redemarrage ==="
taskkill //F //IM java.exe > /dev/null 2>&1; sleep 3
$PSQL -c "SELECT 1" > /dev/null 2>&1 || {
    "$PG/pg_ctl.exe" -D /f/MoneyCore/pgsql/data -l /f/MoneyCore/pgsql/data/pg.log start > /dev/null 2>&1; sleep 5; }
rm -f "$LOGS/mc-dmas-member.log" "$LOGS/mc-dmas-mastercard.log"
# repartir de zero sur les cles PEK et MDK reseau
$PSQL -q -c "DELETE FROM mc_dmas_member_keys WHERE key_type IN ('PEK');
             DELETE FROM mc_dmas_mastercard_keys WHERE key_type IN ('PEK','MDK');
             UPDATE mc_dmas_cards SET emv_atc=0 WHERE bank_code='$BANK';" > /dev/null 2>&1

"$JAVA" -jar "$MASTER_JAR" --sg.interface=DMAS_MASTERCARD_1 > /dev/null 2>&1 &
for i in $(seq 1 45); do sleep 1; grep -q "Started SgMcDmasMastercard" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null && break; done
echo "    reseau demarre"
"$JAVA" -jar "$MEMBER_JAR" --sg.interface=DMAS_BANK_A > /dev/null 2>&1 &
for i in $(seq 1 45); do sleep 1; grep -q "Started SgMcDmasMember" "$LOGS/mc-dmas-member.log" 2>/dev/null && break; done
echo "    membre demarre"
sleep 2

TOK_M=$(tok 8084); TOK_S=$(tok 8501)
[ -z "$TOK_M" ] && { echo "    ECHEC auth"; exit 1; }
echo "    tokens obtenus"

echo ""
echo "=== [2/7] Sign-on ==="
curl -s -X POST http://localhost:8084/api/admin/dmas/network/signon -H "Authorization: Bearer $TOK_M"; echo ""

echo ""
echo "=== [3/7] KEK des deux cotes ==="
curl -s -X POST http://localhost:8084/api/admin/dmas/kek/bootstrap -H "Authorization: Bearer $TOK_M" \
    -H "Content-Type: application/json" -d "{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEK\"}" > /dev/null
curl -s -X POST http://localhost:8501/api/admin/dmas/kek/bootstrap -H "Authorization: Bearer $TOK_S" \
    -H "Content-Type: application/json" -d "{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEK\"}" > /dev/null
echo "    posee"

echo ""
echo "=== [4/7] PEK (echange 162) ==="
curl -s -X POST "http://localhost:8084/api/admin/dmas/keys/solicit?bank=$BANK" -H "Authorization: Bearer $TOK_M"; echo ""
sleep 5

echo ""
echo "=== [5/7] MDK des deux cotes (KCV attendu 944A44) ==="
echo -n "    membre : "
curl -s -X POST http://localhost:8084/api/admin/dmas/mdk/bootstrap -H "Authorization: Bearer $TOK_M" \
    -H "Content-Type: application/json" -d "{\"mdkClear\":\"$MDK\",\"bank\":\"$BANK\"}" \
    | sed -n 's/.*"kcv":"\([^"]*\)".*/\1/p'
echo -n "    reseau : "
curl -s -X POST http://localhost:8501/api/admin/dmas/mdk/bootstrap -H "Authorization: Bearer $TOK_S" \
    -H "Content-Type: application/json" -d "{\"mdkClear\":\"$MDK\",\"bank\":\"$BANK\"}" \
    | sed -n 's/.*"kcv":"\([^"]*\)".*/\1/p'

echo ""
echo "=== [6/7] Un 0100 avec DE55 EMV ==="
# Carte de la banque A
PAN=$($PSQL -tAc "SELECT pan FROM mc_dmas_cards WHERE bank_code='$BANK' AND status='ACTIVE' LIMIT 1;" | tr -d ' \r\n')
echo "    carte : $PAN"
# loadtest 1 tx avec withEmv
curl -s -X POST "http://localhost:8084/api/admin/dmas/loadtest" -H "Authorization: Bearer $TOK_M" \
    -H "Content-Type: application/json" \
    -d "{\"pan\":\"$PAN\",\"amount\":\"000000050200\",\"count\":1,\"concurrency\":1,\"withEmv\":true,\"mti\":\"0100\"}"
echo ""
sleep 5

echo ""
echo "=== [7/7] RESULTAT ==="
echo ""
echo "--- construction, cote membre ---"
grep -E "\[EMV\]|DE55 construit" "$LOGS/mc-dmas-member.log" 2>/dev/null | tail -4 | sed 's|^.*: |  |'
echo ""
echo "--- validation, cote reseau ---"
grep -E "\[EMV-VAL\]" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null | tail -6 | sed 's|^.*: |  |'
echo ""
echo "--- ATC incremente ? ---"
$PSQL -c "SELECT pan, emv_atc FROM mc_dmas_cards WHERE pan='$PAN';"
echo ""
if grep -q "match=true" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null; then
    echo "  >>> ARQC VALIDE — chaine EMV complete et coherente"
else
    echo "  >>> ARQC non valide ou non journalise — voir les logs ci-dessus"
fi
