#!/usr/bin/env bash
set -euo pipefail

export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../common/runtime/platform-env.sh
source "$SCRIPT_DIR/../common/runtime/platform-env.sh"
FRONTEND_PORT="${FRONTEND_PORT:-4200}"

cd "$ROOT/sg-frontend"

command -v node >/dev/null 2>&1 || {
  echo "[ERREUR] Node.js introuvable. NODE_HOME=$NODE_HOME" >&2
  exit 1
}
[[ -f "$NPM" ]] || {
  echo "[ERREUR] npm introuvable." >&2
  exit 1
}

if [[ ! -d node_modules ]]; then
  echo "[INFO] Installation des dependances Angular..."
  "$NPM" ci
fi

echo "[INFO] Frontend : http://localhost:$FRONTEND_PORT"
echo "[INFO] Backend attendu : http://localhost:8080"
echo "[INFO] Arret : Ctrl+C"
exec "$NPM" start -- --host 0.0.0.0 --port "$FRONTEND_PORT"
