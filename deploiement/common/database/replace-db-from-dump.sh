#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../runtime/platform-env.sh
source "$SCRIPT_DIR/../runtime/platform-env.sh"

PG_DUMP="${PG_DUMP:-$POSTGRES_HOME/bin/pg_dump.exe}"
PG_RESTORE="${PG_RESTORE:-$POSTGRES_HOME/bin/pg_restore.exe}"
DUMP_FILE="${1:-${DB_TRANSFER_FILE:-}}"
BACKUP_DIR="${DB_BACKUP_DIR:-$ROOT/runtime/database-backups}"
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}-before-replace-$(date +%Y%m%d-%H%M%S).dump"

[[ -n "$DUMP_FILE" ]] || {
  echo "Usage: bash $0 /chemin/vers/scenariogenerator-reference.dump" >&2
  exit 2
}
[[ -f "$DUMP_FILE" ]] || {
  echo "[FAIL] Dump absent : $DUMP_FILE" >&2
  exit 1
}
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || {
  echo "[FAIL] DB_NAME non sécurisé : $DB_NAME" >&2
  exit 1
}
[[ -n "$DB_PASSWORD" ]] || {
  read -r -s -p "DB_PASSWORD PostgreSQL cible : " DB_PASSWORD
  echo
  export DB_PASSWORD
}
[[ -x "$PG_DUMP" && -x "$PG_RESTORE" && -x "$PSQL" ]] || {
  echo "[FAIL] Outils PostgreSQL introuvables sous POSTGRES_HOME=$POSTGRES_HOME" >&2
  exit 1
}

# Contrôle et démarrage configuré avant confirmation, sauvegarde ou suppression.
bash "$SCRIPT_DIR/check-postgres.sh" --start

echo "ATTENTION : la base cible '$DB_NAME' sur $DB_HOST:$DB_PORT sera remplacée."
echo "Arrêter tous les services applicatifs avant de continuer."
confirmation="${DB_REPLACE_CONFIRMATION:-}"
if [[ -z "$confirmation" ]]; then
  read -r -p "Saisir exactement 'REMPLACER $DB_NAME' : " confirmation
fi
[[ "$confirmation" == "REMPLACER $DB_NAME" ]] || {
  echo "[ANNULÉ] Confirmation incorrecte."
  exit 1
}

mkdir -p "$BACKUP_DIR"
database_exists="$(
  PGPASSWORD="$DB_PASSWORD" "$PSQL" \
    --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" \
    --dbname=postgres --tuples-only --no-align \
    --command="SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'"
)"

if [[ "$database_exists" == "1" ]]; then
  PGPASSWORD="$DB_PASSWORD" "$PG_DUMP" \
    --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" \
    --format=custom --compress=9 --no-owner --no-acl \
    --file="$BACKUP_FILE" "$DB_NAME"
  echo "[OK] Sauvegarde de sécurité : $BACKUP_FILE"
fi

PGPASSWORD="$DB_PASSWORD" "$PSQL" \
  --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" \
  --dbname=postgres --set=ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$DB_NAME'
  AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS "$DB_NAME";
CREATE DATABASE "$DB_NAME";
SQL

PGPASSWORD="$DB_PASSWORD" "$PG_RESTORE" \
  --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" \
  --dbname="$DB_NAME" --no-owner --no-acl --exit-on-error "$DUMP_FILE"

PGPASSWORD="$DB_PASSWORD" "$PSQL" \
  --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" \
  --dbname="$DB_NAME" --tuples-only --no-align \
  --command="SELECT current_database() || ': ' || count(*) || ' tables' FROM information_schema.tables WHERE table_schema = 'public';"

echo "[OK] Base '$DB_NAME' remplacée depuis : $DUMP_FILE"
