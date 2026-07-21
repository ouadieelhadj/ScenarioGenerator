#!/usr/bin/env bash
# ============================================================
#  test-key-162.sh — LES TROIS MECANISMES D'ECHANGE DE CLE DMAS
#
#  Enchaine, en repartant d'une base vide a chaque fois :
#
#    1. SOLLICITATION 162    le membre demande, le reseau livre
#    2. SYSTEM GENERATED     le reseau livre spontanement (24 h)
#    3. INJECTION MANUELLE   par REST, sans reseau
#
#  Les trois doivent aboutir a la MEME cle des deux cotes.
# ============================================================
REPO="/f/ScenarioGenerator"
LOGS="$REPO/logs"
PG="/f/MoneyCore/pgsql/bin"
export JAVA_HOME="/f/MoneyCore/jdk-26_windows-x64_bin/jdk-26.0.1"
JAVA="$JAVA_HOME/bin/java.exe"

LOGIN="${1:-admin}"
PASSWORD="${2:-Admin123!}"
KEK="13AED5DA1F32347523C708C11F2608FD"
PEK_MANUELLE="BC4AEA2F5BB3FD1504624F8623835D5B"
MGID="TESTGRP01"

export PGPASSWORD=postgres123
PSQL="$PG/psql.exe -U postgres -h 127.0.0.1 -d scenariogenerator"

# ------------------------------------------------------------
purge_pek() {
    $PSQL -q -c "
DELETE FROM mc_dmas_member_keys     WHERE key_type='PEK';
DELETE FROM mc_dmas_mastercard_keys WHERE key_type='PEK';" > /dev/null 2>&1
}

etat_pek() {
    $PSQL -tAc "
SELECT 'membre ' || COALESCE(kcv,'-') || ' ' || COALESCE(status,'-')
FROM mc_dmas_member_keys WHERE key_type='PEK'
UNION ALL
SELECT 'reseau ' || COALESCE(kcv,'-') || ' ' || COALESCE(status,'-')
FROM mc_dmas_mastercard_keys WHERE key_type='PEK'
ORDER BY 1;" 2>/dev/null | sed 's|^|      |'
}

