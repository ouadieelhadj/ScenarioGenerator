#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
CLI_JAR="${DEPLOYMENT_CLI_JAR:-${REPO_ROOT}/sg-deployment-cli/target/deployment-cli.jar}"
JAVA_EXECUTABLE="${DEPLOYMENT_JAVA:-java}"

if [[ ! -f "${CLI_JAR}" ]]; then
  echo "ERREUR: CLI absent: ${CLI_JAR}" >&2
  exit 2
fi

exec "${JAVA_EXECUTABLE}" -jar "${CLI_JAR}" "$@"
