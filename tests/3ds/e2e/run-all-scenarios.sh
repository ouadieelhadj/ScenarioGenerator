#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../../.." && pwd)"
ACQ_DIR="$ROOT/tests/acquiring/ecommerce-e2e"
ROUTE=LOCAL_ISSUING

export THREE_DS_ENABLED=true
export THREE_DS_PROGRAM="${THREE_DS_PROGRAM:-MASTERCARD}"
export THREE_DS_ISSUER_MODE=MEMBER

trap 'bash "$ACQ_DIR/05-stop.sh" "$ROUTE" || true' EXIT
if [[ "${ECOMMERCE_E2E_SKIP_BUILD:-false}" != "true" ]]; then
  bash "$ACQ_DIR/00-build-and-install.sh" "$ROUTE"
fi
bash "$ACQ_DIR/01-start.sh" "$ROUTE"
bash "$ACQ_DIR/02-provision.sh" "$ROUTE"

export MERCHANT_SITE_TYPE=NATIONAL THREE_DS_FLOW=FRICTIONLESS
bash "$ACQ_DIR/03-purchase.sh" "$ROUTE"

export MERCHANT_SITE_TYPE=NATIONAL THREE_DS_FLOW=CHALLENGE
bash "$ACQ_DIR/03-purchase.sh" "$ROUTE"

export MERCHANT_SITE_TYPE=INTERNATIONAL THREE_DS_FLOW=CHALLENGE
bash "$ACQ_DIR/03-purchase.sh" "$ROUTE"

printf '[3DS E2E] SUCCES - frictionless national, challenge national et challenge international.\n'
