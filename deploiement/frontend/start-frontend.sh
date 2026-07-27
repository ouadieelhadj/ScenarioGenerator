#!/usr/bin/env bash
set -euo pipefail

ROOT="${ROOT:-/d/MoneyCore/ScenarioGenerator}"
NODE_HOME="${NODE_HOME:-/d/MoneyCore/nodejs}"
FRONTEND_PORT="${FRONTEND_PORT:-4200}"

export PATH="$NODE_HOME:$PATH"
cd "$ROOT/sg-frontend"

command -v node >/dev/null 2>&1 || {
  echo "[ERREUR] Node.js introuvable. NODE_HOME=$NODE_HOME" >&2
  exit 1
}
command -v npm >/dev/null 2>&1 || {
  echo "[ERREUR] npm introuvable." >&2
  exit 1
}

if [[ ! -d node_modules ]]; then
  echo "[INFO] Installation des dependances Angular..."
  npm ci
fi

echo "[INFO] Frontend : http://localhost:$FRONTEND_PORT"
echo "[INFO] Backend attendu : http://localhost:8080"
echo "[INFO] Arret : Ctrl+C"
exec npm start -- --host 0.0.0.0 --port "$FRONTEND_PORT"
