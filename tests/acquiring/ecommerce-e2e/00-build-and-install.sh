#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh" "${1:-}"

psql_file "$ROOT/sql/acquiring/V2__create_ecommerce_transactions.sql"
"$MAVEN" -o -nsu -f "$ROOT/pom.xml" \
  -pl sg-card-issuing,sg-acquiring,sg-ecommerce-simulator,sg-mc-dmas-member,sg-mc-dmas-mastercard,sg-swam-acquirer,sg-swam-issuer \
  -am package -DskipTests -Dmaven.repo.local="$MAVEN_REPO"
printf '[ECOM E2E] Migration V2 et build termines.\n'
