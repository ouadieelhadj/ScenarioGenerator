#!/usr/bin/env bash
set +H
set -euo pipefail

# ================================================================
# install-full-db.sh — Creation base scenariogenerator complete
# DMAS + multi-reseau v1.2.0 + SWAM v1.3.0
#
# Usage (depuis n'importe quel PC) :
#   bash /chemin/vers/ScenarioGenerator/deploiement/common/database/install-full-db.sh
#
# Prerequis : PostgreSQL installe ou portable, service demarre,
#             superuser postgres / postgres123
# ================================================================

# Chemin du script -> deploiement/common/database
DEPLOY="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_ROOT="$(cd "$DEPLOY/../.." && pwd)"
ROOT="$(cd "$DEPLOY_ROOT/.." && pwd)"
SWAM_DEPLOY="$DEPLOY_ROOT/swam"

# Recherche automatique de psql
PSQL=""
for try in \
  "/d/MoneyCore/PostgreSQL/18/bin/psql.exe" \
  "/f/MoneyCore/PostgreSQL/18/bin/psql.exe" \
  "/f/MoneyCore/pgsql/bin/psql.exe" \
  "/d/MoneyCore/pgsql/bin/psql.exe" \
  "$(which psql 2>/dev/null)"; do
  [ -f "$try" ] && { PSQL="$try"; break; }
done

if [ -z "$PSQL" ]; then
  echo "ERREUR : psql introuvable. Lance :"
  echo "  export PSQL=/chemin/vers/psql.exe && bash $0"
  exit 1
fi

PGHOST="localhost" ; PGPORT="5432" ; SUPERUSER="postgres"
export PGPASSWORD="postgres123"
DBNAME="scenariogenerator" ; DBPASS="postgres123"

prun() { "$PSQL" -h "$PGHOST" -p "$PGPORT" -U "$SUPERUSER" "$@"; }

echo "=================================================================="
echo " Installation base $DBNAME"
echo " psql   : $PSQL"
echo " deploy : $DEPLOY"
echo "=================================================================="

echo
echo "=== 1. Users applicatifs (idempotent) ==="
prun -d postgres -c "
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='scenario_user')
    THEN CREATE ROLE scenario_user     LOGIN PASSWORD '$DBPASS'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_acquirer_user')
    THEN CREATE ROLE dmas_acquirer_user LOGIN PASSWORD '$DBPASS'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_issuer_user')
    THEN CREATE ROLE dmas_issuer_user  LOGIN PASSWORD '$DBPASS'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_issuer_user')
    THEN CREATE ROLE swam_issuer_user  LOGIN PASSWORD '$DBPASS'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_acquirer_user')
    THEN CREATE ROLE swam_acquirer_user LOGIN PASSWORD '$DBPASS'; END IF;
END \$\$;"
echo "  OK."

echo
echo "=== 2. DROP + CREATE DATABASE $DBNAME ==="
prun -d postgres -c "DROP DATABASE IF EXISTS $DBNAME;"
prun -d postgres -c "CREATE DATABASE $DBNAME OWNER scenario_user;"
echo "  OK."

echo
echo "=== 3. Droits schema public ==="
prun -v ON_ERROR_STOP=1 -d "$DBNAME" -c \
  "GRANT ALL ON SCHEMA public TO scenario_user, dmas_acquirer_user, dmas_issuer_user, swam_issuer_user, swam_acquirer_user;"

run_sql() {
  echo; echo "=== $1 ==="
  prun -v ON_ERROR_STOP=1 -d "$DBNAME" -f "$2"
  echo "  OK."
}

run_sql "4.  Structure (35 tables DMAS)"          "$DEPLOY/structure_tables.sql"
run_sql "5.  Donnees de reference"                "$DEPLOY/donnees_reference.sql"
run_sql "6.  v1.2.0 : table networks"             "$DEPLOY/migration_v1.2.0_networks.sql"
run_sql "7.  v1.2.0 : socle multi-reseau"         "$DEPLOY/migration_v1.2.0_multireseau.sql"
run_sql "8.  v1.2.0 : grants networks"            "$DEPLOY/migration_v1.2.0_grants.sql"
run_sql "9.  v1.2.0 : data category"              "$DEPLOY/migration_v1.2.0_data_category.sql"
run_sql "10. v1.2.0 : direction"                  "$DEPLOY/migration_v1.2.0_direction.sql"
run_sql "11. v1.3.0 : tables SWAM + users swam_*" "$SWAM_DEPLOY/migration_v1.3.0_swam_tables.sql"
run_sql "12. v1.3.0 : owners SWAM"                "$SWAM_DEPLOY/migration_v1.3.0_swam_owners.sql"
run_sql "13. v1.3.0 : ports SWAM dans networks"   "$SWAM_DEPLOY/migration_v1.3.0_ports_swam.sql"
run_sql "14. Cartes de test SWAM"                 "$SWAM_DEPLOY/swam_cartes_test.sql"

echo
echo "=== 15. Controle final ==="
prun -d "$DBNAME" -P pager=off -c "
SELECT 'tables total'      AS objet, count(*)::text AS n FROM pg_tables WHERE schemaname='public'
UNION ALL SELECT 'tables swam_*',    count(*)::text FROM pg_tables WHERE tablename LIKE 'swam%'
UNION ALL SELECT 'users (app)',      count(*)::text FROM users
UNION ALL SELECT 'roles',            count(*)::text FROM roles
UNION ALL SELECT 'dmas_cards',       count(*)::text FROM dmas_cards
UNION ALL SELECT 'swam_cards',       count(*)::text FROM swam_cards
UNION ALL SELECT 'networks SWAM iso_port', COALESCE(issuer_iso_port::text,'NULL')
  FROM networks WHERE code='SWAM'
ORDER BY objet;"

echo
echo "=================================================================="
echo " BASE $DBNAME PRETE."
echo " Attendu : ~41 tables, swam_cards=3, networks SWAM iso_port=8510."
echo " Etape suivante : bash deploiement/swam/swam-e2e.sh"
echo "=================================================================="
