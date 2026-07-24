#!/usr/bin/env bash
# ============================================================
#  test-keyexchange.sh
#  Relance les modules et deroule l'echange de cles 162.
# ============================================================
REPO="/f/ScenarioGenerator"
LOGS="$REPO/logs"
export JAVA_HOME="/f/MoneyCore/jdk-26_windows-x64_bin/jdk-26.0.1"
JAVA="$JAVA_HOME/bin/java.exe"
ZMK="13AED5DA1F32347523C708C11F2608FD"

echo "=== [1/7] PostgreSQL ==="
export PGPASSWORD=postgres123
if /f/MoneyCore/pgsql/bin/psql.exe -U postgres -h 127.0.0.1 -d scenariogenerator -c "SELECT 1" > /dev/null 2>&1; then
    echo "    deja demarre"
else
    /f/MoneyCore/pgsql/bin/pg_ctl.exe -D /f/MoneyCore/pgsql/data -l /f/MoneyCore/pgsql/data/pg.log start > /dev/null 2>&1
    sleep 5
    echo "    demarre"
fi

echo "=== [2/7] Arret des process ==="
taskkill //F //IM java.exe > /dev/null 2>&1
sleep 2

echo "=== [3/7] Purge des logs ==="
mkdir -p "$LOGS"
rm -f "$LOGS/mc-sms-acquirer.log" "$LOGS/mc-sms-issuer.log"

echo "=== [4/7] Demarrage ISSUER ==="
"$JAVA" -jar "$REPO/sg-mc-sms-issuer/target/sg-mc-sms-issuer-1.0.0-SNAPSHOT.jar" > /dev/null 2>&1 &
for i in $(seq 1 40); do
    sleep 1
    grep -q "Started SgMcSmsIssuerApplication" "$LOGS/mc-sms-issuer.log" 2>/dev/null && { echo "    demarre ($i s)"; break; }
    [ $i -eq 40 ] && { echo "    ECHEC"; tail -20 "$LOGS/mc-sms-issuer.log"; exit 1; }
done

echo "=== [5/7] Demarrage ACQUEREUR ==="
"$JAVA" -jar "$REPO/sg-mc-sms-acquirer/target/sg-mc-sms-acquirer-1.0.0-SNAPSHOT.jar" > /dev/null 2>&1 &
for i in $(seq 1 40); do
    sleep 1
    grep -q "Started SgMcSmsAcquirerApplication" "$LOGS/mc-sms-acquirer.log" 2>/dev/null && { echo "    demarre ($i s)"; break; }
    [ $i -eq 40 ] && { echo "    ECHEC"; tail -20 "$LOGS/mc-sms-acquirer.log"; exit 1; }
done
sleep 2

echo ""
echo "=== [6/7] Bootstrap ZMK ==="
curl -s -X POST "http://localhost:8095/api/admin/mc/keys/bootstrap-zmk?zmk=$ZMK"
echo ""

echo ""
echo "--- Sign-on ---"
curl -s -X POST http://localhost:8095/api/admin/mc/network/signon
echo ""

echo ""
echo "--- Sollicitation de cle (0800 DE70=162) ---"
curl -s -X POST http://localhost:8095/api/admin/mc/keys/solicit
echo ""

echo ""
echo "    attente de la livraison et de l'acquittement..."
sleep 4

echo ""
echo "=== [7/7] RESULTAT ==="
echo ""
echo "--- Cle cote MEMBRE ---"
curl -s http://localhost:8095/api/admin/mc/keys/current
echo ""
echo ""
echo "--- Cle cote SIMULATEUR ---"
curl -s http://localhost:8097/api/admin/mc/sim/last-key
echo ""

echo ""
echo "=== LOGS ACQUEREUR ==="
grep "MC-KEX\|MC-CLI" "$LOGS/mc-sms-acquirer.log" 2>/dev/null | tail -20

echo ""
echo "=== LOGS ISSUER ==="
grep "MC-KEX-SIM\|MC-SRV" "$LOGS/mc-sms-issuer.log" 2>/dev/null | tail -20
