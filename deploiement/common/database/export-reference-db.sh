#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../runtime/platform-env.sh
source "$SCRIPT_DIR/../runtime/platform-env.sh"

PG_DUMP="${PG_DUMP:-$POSTGRES_HOME/bin/pg_dump.exe}"
TRANSFER_DIR="${DB_TRANSFER_DIR:-$ROOT/runtime/database-transfer}"
TRANSFER_FILE="${DB_TRANSFER_FILE:-$TRANSFER_DIR/${DB_NAME}-reference-$(date +%Y%m%d-%H%M%S).dump}"

[[ -n "$DB_PASSWORD" ]] || {
  read -r -s -p "DB_PASSWORD PostgreSQL source : " DB_PASSWORD
  echo
  export DB_PASSWORD
}
[[ -x "$PG_DUMP" ]] || {
  echo "[FAIL] pg_dump introuvable : $PG_DUMP" >&2
  exit 1
}

mkdir -p "$TRANSFER_DIR"
PGPASSWORD="$DB_PASSWORD" "$PG_DUMP" \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --username="$DB_USER" \
  --format=custom \
  --compress=9 \
  --no-owner \
  --no-acl \
  --file="$TRANSFER_FILE" \
  "$DB_NAME"

echo "[OK] Export créé : $TRANSFER_FILE"
echo "Copier ce fichier de manière sécurisée sur le poste cible."
echo "Ne pas ajouter le fichier .dump dans Git."
