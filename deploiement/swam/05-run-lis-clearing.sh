#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

[[ -n "$DB_PASSWORD" ]] || {
  echo "[FAIL] DB_PASSWORD obligatoire pour le clearing LIS" >&2
  exit 1
}

# Le scénario LIS existant conserve les contrôles métier de référence.
# Les services SID et les clés ont déjà été préparés par les étapes 01 à 04.
export RUN_SID_BOOTSTRAP=false
export ROOT JAVA MAVEN_REPO PSQL DB_PASSWORD
export MVN="$MAVEN"
bash "$SCRIPT_DIR/swam-lis-e2e.sh"
