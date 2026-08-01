#!/usr/bin/env bash

set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ISSUING_E2E_CONFIG_FILE="${ISSUING_E2E_CONFIG_FILE:-$ROOT/runtime/issuing-connected-e2e/connected-e2e.env}"
if [[ -f "$ISSUING_E2E_CONFIG_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ISSUING_E2E_CONFIG_FILE"
  set +a
fi
# shellcheck source=../../deploiement/common/runtime/platform-env.sh
source "$ROOT/deploiement/common/runtime/platform-env.sh"

fail() {
  echo "[Issuing DB] FAIL - $*" >&2
  exit 1
}

for name in DB_PASSWORD CARD_ISSUING_DB_PASSWORD; do
  [[ -n "${!name-}" ]] || fail "$name est obligatoire"
done

[[ -x "$PSQL" ]] || fail "psql introuvable : $PSQL"
[[ "$DB_HOST" =~ ^[A-Za-z0-9_.:-]+$ ]] || fail "DB_HOST invalide"
[[ "$DB_PORT" =~ ^[0-9]{1,5}$ ]] || fail "DB_PORT invalide"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail "DB_NAME invalide"
[[ "$DB_USER" =~ ^[A-Za-z0-9_]+$ ]] || fail "DB_USER invalide"

export PGPASSWORD="$DB_PASSWORD"
"$PSQL" -w -v ON_ERROR_STOP=1 -v database_name="$DB_NAME" \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<'SQL'
\getenv issuing_password CARD_ISSUING_DB_PASSWORD
SELECT format(
  'CREATE ROLE card_issuing_user LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
  :'issuing_password')
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = 'card_issuing_user')
\gexec
SELECT format(
  'ALTER ROLE card_issuing_user LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
  :'issuing_password')
\gexec
SELECT format(
  'GRANT CONNECT ON DATABASE %I TO card_issuing_user',
  :'database_name')
\gexec
GRANT USAGE, CREATE ON SCHEMA public TO card_issuing_user;
SQL

export PGPASSWORD="$CARD_ISSUING_DB_PASSWORD"

marker_exists() {
  local kind="$1" name="$2" result
  if [[ "$kind" == "table" ]]; then
    result="$("$PSQL" -w -tAc \
      "SELECT to_regclass('public.$name') IS NOT NULL" \
      -h "$DB_HOST" -p "$DB_PORT" -U card_issuing_user -d "$DB_NAME")"
  else
    result="$("$PSQL" -w -tAc \
      "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='issuing_payment_identifier' AND column_name='$name')" \
      -h "$DB_HOST" -p "$DB_PORT" -U card_issuing_user -d "$DB_NAME")"
  fi
  [[ "$result" == "t" ]]
}

apply_migration() {
  local version="$1" kind="$2" marker="$3" file="$4"
  if marker_exists "$kind" "$marker"; then
    echo "[Issuing DB] SKIP $version - marqueur present"
    return
  fi
  echo "[Issuing DB] APPLY $version"
  "$PSQL" -w -1 -v ON_ERROR_STOP=1 \
    -h "$DB_HOST" -p "$DB_PORT" -U card_issuing_user -d "$DB_NAME" \
    -f "$ROOT/sql/issuing/$file"
}

apply_migration V1 table issuing_card_product \
  V1__create_card_issuing_foundation.sql
apply_migration V2 table issuing_payment_identifier \
  V2__create_payment_identifier_and_issuance.sql
apply_migration V3 table issuing_interface_endpoint \
  V3__create_issuing_interface_registry.sql
apply_migration V4 table issuing_authorization \
  V4__create_authorization_journal.sql
apply_migration V5 column pan_clear \
  V5__add_clear_pan_token_mapping.sql
apply_migration V6 table issuing_core_banking_sandbox_account \
  V6__create_core_banking_sandbox.sql
apply_migration V7 table payment_contract \
  V7__generalize_payment_contract.sql

table_count="$("$PSQL" -w -tAc \
  "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'issuing_%'" \
  -h "$DB_HOST" -p "$DB_PORT" -U card_issuing_user -d "$DB_NAME")"
foreign_owner_count="$("$PSQL" -w -tAc \
  "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'issuing_%' AND tableowner <> 'card_issuing_user'" \
  -h "$DB_HOST" -p "$DB_PORT" -U card_issuing_user -d "$DB_NAME")"

[[ "$table_count" == "11" ]] \
  || fail "11 tables prefixees issuing_ attendues, obtenu : $table_count"
[[ "$("$PSQL" -w -tAc \
  "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename='payment_contract'" \
  -h "$DB_HOST" -p "$DB_PORT" -U card_issuing_user -d "$DB_NAME")" == "1" ]] \
  || fail "Table partagee payment_contract absente"
[[ "$foreign_owner_count" == "0" ]] \
  || fail "$foreign_owner_count table(s) Issuing ont un autre proprietaire"

echo "[Issuing DB] SUCCESS - V1 a V6, 12 tables, proprietaire card_issuing_user"
