#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../runtime/platform-env.sh
source "$SCRIPT_DIR/../runtime/platform-env.sh"

PG_ISREADY="${PG_ISREADY:-$POSTGRES_HOME/bin/pg_isready.exe}"
PG_CTL="${PG_CTL:-$POSTGRES_HOME/bin/pg_ctl.exe}"
ACTION="${1:-check}"
POSTGRES_LOG="${POSTGRES_LOG:-$ROOT/runtime/postgres/postgresql.log}"

[[ -x "$PG_ISREADY" && -x "$PSQL" ]] || {
  echo "[FAIL] pg_isready/psql introuvable sous POSTGRES_HOME=$POSTGRES_HOME" >&2
  exit 1
}

start_postgres() {
  if [[ -n "${POSTGRES_SERVICE_NAME:-}" ]]; then
    [[ "$POSTGRES_SERVICE_NAME" =~ ^[A-Za-z0-9_.-]+$ ]] || {
      echo "[FAIL] POSTGRES_SERVICE_NAME non sécurisé." >&2
      return 1
    }
    command -v powershell.exe >/dev/null 2>&1 || {
      echo "[FAIL] powershell.exe requis pour démarrer le service Windows." >&2
      return 1
    }
    echo "[INFO] Démarrage du service Windows $POSTGRES_SERVICE_NAME..."
    powershell.exe -NoProfile -Command \
      "\$service=Get-Service -Name '$POSTGRES_SERVICE_NAME' -ErrorAction Stop; if (\$service.Status -ne 'Running') { Start-Service -Name '$POSTGRES_SERVICE_NAME' }"
    return
  fi

  if [[ -n "${PGDATA:-}" && -f "$PGDATA/PG_VERSION" && -x "$PG_CTL" ]]; then
    mkdir -p "$(dirname "$POSTGRES_LOG")"
    echo "[INFO] Démarrage de PostgreSQL portable avec PGDATA=$PGDATA..."
    "$PG_CTL" -D "$PGDATA" -l "$POSTGRES_LOG" start
    return
  fi

  cat >&2 <<EOF
[FAIL] Démarrage automatique impossible.
Configurer dans platform.env l'une des options suivantes :
  POSTGRES_SERVICE_NAME=<nom-exact-du-service-Windows>
ou
  PGDATA=<repertoire-contenant-PG_VERSION>

Pour une installation portable probable :
  find "$POSTGRES_HOME" -maxdepth 4 -name PG_VERSION -print
EOF
  return 1
}

if ! "$PG_ISREADY" --host="$DB_HOST" --port="$DB_PORT" >/dev/null 2>&1; then
  if [[ "$ACTION" == "--start" || "$ACTION" == "start" ]]; then
    start_postgres
    for _ in $(seq 1 30); do
      "$PG_ISREADY" --host="$DB_HOST" --port="$DB_PORT" >/dev/null 2>&1 && break
      sleep 1
    done
  fi
fi

if ! "$PG_ISREADY" --host="$DB_HOST" --port="$DB_PORT" >/dev/null 2>&1; then
  cat >&2 <<EOF
[FAIL] PostgreSQL n'écoute pas sur $DB_HOST:$DB_PORT.

PostgreSQL installé ne signifie pas PostgreSQL démarré.

Service Windows (PowerShell administrateur) :
  Get-Service *postgre*
  Start-Service -Name <nom-du-service>

Installation portable (Git Bash) :
  "$POSTGRES_HOME/bin/pg_ctl.exe" -D <repertoire-data> \\
    -l <fichier-log> start

Si PostgreSQL écoute sur un autre port, corriger DB_PORT dans platform.env,
puis exécuter : source platform-path.sh
EOF
  exit 1
fi

echo "[OK] PostgreSQL écoute sur $DB_HOST:$DB_PORT"

if [[ -z "$DB_PASSWORD" ]]; then
  echo "[WARN] DB_PASSWORD absent : connexion SQL authentifiée non vérifiée."
  exit 0
fi

if ! PGPASSWORD="$DB_PASSWORD" "$PSQL" \
    --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" \
    --dbname=postgres --no-password --tuples-only --no-align \
    --command="SELECT version();" >/dev/null; then
  echo "[FAIL] Le serveur écoute, mais la connexion SQL a échoué." >&2
  echo "Vérifier DB_USER, DB_PASSWORD, DB_PORT et pg_hba.conf." >&2
  exit 1
fi

echo "[OK] Connexion PostgreSQL authentifiée"