verdict() {
    local n
    n=$($PSQL -tAc "
SELECT count(DISTINCT kcv) FROM (
  SELECT kcv FROM mc_dmas_member_keys     WHERE key_type='PEK' AND status='ACTIVE'
  UNION ALL
  SELECT kcv FROM mc_dmas_mastercard_keys WHERE key_type='PEK' AND status='ACTIVE'
) t;" 2>/dev/null | tr -d ' \r\n')
    local c
    c=$($PSQL -tAc "
SELECT count(*) FROM (
  SELECT kcv FROM mc_dmas_member_keys     WHERE key_type='PEK' AND status='ACTIVE'
  UNION ALL
  SELECT kcv FROM mc_dmas_mastercard_keys WHERE key_type='PEK' AND status='ACTIVE'
) t;" 2>/dev/null | tr -d ' \r\n')
    if [ "$c" = "2" ] && [ "$n" = "1" ]; then
        echo "      >>> OK — meme cle ACTIVE des deux cotes"
    else
        echo "      >>> ECHEC — $c cle(s) ACTIVE, $n KCV distinct(s)"
    fi
}

# ============================================================
echo "=== [1/9] Redemarrage ==="
taskkill //F //IM java.exe > /dev/null 2>&1
sleep 3
$PSQL -c "SELECT 1" > /dev/null 2>&1 || {
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
echo "=== [2/9] Authentification ==="
TOK_M=$(curl -s -X POST http://localhost:8084/auth/login -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
TOK_S=$(curl -s -X POST http://localhost:8501/auth/login -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -z "$TOK_M" ] && { echo "    ECHEC"; exit 1; }
echo "    tokens obtenus"

echo ""
echo "=== [3/9] Bootstrap de la KEK des deux cotes ==="
curl -s -X POST http://localhost:8084/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_M" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEK\"}"
echo ""
curl -s -X POST http://localhost:8501/api/admin/dmas/kek/bootstrap \
    -H "Authorization: Bearer $TOK_S" -H "Content-Type: application/json" \
    -d "{\"memberGroupId\":\"$MGID\",\"kekClear\":\"$KEK\"}"
echo ""

echo ""
echo "=== [4/9] Sign-on ==="
curl -s -X POST http://localhost:8084/api/admin/dmas/network/signon \
    -H "Authorization: Bearer $TOK_M"
echo ""

# ============================================================
echo ""
echo "############################################################"
echo "#  MECANISME 1 : SOLLICITATION 162  (customer generated)"
echo "############################################################"
purge_pek
echo "    PEK purgee — on part de zero"
echo ""
echo "--- 0800 DE70=162 ---"
curl -s -X POST "http://localhost:8084/api/admin/dmas/keys/solicit?memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_M"
echo ""
echo "    attente de la livraison et de l'acquittement..."
sleep 5
echo ""
echo "    Etat des cles :"
etat_pek
verdict
KCV_1=$($PSQL -tAc "SELECT kcv FROM mc_dmas_member_keys WHERE key_type='PEK' AND status='ACTIVE' LIMIT 1;" 2>/dev/null | tr -d ' \r\n')

# ============================================================
echo ""
echo "############################################################"
echo "#  MECANISME 2 : SYSTEM GENERATED  (le reseau pousse)"
echo "############################################################"
purge_pek
echo "    PEK purgee — on part de zero"
echo ""
echo "--- POST /jpos/push/pek ---"
curl -s -X POST "http://localhost:8501/api/admin/dmas/jpos/push/pek" \
    -H "Authorization: Bearer $TOK_S"
echo ""
echo "    attente de la livraison et de l'acquittement..."
sleep 5
echo ""
echo "    Etat des cles :"
etat_pek
verdict
KCV_2=$($PSQL -tAc "SELECT kcv FROM mc_dmas_member_keys WHERE key_type='PEK' AND status='ACTIVE' LIMIT 1;" 2>/dev/null | tr -d ' \r\n')

# ============================================================
echo ""
echo "############################################################"
echo "#  MECANISME 3 : INJECTION MANUELLE  (sans reseau)"
echo "############################################################"
purge_pek
echo "    PEK purgee — on part de zero"
echo ""
echo "--- membre ---"
curl -s -X POST "http://localhost:8084/api/admin/dmas/keys/inject?clear=$PEK_MANUELLE&memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_M"
echo ""
echo "--- reseau ---"
curl -s -X POST "http://localhost:8501/api/admin/dmas/keys/inject?clear=$PEK_MANUELLE&memberGroupId=$MGID" \
    -H "Authorization: Bearer $TOK_S"
echo ""
sleep 1
echo ""
echo "    Etat des cles :"
etat_pek
verdict
KCV_3=$($PSQL -tAc "SELECT kcv FROM mc_dmas_member_keys WHERE key_type='PEK' AND status='ACTIVE' LIMIT 1;" 2>/dev/null | tr -d ' \r\n')

# ============================================================
echo ""
echo "############################################################"
echo "#  RECAPITULATIF"
echo "############################################################"
printf "  1. sollicitation 162   KCV=%s\n" "${KCV_1:-ECHEC}"
printf "  2. system generated    KCV=%s\n" "${KCV_2:-ECHEC}"
printf "  3. injection manuelle  KCV=%s\n" "${KCV_3:-ECHEC}"
echo ""
echo "  Les KCV 1 et 2 sont differents a chaque execution : la cle est"
echo "  generee aleatoirement par le reseau. Le KCV 3 est toujours"
echo "  43A186, puisque la cle injectee est fixe."

echo ""
echo "--- ENCHAINEMENT (membre) ---"
grep "DMAS-KEX\]" "$LOGS/mc-dmas-member.log" 2>/dev/null | tail -12 | sed 's|^|  |'
echo ""
echo "--- ENCHAINEMENT (reseau) ---"
grep "DMAS-KEXS\]" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null | tail -12 | sed 's|^|  |'
