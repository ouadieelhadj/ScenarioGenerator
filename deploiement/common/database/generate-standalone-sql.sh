#!/usr/bin/env bash
set +H
set -euo pipefail

# ================================================================
# generate-standalone-sql.sh  (PC SOURCE)
# Genere un seul fichier SQL autonome qui recrée la base complète
# scenariogenerator sur n'importe quel PC, sans aucun fichier externe.
# Inclut : schema + données de référence (pas les transactions runtime).
# ================================================================

PSQL="/d/MoneyCore/PostgreSQL/18/bin/psql.exe"
PGDUMP="/d/MoneyCore/PostgreSQL/18/bin/pg_dump.exe"
export PGPASSWORD="postgres123"
PGHOST="localhost"; PGPORT="5432"; SUPER="postgres"; DB="scenariogenerator"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$SCRIPT_DIR/create-standalone-db.sql"

# Auto-detection
for try in "/d/MoneyCore/PostgreSQL/18/bin/psql.exe" "/f/MoneyCore/pgsql/bin/psql.exe" "/d/MoneyCore/pgsql/bin/psql.exe"; do
  [ -f "$try" ] && { PSQL="$try"; PGDUMP="${try/psql/pg_dump}"; break; }
done
[ ! -f "$PSQL" ] && { echo "ERREUR : psql introuvable"; exit 1; }

pq() { "$PSQL" -h "$PGHOST" -p "$PGPORT" -U "$SUPER" -d "$DB" -tA "$@"; }
pd() { "$PGDUMP" -h "$PGHOST" -p "$PGPORT" -U "$SUPER" "$@"; }

echo "== Génération $OUT =="
echo

# ---------------------------------------------------------------
# HEADER
# ---------------------------------------------------------------
cat > "$OUT" << 'SQL'
-- ================================================================
-- create-standalone-db.sql — BASE COMPLETE scenariogenerator
-- Genere automatiquement depuis la base source.
-- Usage : psql -U postgres -f create-standalone-db.sql
-- ================================================================
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

-- Users applicatifs (idempotent)
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='scenario_user')
    THEN CREATE ROLE scenario_user      LOGIN PASSWORD 'postgres123'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_acquirer_user')
    THEN CREATE ROLE dmas_acquirer_user LOGIN PASSWORD 'postgres123'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_issuer_user')
    THEN CREATE ROLE dmas_issuer_user   LOGIN PASSWORD 'postgres123'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_issuer_user')
    THEN CREATE ROLE swam_issuer_user   LOGIN PASSWORD 'postgres123'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_acquirer_user')
    THEN CREATE ROLE swam_acquirer_user LOGIN PASSWORD 'postgres123'; END IF;
END $$;

-- Drop + Create base
DROP DATABASE IF EXISTS scenariogenerator;
CREATE DATABASE scenariogenerator OWNER scenario_user;
\connect scenariogenerator

GRANT ALL ON SCHEMA public TO scenario_user, dmas_acquirer_user, dmas_issuer_user, swam_issuer_user, swam_acquirer_user;

SQL

echo "  Header OK."

# ---------------------------------------------------------------
# SCHEMA : tables + sequences (schema public uniquement, sans FK)
# ---------------------------------------------------------------
echo "  Dump schema (tables + sequences, sans FK)..."
pd \
  --schema-only \
  --schema=public \
  --no-owner --no-acl \
  --no-privileges \
  --section=pre-data \
  "$DB" | grep -v "^--" | grep -v "^\\\\" | grep -v "^$" | \
  sed '/^ALTER TABLE.*ADD CONSTRAINT.*FOREIGN KEY/,/;/d' \
  >> "$OUT"

echo "  Schema tables OK."

# ---------------------------------------------------------------
# DONNEES : tables de référence seulement (pas les transactions)
# ---------------------------------------------------------------
echo "  Dump données de référence..."
REF_TABLES=(
  "networks"
  "roles"
  "permissions"
  "role_permissions"
  "users"
  "bin_range"
  "iso_field_catalog"
  "message_types"
  "campaigns"
  "tests"
  "dmas_cards"
  "swam_cards"
  "dmas_kek"
  "dmas_acq_keys"
  "dmas_iss_keys"
)

for tbl in "${REF_TABLES[@]}"; do
  COUNT=$(pq -c "SELECT count(*) FROM public.$tbl;" 2>/dev/null || echo "0")
  if [ "$COUNT" -gt 0 ] 2>/dev/null; then
    echo "    -> $tbl ($COUNT lignes)"
    pd \
      --data-only \
      --schema=public \
      --no-owner --no-acl \
      --table="public.$tbl" \
      "$DB" | grep -v "^--" | grep -v "^SET\|^SELECT\|^$" \
      >> "$OUT"
  else
    echo "    -> $tbl (vide, skip)"
  fi
done

# Cartes SWAM avec données de test (toujours inclure même si vide)
echo "    -> swam_cards (cartes de test SWAM)"
pd --data-only --schema=public --no-owner --no-acl --table="public.swam_cards" "$DB" \
  | grep -v "^--" | grep -v "^SET\|^SELECT\|^$" >> "$OUT" 2>/dev/null || true

echo "  Données OK."

# ---------------------------------------------------------------
# FK CONSTRAINTS (a la fin, apres toutes les tables et donnees)
# ---------------------------------------------------------------
echo "  Dump FK constraints..."
pd \
  --schema-only \
  --schema=public \
  --no-owner --no-acl \
  --no-privileges \
  --section=post-data \
  "$DB" | grep -v "^--" | grep -v "^\\\\" | grep -v "^$" \
  >> "$OUT"

echo "  FK OK."

# ---------------------------------------------------------------
# FOOTER de validation
# ---------------------------------------------------------------
cat >> "$OUT" << 'SQL'

-- Controle final
SELECT 'tables total'      AS objet, count(*)::text AS n FROM pg_tables WHERE schemaname='public'
UNION ALL SELECT 'tables swam_*',    count(*)::text FROM pg_tables WHERE tablename LIKE 'swam%'
UNION ALL SELECT 'users (app)',      count(*)::text FROM users
UNION ALL SELECT 'swam_cards',       count(*)::text FROM swam_cards
UNION ALL SELECT 'networks SWAM',    COALESCE(issuer_iso_port::text,'NULL') FROM networks WHERE code='SWAM'
ORDER BY objet;
SQL

SIZE=$(wc -c < "$OUT")
echo
echo "=================================================================="
echo " FICHIER PRET : $OUT"
echo " Taille : $(( SIZE / 1024 )) Ko"
echo
echo " Sur le nouveau PC :"
echo '   PGPASSWORD=postgres123 psql.exe -U postgres -h localhost -f create-standalone-db.sql'
echo "=================================================================="
