#!/usr/bin/env bash

export PATH="/usr/bin:/mingw64/bin:$PATH"

# Configuration globale locale de la plateforme.
# Chaque valeur peut être surchargée avant de lancer un script :
#   export JAVA_HOME_DIR=/f/MoneyCore/jdk-21.0.11

PLATFORM_RUNTIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DEFAULT_ROOT="$(cd "$PLATFORM_RUNTIME_DIR/../../.." && pwd)"

# Priorité : variable déjà définie > platform.env local > valeur par défaut.
# Le chemin du fichier peut être surchargé avec PLATFORM_ENV_FILE.
PLATFORM_ENV_FILE="${PLATFORM_ENV_FILE:-${ROOT:-$PLATFORM_DEFAULT_ROOT}/platform.env}"
PLATFORM_ENV_KEYS=(
  ROOT JAVA_HOME_DIR JAVA MAVEN_HOME MAVEN NODE_HOME NPM NPX POSTGRES_HOME
  PSQL DB_HOST DB_PORT DB_NAME DB_USER MAVEN_REPO PLATFORM_RUNTIME
  PLATFORM_LOG_DIR PLATFORM_PID_DIR REPORTS_DIR DMAS_LMK_FILE
  SPRING_DATASOURCE_URL
)
declare -A PLATFORM_ENV_PRESET=()
declare -A PLATFORM_ENV_PRESET_VALUES=()
PLATFORM_DB_PASSWORD_WAS_SET=false
PLATFORM_DB_PASSWORD_VALUE=""
if [[ -v DB_PASSWORD ]]; then
  PLATFORM_DB_PASSWORD_WAS_SET=true
  PLATFORM_DB_PASSWORD_VALUE="$DB_PASSWORD"
fi
for PLATFORM_ENV_KEY in "${PLATFORM_ENV_KEYS[@]}"; do
  if [[ -v "$PLATFORM_ENV_KEY" ]]; then
    PLATFORM_ENV_PRESET["$PLATFORM_ENV_KEY"]=1
    PLATFORM_ENV_PRESET_VALUES["$PLATFORM_ENV_KEY"]="${!PLATFORM_ENV_KEY}"
  fi
done
if [[ -f "$PLATFORM_ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$PLATFORM_ENV_FILE"
  set +a
fi
if [[ "$PLATFORM_DB_PASSWORD_WAS_SET" == "true" ]]; then
  DB_PASSWORD="$PLATFORM_DB_PASSWORD_VALUE"
  export DB_PASSWORD
else
  unset DB_PASSWORD
fi
for PLATFORM_ENV_KEY in "${!PLATFORM_ENV_PRESET[@]}"; do
  printf -v "$PLATFORM_ENV_KEY" '%s' "${PLATFORM_ENV_PRESET_VALUES[$PLATFORM_ENV_KEY]}"
  export "$PLATFORM_ENV_KEY"
done
unset PLATFORM_ENV_KEY PLATFORM_ENV_KEYS PLATFORM_ENV_PRESET PLATFORM_ENV_PRESET_VALUES
unset PLATFORM_DB_PASSWORD_WAS_SET PLATFORM_DB_PASSWORD_VALUE

export ROOT="${ROOT:-$PLATFORM_DEFAULT_ROOT}"
export JAVA_HOME_DIR="${JAVA_HOME_DIR:-/d/MoneyCore/jdk-21.0.11}"
export JAVA="${JAVA:-$JAVA_HOME_DIR/bin/java.exe}"
export MAVEN_HOME="${MAVEN_HOME:-/d/MoneyCore/idea-2026.1.3.win/plugins/maven/lib/maven3}"
export MAVEN="${MAVEN:-$MAVEN_HOME/bin/mvn.cmd}"
export NODE_HOME="${NODE_HOME:-/d/MoneyCore/nodejs}"
export NPM="${NPM:-$NODE_HOME/npm.cmd}"
export NPX="${NPX:-$NODE_HOME/npx.cmd}"
export POSTGRES_HOME="${POSTGRES_HOME:-/d/MoneyCore/PostgreSQL/18}"
export PSQL="${PSQL:-$POSTGRES_HOME/bin/psql.exe}"
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-scenariogenerator}"
export DB_USER="${DB_USER:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-}"
export MAVEN_REPO="${MAVEN_REPO:-$ROOT/tmp/m2repo}"
export PLATFORM_RUNTIME="${PLATFORM_RUNTIME:-$ROOT/runtime/platform}"
export PLATFORM_LOG_DIR="${PLATFORM_LOG_DIR:-$PLATFORM_RUNTIME/logs}"
export PLATFORM_PID_DIR="${PLATFORM_PID_DIR:-$PLATFORM_RUNTIME/pids}"
export REPORTS_DIR="${REPORTS_DIR:-$ROOT/reports}"
export DMAS_LMK_FILE="${DMAS_LMK_FILE:-$ROOT/keys/dmas-lmk.lmk}"

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME}"
if [[ -n "$DB_PASSWORD" ]]; then
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$DB_PASSWORD}"
fi

export PATH="$NODE_HOME:$JAVA_HOME_DIR/bin:$MAVEN_HOME/bin:$POSTGRES_HOME/bin:$PATH"
