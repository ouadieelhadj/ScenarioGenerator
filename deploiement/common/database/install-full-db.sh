#!/usr/bin/env bash
set +H
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

# Reconstruit la base de reference exclusivement depuis les scripts SQL Git.
# Aucune archive .dump n'est necessaire.

DATABASE_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DATABASE_DIR/../../.." && pwd)"
SQL_RUNTIME_DIR=""

cleanup() {
  [[ -n "$SQL_RUNTIME_DIR" && -d "$SQL_RUNTIME_DIR" ]] && rm -rf -- "$SQL_RUNTIME_DIR"
}
trap cleanup EXIT

# shellcheck disable=SC1091
source "$ROOT/deploiement/common/runtime/platform-env.sh"

: "${DB_PASSWORD:?Exporter DB_PASSWORD avant de lancer ce script}"
export PGPASSWORD="$DB_PASSWORD"

if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "[ERREUR] DB_NAME invalide : $DB_NAME" >&2
  exit 1
fi
SQL_RUNTIME_DIR="$(mktemp -d)"
if [[ ! -x "$PSQL" && ! -f "$PSQL" ]]; then
  echo "[ERREUR] psql introuvable : $PSQL" >&2
  exit 1
fi

bash "$DATABASE_DIR/check-postgres.sh" --start

echo "[ATTENTION] La base $DB_NAME sera entierement recreee."
if [[ "${DB_REPLACE_CONFIRMATION:-}" != "RECREER $DB_NAME" ]]; then
  read -r -p "Saisir exactement 'RECREER $DB_NAME' : " confirmation
  [[ "$confirmation" == "RECREER $DB_NAME" ]] || {
    echo "[ANNULE] Confirmation incorrecte."
    exit 1
  }
fi

psql_admin() {
  "$PSQL" -X -v ON_ERROR_STOP=1 \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$@"
}

run_sql() {
  local label="$1" file="$2"
  local rendered
  [[ -f "$file" ]] || {
    echo "[ERREUR] Migration absente : $file" >&2
    exit 1
  }
  rendered="$SQL_RUNTIME_DIR/$(basename "$file")"
  # Plusieurs migrations historiques contiennent encore
  # "\connect scenariogenerator" ou un GRANT sur ce nom. La copie temporaire
  # rend leur cible explicite sans modifier les sources.
  sed "s/scenariogenerator/$DB_NAME/g" "$file" > "$rendered"
  echo "[SQL] $label"
  psql_admin -d "$DB_NAME" -f "$rendered"
}

echo "[1/4] Creation des roles applicatifs de base"
psql_admin -d postgres <<'SQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='scenario_user') THEN
    CREATE ROLE scenario_user LOGIN PASSWORD 'postgres123';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_acquirer_user') THEN
    CREATE ROLE dmas_acquirer_user LOGIN PASSWORD 'postgres123';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_issuer_user') THEN
    CREATE ROLE dmas_issuer_user LOGIN PASSWORD 'postgres123';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_issuer_user') THEN
    CREATE ROLE swam_issuer_user LOGIN PASSWORD 'postgres123';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_acquirer_user') THEN
    CREATE ROLE swam_acquirer_user LOGIN PASSWORD 'postgres123';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='mc_dmas_member') THEN
    CREATE ROLE mc_dmas_member LOGIN PASSWORD 'postgres123';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='mc_dmas_mastercard') THEN
    CREATE ROLE mc_dmas_mastercard LOGIN PASSWORD 'postgres123';
  END IF;
END $$;
SQL

echo "[2/4] Recreation de $DB_NAME"
psql_admin -d postgres <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS "$DB_NAME";
CREATE DATABASE "$DB_NAME" OWNER scenario_user;
SQL

echo "[3/4] Schema, parametrage et migrations"
run_sql "Structure de base"                         "$DATABASE_DIR/structure_tables.sql"
run_sql "Donnees de reference"                     "$DATABASE_DIR/donnees_reference.sql"
run_sql "Alignement des sequences"                 "$DATABASE_DIR/reset-sequences.sql"
run_sql "Socle multi-reseau"                       "$DATABASE_DIR/migration_v1.2.0_multireseau.sql"
run_sql "Reseaux"                                  "$DATABASE_DIR/migration_v1.2.0_networks.sql"
run_sql "Droits multi-reseau"                      "$DATABASE_DIR/migration_v1.2.0_grants.sql"
run_sql "Categories de donnees"                    "$DATABASE_DIR/migration_v1.2.0_data_category.sql"
run_sql "Directions"                               "$DATABASE_DIR/migration_v1.2.0_direction.sql"

run_sql "Tables SWAM SID"                          "$ROOT/deploiement/swam/migration_v1.3.0_swam_tables.sql"
run_sql "Proprietaires SWAM SID"                   "$ROOT/deploiement/swam/migration_v1.3.0_swam_owners.sql"
run_sql "Ports SWAM"                               "$ROOT/deploiement/swam/migration_v1.3.0_ports_swam.sql"
run_sql "Interfaces et journaux SWAM"              "$ROOT/deploiement/swam/migration_v1.4.0_swam_interfaces_logs.sql"
run_sql "Champs transactionnels SWAM SID"          "$ROOT/deploiement/swam/migration_v1.4.0_swam_sid_transactions.sql"
run_sql "Champs SID requis par le clearing"        "$ROOT/sql/16b_swam_sid_clearing_fields.sql"
run_sql "Cartes SWAM separees membre et switch"    "$ROOT/deploiement/swam/migration_v1.5.0_swam_cards_by_owner.sql"
run_sql "Clearing SWAM LIS"                        "$ROOT/sql/17_swam_lis_clearing.sql"
run_sql "Journaux autorisation DMAS pour DMC"      "$ROOT/sql/mastercard/V6__separate_dmas_authorization_journals.sql"
run_sql "Transactions clearing DMC separees"       "$ROOT/sql/mastercard/V7__dmc_clearing_transactions.sql"
run_sql "Portail RBAC et Maker Checker"             "$ROOT/sql/18_portal_rbac_workflow.sql"
run_sql "Cartes SWAM de recette"                   "$ROOT/deploiement/swam/swam_cartes_test.sql"
run_sql "Droits applicatifs finaux"                "$DATABASE_DIR/application-grants.sql"

