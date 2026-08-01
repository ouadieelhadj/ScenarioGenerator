#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh" "${1:-}"

psql_file "$ROOT/sql/acquiring/V2__create_ecommerce_transactions.sql"
psql_file "$ROOT/sql/3ds/V1__create_three_ds_member.sql"
"$MAVEN" -o -nsu -f "$ROOT/pom.xml" \
  -pl sg-card-issuing,sg-acquiring,sg-merchant-site-simulator,sg-3ds-member,sg-3ds-network-simulator,sg-visa-mastercard-gateway-simulator,sg-mc-dmas-member,sg-mc-dmas-mastercard,sg-swam-acquirer,sg-swam-issuer \
  -am package -DskipTests -Dmaven.repo.local="$MAVEN_REPO"
printf '[ECOM E2E] Migrations Acquisition/3DS et build termines.\n'
