#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
export THREE_DS_ENABLED=true
export THREE_DS_PROGRAM="${THREE_DS_PROGRAM:-MASTERCARD}"
export THREE_DS_ISSUER_MODE="${THREE_DS_ISSUER_MODE:-MEMBER}"
export MERCHANT_SITE_TYPE="${MERCHANT_SITE_TYPE:-NATIONAL}"
export THREE_DS_FLOW="${THREE_DS_FLOW:-CHALLENGE}"

# Le socle existant conserve la gestion des PID, des journaux, des ports et du
# fichier local ignore. Aucun secret n'est affiche par ce harnais.
# shellcheck disable=SC1091
source "$ROOT/tests/acquiring/ecommerce-e2e/_common.sh" LOCAL_ISSUING

export JAVA_TOOL_OPTIONS="${ISSUING_E2E_JAVA_TOOL_OPTIONS:--Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC}"
export ACQUIRING_DB_URL="jdbc:postgresql://${DB_HOST:-localhost}:${DB_PORT:-5432}/${DB_NAME:-scenariogenerator}"
export ACQUIRING_DB_USER="${DB_USER:-postgres}"
export ACQUIRING_DB_PASSWORD="$DB_PASSWORD"
