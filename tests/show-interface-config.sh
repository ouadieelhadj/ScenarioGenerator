#!/usr/bin/env bash
set -euo pipefail
set +H

PG_BIN="${PG_BIN:-D:/MoneyCore/PostgreSQL/18/bin}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-scenariogenerator}"
DB_USER="${DB_USER:-postgres}"

if [ -z "${DB_PASSWORD:-}" ]; then
  read -r -s -p "Mot de passe PostgreSQL: " DB_PASSWORD
  echo
fi
[ -n "${DB_PASSWORD:-}" ] || { echo "DB_PASSWORD requis"; exit 1; }
export PGPASSWORD="$DB_PASSWORD"

PSQL=("$PG_BIN/psql.exe"
  -U "$DB_USER"
  -h "$DB_HOST"
  -p "$DB_PORT"
  -d "$DB_NAME"
  -v ON_ERROR_STOP=1)

echo "===== INTERFACES DMAS ====="
"${PSQL[@]}" -P null='NULL' -c "
SELECT id_interface,
       bank_code,
       business_role,
       acq_ica_de32 AS de32,
       fwd_id_de33 AS de33,
       iss_ica_de100 AS de100,
       member_group_id,
       rest_port,
       iso_port,
       target_host,
       target_port,
       log_file,
       status,
       active
FROM mc_dmas_interface
ORDER BY id_interface;"

echo
echo "===== INTERFACES SWAM ====="
"${PSQL[@]}" -P null='NULL' -c "
SELECT id_interface,
       bank_code,
       business_role,
       acquirer_code_de32 AS de32,
       issuer_code_de33 AS de33,
       member_group_id,
       rest_port,
       iso_port,
       target_host,
       target_port,
       log_file,
       status,
       active
FROM swam_interface
ORDER BY id_interface;"
