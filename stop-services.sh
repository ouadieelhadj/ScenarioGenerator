#!/usr/bin/env bash
# =====================================================================
# stop-services.sh
# Arrete les 3 services lances par start-services.sh (via les .pid).
# =====================================================================
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGDIR="$ROOT/run-logs"

for name in orchestrateur issuer acquereur; do
  pidfile="$LOGDIR/$name.pid"
  if [ -f "$pidfile" ]; then
    pid=$(cat "$pidfile")
    if kill -0 "$pid" 2>/dev/null; then
      echo "  arret $name (PID $pid)..."
      # tuer l'arbre de process (Windows : taskkill ; sinon kill)
      if command -v taskkill >/dev/null 2>&1; then
        taskkill //PID "$pid" //T //F >/dev/null 2>&1
      else
        kill "$pid" 2>/dev/null; sleep 1; kill -9 "$pid" 2>/dev/null
      fi
    else
      echo "  $name deja arrete"
    fi
    rm -f "$pidfile"
  else
    echo "  $name : pas de PID enregistre"
  fi
done
echo "Termine."
