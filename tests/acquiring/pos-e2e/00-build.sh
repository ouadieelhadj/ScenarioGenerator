#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

"$MAVEN" -o -nsu -f "$ROOT/pom.xml" \
  -pl sg-card-issuing,sg-way-pos-server,sg-way-pos-simulator -am package \
  -DskipTests -Dmaven.repo.local="$MAVEN_REPO"
printf '[POS E2E] Build termine.\n'
