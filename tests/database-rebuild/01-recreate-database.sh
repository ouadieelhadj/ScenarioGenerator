#!/usr/bin/env bash
set -euo pipefail
set +H

# Reconstruction destructive de scenariogenerator depuis les entites JPA.
# CONFIRM_RECREATE doit être exactement égal au nom de la base.

ROOT="${SG_ROOT:-D:/MoneyCore/ScenarioGenerator}"
PG_BIN="${PG_BIN:-D:/MoneyCore/PostgreSQL/18/bin}"
JAVA_BIN="${JAVA_BIN:-D:/MoneyCore/jdk-21.0.11/bin/java.exe}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-scenariogenerator}"
DB_ADMIN="${DB_ADMIN:-postgres}"

secret() {
  local name="$1" prompt="$2"
  if [ -z "${!name:-}" ]; then read -r -s -p "$prompt: " "$name"; echo; fi
  [ -n "${!name:-}" ] || { echo "$name requis"; exit 1; }
}

[ "${CONFIRM_RECREATE:-}" = "$DB_NAME" ] || {
  echo "Opération destructive refusée."
  echo "Relancer avec CONFIRM_RECREATE=$DB_NAME"
  exit 2
}
secret DB_PASSWORD "Mot de passe PostgreSQL administrateur"
secret APP_DB_PASSWORD "Mot de passe des quatre rôles applicatifs"
export PGPASSWORD="$DB_PASSWORD"

STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP="$ROOT/tests/database-rebuild/backup-$STAMP"
mkdir -p "$BACKUP" "$ROOT/logs"

psql_admin() {
  "$PG_BIN/psql.exe" -U "$DB_ADMIN" -h "$DB_HOST" -p "$DB_PORT" \
    -d "$1" -v ON_ERROR_STOP=1 "${@:2}"
}

echo "[1/7] Sauvegarde"
if psql_admin postgres -tAc \
   "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1; then
  "$PG_BIN/pg_dump.exe" -U "$DB_ADMIN" -h "$DB_HOST" -p "$DB_PORT" \
    -d "$DB_NAME" -F c -f "$BACKUP/$DB_NAME.dump"
  "$PG_BIN/pg_dump.exe" -U "$DB_ADMIN" -h "$DB_HOST" -p "$DB_PORT" \
    -d "$DB_NAME" --schema-only -f "$BACKUP/schema.sql"
fi

echo "[2/7] Recréation de $DB_NAME"
psql_admin postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
   WHERE datname='$DB_NAME' AND pid <> pg_backend_pid();"
"$PG_BIN/dropdb.exe" -U "$DB_ADMIN" -h "$DB_HOST" -p "$DB_PORT" \
  --if-exists "$DB_NAME"
"$PG_BIN/createdb.exe" -U "$DB_ADMIN" -h "$DB_HOST" -p "$DB_PORT" \
  -O "$DB_ADMIN" "$DB_NAME"

echo "[3/7] Rôles et grants initiaux"
for role in mc_dmas_member mc_dmas_mastercard swam_acquirer_user swam_issuer_user; do
  if ! psql_admin postgres -tAc \
       "SELECT 1 FROM pg_roles WHERE rolname='$role'" | grep -q 1; then
    psql_admin postgres -v role_name="$role" -v role_password="$APP_DB_PASSWORD" \
      -c "CREATE ROLE :\"role_name\" LOGIN PASSWORD :'role_password';"
  fi
done
psql_admin "$DB_NAME" -c "
GRANT CONNECT ON DATABASE $DB_NAME
 TO mc_dmas_member,mc_dmas_mastercard,swam_acquirer_user,swam_issuer_user;
GRANT USAGE,CREATE ON SCHEMA public
 TO mc_dmas_member,mc_dmas_mastercard,swam_acquirer_user,swam_issuer_user;"

