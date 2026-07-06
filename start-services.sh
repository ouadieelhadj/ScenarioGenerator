#!/usr/bin/env bash
# =====================================================================
# start-services.sh
# Lance les 3 fat JAR (orchestrateur, acquereur, issuer) avec Java 21.
# A lancer depuis la racine du projet (/d/MoneyCore/ScenarioGenerator).
# Les logs vont dans ./run-logs/, les PID dans ./run-logs/*.pid
# Pour arreter : ./stop-services.sh
# =====================================================================
set -u

# --- Configuration ---
JAVA21="${JAVA21:-/d/MoneyCore/jdk-21.0.11/bin/java.exe}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGDIR="$ROOT/run-logs"
mkdir -p "$LOGDIR"

JAR_ORC="$ROOT/sg-generator-orchestrator/target/sg-generator-orchestrator-1.0.0-SNAPSHOT.jar"
JAR_ACQ="$ROOT/sg-dmas-acquirer/target/sg-dmas-acquirer-1.0.0-SNAPSHOT.jar"
JAR_ISS="$ROOT/sg-dmas-issuer/target/sg-dmas-issuer-1.0.0-SNAPSHOT.jar"

# service : nom | jar | port REST de health-check
SERVICES=(
  "acquereur|$JAR_ACQ|8084"
  "issuer|$JAR_ISS|8501"
  "orchestrateur|$JAR_ORC|8080"
)

echo "=== Verification Java 21 ==="
if [ ! -f "$JAVA21" ]; then
  echo "  ERREUR : Java 21 introuvable a $JAVA21"
  echo "  Definir la variable JAVA21 vers le bon chemin et relancer."
  exit 1
fi
"$JAVA21" -version 2>&1 | head -1

echo ""
echo "=== Verification des JAR ==="
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r name jar port <<< "$entry"
  if [ ! -f "$jar" ]; then
    echo "  ERREUR : JAR manquant pour $name : $jar"
    echo "  Lancer d'abord : mvn clean package -DskipTests -pl sg-common,sg-dmas-issuer,sg-dmas-acquirer,sg-generator-orchestrator -am"
    exit 1
  fi
  echo "  OK $name : $(basename "$jar")"
done

# --- Demarrage ---
# Ordre : acquereur (serveur jPOS) d'abord, puis issuer (client), puis orchestrateur.
echo ""
echo "=== Demarrage des services ==="
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r name jar port <<< "$entry"
  log="$LOGDIR/$name.log"
  pidfile="$LOGDIR/$name.pid"
  echo "  -> $name (port $port)..."
  "$JAVA21" -jar "$jar" > "$log" 2>&1 &
  echo $! > "$pidfile"
  sleep 2   # petit decalage entre demarrages
done

# --- Attente que chaque service reponde ---
echo ""
echo "=== Attente du demarrage (max 90s par service) ==="
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r name jar port <<< "$entry"
  echo -n "  $name (port $port) "
  ok=0
  for i in $(seq 1 45); do
    code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/api/status" 2>/dev/null)
    # 200 = ouvert, 403 = vivant mais protege : les deux signifient "demarre"
    if [ "$code" = "200" ] || [ "$code" = "403" ]; then ok=1; break; fi
    echo -n "."
    sleep 2
  done
  if [ "$ok" = "1" ]; then echo " DEMARRE (HTTP $code)"; else echo " TIMEOUT - voir $LOGDIR/$name.log"; fi
done

echo ""
echo "=== Etat ==="
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r name jar port <<< "$entry"
  pid=$(cat "$LOGDIR/$name.pid" 2>/dev/null)
  echo "  $name : PID $pid, log $LOGDIR/$name.log"
done
echo ""
echo "Pour arreter : ./stop-services.sh"
echo "Pour le scenario : ./scenario-e2e.sh"
