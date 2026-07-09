#!/usr/bin/env bash
set +H

# Recherche psql automatiquement
PSQL=""
for try in \
  "/d/MoneyCore/PostgreSQL/18/bin/psql.exe" \
  "/f/MoneyCore/pgsql/bin/psql.exe" \
  "/f/MoneyCore/PostgreSQL/18/bin/psql.exe"; do
  [ -f "$try" ] && { PSQL="$try"; break; }
done
[ -z "$PSQL" ] && { echo "psql introuvable"; exit 1; }

# Recherche create-db.sql dans le repo
SQL="$(dirname "$0")/create-db.sql"
[ ! -f "$SQL" ] && SQL="/d/MoneyCore/ScenarioGenerator/deploiement/create-db.sql"
[ ! -f "$SQL" ] && { echo "create-db.sql introuvable"; exit 1; }

echo "psql : $PSQL"
echo "sql  : $SQL"
echo "Lancement..."

PGPASSWORD=postgres123 "$PSQL" -U postgres -h localhost -f "$SQL"
echo "Termine."
