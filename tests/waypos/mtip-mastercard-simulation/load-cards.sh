#!/usr/bin/env bash
set -Eeuo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$script_dir/../gitbash/_common.sh"
export PGHOST="${PGHOST:-${DB_HOST:-127.0.0.1}}"
export PGPORT="${PGPORT:-${DB_PORT:-5432}}"
export PGDATABASE="${PGDATABASE:-${DB_NAME:-scenariogenerator}}"
export PGUSER="${PGUSER:-${DB_USER:-postgres}}"
export PGPASSWORD="${PGPASSWORD:-${WAY_POS_DB_PASSWORD:-${DB_PASSWORD:-}}}"
[[ -n "$PGPASSWORD" ]] || fail "Mot de passe PostgreSQL absent"
exec "$script_dir/../../dmas/mtip_mastercard/load-mtip-mastercard-cards.sh"
