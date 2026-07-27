#!/usr/bin/env bash

export PATH="/usr/bin:/mingw64/bin:$PATH"

# Configuration globale locale de la plateforme.
# Chaque valeur peut être surchargée avant de lancer un script :
#   export JAVA_HOME_DIR=/f/MoneyCore/jdk-21.0.11

PLATFORM_RUNTIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DEFAULT_ROOT="$(cd "$PLATFORM_RUNTIME_DIR/../../.." && pwd)"

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

export PATH="$NODE_HOME:$JAVA_HOME_DIR/bin:$MAVEN_HOME/bin:$POSTGRES_HOME/bin:$PATH"
