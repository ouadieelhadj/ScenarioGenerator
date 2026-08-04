#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "${MTIP_APPLY:-}" != "YES" ]]; then
  echo "Refus: export MTIP_APPLY=YES pour confirmer le chargement des cartes de certification." >&2
  exit 2
fi

: "${PGHOST:=127.0.0.1}"
: "${PGPORT:=5432}"
: "${PGDATABASE:=scenariogenerator}"
: "${PGUSER:=postgres}"

command -v psql >/dev/null 2>&1 || {
  echo "ERREUR: psql est introuvable dans PATH." >&2
  exit 1
}
command -v sed >/dev/null 2>&1 || {
  echo "ERREUR: sed est introuvable dans PATH." >&2
  exit 1
}

run_sql() {
  local sql_file="$1"
  # Les exports PowerShell historiques peuvent contenir un BOM UTF-8.
  # On le retire du flux sans modifier le fichier source.
  sed '1s/^\xEF\xBB\xBF//' "$sql_file" | psql \
    --host "$PGHOST" \
    --port "$PGPORT" \
    --dbname "$PGDATABASE" \
    --username "$PGUSER" \
    --set ON_ERROR_STOP=1 \
    --file -
}

run_sql "$script_dir/01_upsert_mtip_mastercard_cards.sql"
run_sql "$script_dir/02_verify_mtip_mastercard_cards.sql"