run_create() {
  local module="$1" lmk="$2" iface="${3:-}" jar log pid
  jar="$(find "$ROOT/$module/target" -maxdepth 1 -type f -name "$module-*.jar" \
    ! -name '*.original' | head -1)"
  [ -n "$jar" ] || { echo "JAR absent : $module"; exit 1; }
  log="$ROOT/logs/create-$module.log"
  args=(-jar "$jar" --spring.jpa.hibernate.ddl-auto=create "--dmas.lmk.file=$lmk")
  [ -n "$iface" ] && args+=("--sg.interface=$iface")
  "$JAVA_BIN" "${args[@]}" >"$log" 2>&1 &
  pid=$!
  for _ in $(seq 1 60); do
    psql_admin "$DB_NAME" -tAc \
      "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" \
      | grep -Eq '[1-9]' && { sleep 12; break; }
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}

echo "[4/7] CREATE par DMAS member"
psql_admin "$DB_NAME" -c "
SET ROLE mc_dmas_member;
CREATE TABLE mc_dmas_interface(
 id_interface varchar(64) PRIMARY KEY,rest_port integer,log_file varchar(500));
INSERT INTO mc_dmas_interface VALUES(
 'DMAS_BANK_A',8084,'D:/MoneyCore/ScenarioGenerator/logs/mc-dmas-member.log');
RESET ROLE;"
run_create sg-mc-dmas-member "$ROOT/keys/dmas-lmk-acq.lmk" DMAS_BANK_A

echo "[5/7] CREATE par DMAS Mastercard"
psql_admin "$DB_NAME" -c "
REASSIGN OWNED BY mc_dmas_member TO mc_dmas_mastercard;
SET ROLE mc_dmas_mastercard;
INSERT INTO mc_dmas_interface
 (id_interface,bank_code,rest_port,iso_port,member_group_id,status,active,log_file)
 VALUES('DMAS_MASTERCARD_1','002202',8501,8500,'TESTGRP01','OFF',true,
 'D:/MoneyCore/ScenarioGenerator/logs/mc-dmas-mastercard.log');
RESET ROLE;"
run_create sg-mc-dmas-mastercard "$ROOT/keys/dmas-lmk-iss.lmk" DMAS_MASTERCARD_1

echo "[6/7] CREATE par SWAM issuer"
psql_admin "$DB_NAME" -c "
REASSIGN OWNED BY mc_dmas_mastercard TO swam_issuer_user;
SET ROLE swam_issuer_user;
INSERT INTO networks
 (id,code,name,active,issuer_rest_port,issuer_iso_port,acquirer_rest_port,
  acquirer_host,issuer_host)
 VALUES(1,'SWAM','Switch Al Maghrib',true,8511,8510,8094,'localhost','localhost');
INSERT INTO swam_interface
 (id_interface,bank_code,label,issuer_code_de33,member_group_id,business_role,
  host,rest_port,iso_port,log_file,status,active)
 VALUES('SWAM_NETWORK_1','300853','Reseau SWAM','300853','TESTGRP01','ISSUER',
  'localhost',8511,8510,
  'D:/MoneyCore/ScenarioGenerator/logs/swam-issuer.log','OFF',true);
RESET ROLE;"
run_create sg-swam-issuer "$ROOT/keys/dmas-lmk.lmk" SWAM_NETWORK_1

echo "[7/7] CREATE final par SWAM acquéreur"
psql_admin "$DB_NAME" -c "
REASSIGN OWNED BY swam_issuer_user TO swam_acquirer_user;
SET ROLE swam_acquirer_user;
INSERT INTO swam_interface
 (id_interface,bank_code,label,acquirer_code_de32,issuer_code_de33,
  member_group_id,business_role,host,rest_port,target_host,target_port,
  log_file,status,active)
 VALUES('SWAM_MEMBER_A','12345','Membre SWAM A','12345','300853',
  'TESTGRP01','ACQUIRER','localhost',8094,'localhost',8510,
  'D:/MoneyCore/ScenarioGenerator/logs/swam-acquirer.log','OFF',true);
RESET ROLE;"
run_create sg-swam-acquirer "$ROOT/keys/dmas-lmk.lmk" SWAM_MEMBER_A

echo "Schéma généré. Lancer ensuite 02-seed-minimal-and-keys.sh"
