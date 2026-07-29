#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

dmas_require_secret DB_PASSWORD "Mot de passe PostgreSQL"
bash "$ROOT/deploiement/common/database/check-postgres.sh" --start

echo "[INFO] Application idempotente des migrations DMAS/DMC V6 et V7..."
dmas_psql --set=ON_ERROR_STOP=1 \
  --file="$ROOT/sql/mastercard/V6__separate_dmas_authorization_journals.sql"
dmas_psql --set=ON_ERROR_STOP=1 \
  --file="$ROOT/sql/mastercard/V7__dmc_clearing_transactions.sql"

member_log="$(dmas_to_windows_path "$DMAS_DMC_LOG_DIR/sg-mc-dmas-member.log")"
mastercard_log="$(dmas_to_windows_path "$DMAS_DMC_LOG_DIR/sg-mc-dmas-mastercard.log")"
member_log_sql="${member_log//\'/\'\'}"
mastercard_log_sql="${mastercard_log//\'/\'\'}"

dmas_psql --set=ON_ERROR_STOP=1 --command="
UPDATE mc_dmas_interface
   SET log_file='$member_log_sql'
 WHERE id_interface='$DMAS_MEMBER_INTERFACE';
UPDATE mc_dmas_interface
   SET log_file='$mastercard_log_sql'
 WHERE id_interface='$DMAS_MASTERCARD_INTERFACE';
"

counts="$(dmas_psql --tuples-only --no-align --command="
SELECT
 (SELECT count(*) FROM information_schema.tables
   WHERE table_schema='public' AND table_name='mc_dmas_member_transactions'),
 (SELECT count(*) FROM information_schema.tables
   WHERE table_schema='public' AND table_name='mc_dmas_issuer_transactions'),
 (SELECT count(*) FROM information_schema.tables
   WHERE table_schema='public' AND table_name='dmcs_acquirer_clearing_transactions'),
 (SELECT count(*) FROM information_schema.tables
   WHERE table_schema='public' AND table_name='dmcs_issuer_clearing_transactions');
" | tr -d '[:space:]')"

[[ "$counts" == "1|1|1|1" ]] || {
  echo "[FAIL] Tables DMAS/DMC incompletes : $counts" >&2
  exit 1
}
echo "[OK] Journaux DMAS et tables clearing DMC installes"
echo "[OK] Logs DMAS parametrés sous $DMAS_DMC_LOG_DIR"