echo "[4/4] Controles"
psql_admin -d "$DB_NAME" -P pager=off <<'SQL'
SELECT 'tables_public' AS controle, count(*)::text AS valeur
FROM pg_tables WHERE schemaname='public'
UNION ALL
SELECT 'interfaces_swam', count(*)::text FROM swam_interface
UNION ALL
SELECT 'cartes_swam_issuer', count(*)::text FROM issuer_swam_cards
UNION ALL
SELECT 'cartes_swam_acquirer', count(*)::text FROM acquirer_swam_cards
UNION ALL
SELECT 'journaux_dmas_membre', count(*)::text FROM mc_dmas_member_transactions
UNION ALL
SELECT 'journaux_dmas_issuer', count(*)::text FROM mc_dmas_issuer_transactions
UNION ALL
SELECT 'clearing_dmc_acquirer', count(*)::text FROM dmcs_acquirer_clearing_transactions
UNION ALL
SELECT 'clearing_dmc_issuer', count(*)::text FROM dmcs_issuer_clearing_transactions
UNION ALL
SELECT 'modules_portail', count(*)::text FROM app_module
ORDER BY controle;

DO $$
BEGIN
  IF NOT has_table_privilege('swam_issuer_user', 'public.swam_interface', 'SELECT') THEN
    RAISE EXCEPTION 'swam_issuer_user ne peut pas lire swam_interface';
  END IF;
  IF NOT has_table_privilege('swam_acquirer_user', 'public.swam_interface', 'SELECT') THEN
    RAISE EXCEPTION 'swam_acquirer_user ne peut pas lire swam_interface';
  END IF;
  IF NOT has_table_privilege('swam_lis_member_user', 'public.member_lis_file', 'SELECT') THEN
    RAISE EXCEPTION 'swam_lis_member_user ne peut pas lire member_lis_file';
  END IF;
  IF NOT has_table_privilege('swam_lis_switch_user', 'public.switch_lis_file', 'SELECT') THEN
    RAISE EXCEPTION 'swam_lis_switch_user ne peut pas lire switch_lis_file';
  END IF;
  IF to_regclass('public.swam_cards') IS NOT NULL THEN
    RAISE EXCEPTION 'la table partagee swam_cards ne doit plus exister';
  END IF;
  IF NOT has_table_privilege('swam_issuer_user', 'public.issuer_swam_cards', 'UPDATE') THEN
    RAISE EXCEPTION 'swam_issuer_user ne peut pas mettre a jour ses cartes';
  END IF;
  IF has_table_privilege('swam_issuer_user', 'public.acquirer_swam_cards', 'SELECT') THEN
    RAISE EXCEPTION 'swam_issuer_user ne doit pas lire les cartes du membre';
  END IF;
  IF NOT has_table_privilege('swam_acquirer_user', 'public.acquirer_swam_cards', 'UPDATE') THEN
    RAISE EXCEPTION 'swam_acquirer_user ne peut pas mettre a jour ses cartes';
  END IF;
  IF has_table_privilege('swam_acquirer_user', 'public.issuer_swam_cards', 'SELECT') THEN
    RAISE EXCEPTION 'swam_acquirer_user ne doit pas lire les cartes du switch';
  END IF;
  IF NOT has_table_privilege(
      'dmas_acquirer_user', 'public.mc_dmas_member_transactions', 'SELECT') THEN
    RAISE EXCEPTION 'DMCS acquirer ne peut pas lire son journal DMAS membre';
  END IF;
  IF has_table_privilege(
      'dmas_acquirer_user', 'public.mc_dmas_issuer_transactions', 'SELECT') THEN
    RAISE EXCEPTION 'DMCS acquirer ne doit pas lire le journal DMAS issuer';
  END IF;
  IF NOT has_table_privilege(
      'dmas_issuer_user', 'public.mc_dmas_issuer_transactions', 'SELECT') THEN
    RAISE EXCEPTION 'DMCS issuer ne peut pas lire son journal DMAS issuer';
  END IF;
  IF has_table_privilege(
      'dmas_issuer_user', 'public.mc_dmas_member_transactions', 'SELECT') THEN
    RAISE EXCEPTION 'DMCS issuer ne doit pas lire le journal DMAS membre';
  END IF;
  IF NOT has_table_privilege(
      'dmas_acquirer_user', 'public.dmcs_acquirer_clearing_transactions', 'UPDATE') THEN
    RAISE EXCEPTION 'DMCS acquirer ne possede pas sa table de clearing';
  END IF;
  IF NOT has_table_privilege(
      'dmas_issuer_user', 'public.dmcs_issuer_clearing_transactions', 'UPDATE') THEN
    RAISE EXCEPTION 'DMCS issuer ne possede pas sa table de clearing';
  END IF;
END $$;
SQL

echo "[OK] Base $DB_NAME reconstruite depuis les scripts SQL versionnes."
