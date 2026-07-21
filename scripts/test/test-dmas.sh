#!/usr/bin/env bash
# ============================================================
#  test-dmas.sh — test complet DMAS apres inversion
#
#  Enchaine : arret des process, purge des logs, PostgreSQL,
#  demarrage mastercard (serveur) puis member (client), sign-on.
#
#  Verifie que la liaison PERMANENTE est utilisee :
#    [JPOS-CLI] = permanent   (attendu)
#    [DMAS-ACQ] = ephemere    (ne doit PAS apparaitre)
# ============================================================
REPO="/f/ScenarioGenerator"
LOGS="$REPO/logs"
PG="/f/MoneyCore/pgsql/bin"
export JAVA_HOME="/f/MoneyCore/jdk-26_windows-x64_bin/jdk-26.0.1"
JAVA="$JAVA_HOME/bin/java.exe"

MEMBER_JAR="$REPO/sg-mc-dmas-member/target/sg-mc-dmas-member-1.0.0-SNAPSHOT.jar"
MASTER_JAR="$REPO/sg-mc-dmas-mastercard/target/sg-mc-dmas-mastercard-1.0.0-SNAPSHOT.jar"

LOGIN="${1:-admin}"
PASSWORD="${2:-Admin123!}"

echo "=== [1/7] Arret des process Java ==="
taskkill //F //IM java.exe > /dev/null 2>&1
sleep 3
echo "    OK"

echo ""
echo "=== [2/7] Ports liberes ? ==="
echo "    8084 REST membre  8500 ISO reseau  8501 REST reseau  8600 ex-ISO membre"
BUSY=$(netstat -ano 2>/dev/null | grep -E ":(8500|8084|8501|8600)[[:space:]]" | grep LISTENING || true)
if [ -n "$BUSY" ]; then
    echo "    ENCORE OCCUPES :"
    echo "$BUSY" | sed 's|^|      |'
    echo "    -> tuer manuellement le PID de la derniere colonne"
    exit 1
fi
echo "    libres"

echo ""
echo "=== [3/7] PostgreSQL ==="
export PGPASSWORD=postgres123
if "$PG/psql.exe" -U postgres -h 127.0.0.1 -d scenariogenerator -c "SELECT 1" > /dev/null 2>&1; then
    echo "    deja demarre"
else
    "$PG/pg_ctl.exe" -D /f/MoneyCore/pgsql/data -l /f/MoneyCore/pgsql/data/pg.log start > /dev/null 2>&1
    sleep 5
    "$PG/psql.exe" -U postgres -h 127.0.0.1 -d scenariogenerator -c "SELECT 1" > /dev/null 2>&1 \
        && echo "    demarre" || { echo "    ECHEC"; exit 1; }
fi

echo ""
echo "=== [4/7] Purge des logs ==="
mkdir -p "$LOGS"
rm -f "$LOGS/mc-dmas-member.log" "$LOGS/mc-dmas-mastercard.log"
echo "    OK"

echo ""
echo "=== [5/7] Demarrage MASTERCARD (serveur) ==="
"$JAVA" -jar "$MASTER_JAR" > /dev/null 2>&1 &
for i in $(seq 1 45); do
    sleep 1
    if grep -q "Started SgMcDmasMastercardApplication" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null; then
        echo "    demarre ($i s)"
        break
    fi
    if [ $i -eq 45 ]; then
        echo "    ECHEC — 20 dernieres lignes :"
        tail -20 "$LOGS/mc-dmas-mastercard.log" 2>/dev/null | sed 's|^|      |'
        exit 1
    fi
done
# Controle du conflit de port
if grep -q "Address already in use" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null; then
    echo "    !!! CONFLIT DE PORT detecte :"
    grep "Address already in use" "$LOGS/mc-dmas-mastercard.log" | sed 's|^|      |'
    exit 1
fi
grep "ISOServer demarre" "$LOGS/mc-dmas-mastercard.log" | sed 's|^|    |'

echo ""
echo "=== [6/7] Demarrage MEMBER (client) ==="
"$JAVA" -jar "$MEMBER_JAR" > /dev/null 2>&1 &
for i in $(seq 1 45); do
    sleep 1
    if grep -q "Started SgMcDmasMemberApplication" "$LOGS/mc-dmas-member.log" 2>/dev/null; then
        echo "    demarre ($i s)"
        break
    fi
    if [ $i -eq 45 ]; then
        echo "    ECHEC — 20 dernieres lignes :"
        tail -20 "$LOGS/mc-dmas-member.log" 2>/dev/null | sed 's|^|      |'
        exit 1
    fi
done
sleep 2

echo ""
echo "=== [7/7] Authentification et sign-on ==="
RESP=$(curl -s -X POST http://localhost:8084/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$RESP" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
    echo "    ECHEC de l'authentification pour '$LOGIN'"
    echo "    reponse : $RESP"
    echo ""
    echo "    Relancer avec : bash $0 <login> <password>"
    exit 1
fi
echo "    token obtenu (${#TOKEN} caracteres)"

echo ""
echo "--- SIGN-ON ---"
curl -s -X POST http://localhost:8084/api/admin/dmas/network/signon \
    -H "Authorization: Bearer $TOKEN"
echo ""

echo ""
echo "--- STATUT MEMBRE ---"
curl -s http://localhost:8084/api/admin/dmas/network/status \
    -H "Authorization: Bearer $TOKEN"
echo ""

echo ""
echo "--- STATUT RESEAU ---"
curl -s http://localhost:8501/api/admin/dmas/jpos/status \
    -H "Authorization: Bearer $TOKEN"
echo ""

# ============================================================
echo ""
echo "============================================================"
echo "  RESULTAT"
echo "============================================================"
echo ""
echo "--- LOGS MEMBRE ---"
grep "JPOS-CLI\|DMAS-ACQ\|ERROR" "$LOGS/mc-dmas-member.log" 2>/dev/null | tail -12 | sed 's|^|  |'
echo ""
echo "--- LOGS RESEAU ---"
grep "JPOS-SRV\|DMAS-ISS\|ERROR" "$LOGS/mc-dmas-mastercard.log" 2>/dev/null | tail -12 | sed 's|^|  |'

echo ""
echo "--- QUEL MECANISME ? ---"
NB_PERM=$(grep -c "JPOS-CLI" "$LOGS/mc-dmas-member.log" 2>/dev/null | head -1)
NB_EPH=$(grep -c "DMAS-ACQ" "$LOGS/mc-dmas-member.log" 2>/dev/null | head -1)
NB_PERM=${NB_PERM:-0}
NB_EPH=${NB_EPH:-0}
printf "  liaison permanente [JPOS-CLI] : %s ligne(s)\n" "$NB_PERM"
printf "  connexion ephemere [DMAS-ACQ] : %s ligne(s)\n" "$NB_EPH"
if [ "$NB_EPH" -gt 0 ]; then
    echo "  !!! le mecanisme ephemere a ete utilise"
else
    echo "  OK — seule la liaison permanente a servi"
fi

echo ""
echo "--- SOCKETS OUVERTES SUR 8500 ---"
netstat -ano 2>/dev/null | grep ":8500" | sed 's|^|  |' || echo "  aucune"

echo ""
echo "--- PORT 8600 (ex-ISO membre, doit etre LIBRE) ---"
if netstat -ano 2>/dev/null | grep ":8600" | grep -q LISTENING; then
    echo "  !!! encore ecoute — un serveur subsiste cote membre"
    netstat -ano 2>/dev/null | grep ":8600" | sed 's|^|  |'
else
    echo "  OK — plus personne n'ecoute (inversion effective)"
fi
