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

psql \
  --host "$PGHOST" \
  --port "$PGPORT" \
  --dbname "$PGDATABASE" \
  --username "$PGUSER" \
  --set ON_ERROR_STOP=1 \
  --file "$script_dir/01_upsert_mtip_mastercard_cards.sql"

psql \
  --host "$PGHOST" \
  --port "$PGPORT" \
  --dbname "$PGDATABASE" \
  --username "$PGUSER" \
  --set ON_ERROR_STOP=1 \
  --file "$script_dir/02_verify_mtip_mastercard_cards.sql"
