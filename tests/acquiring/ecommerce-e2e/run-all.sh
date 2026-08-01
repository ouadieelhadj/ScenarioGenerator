#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
DIR="$(cd "$(dirname "$0")" && pwd)"
ROUTE="${1:-${ECOMMERCE_ROUTE:-LOCAL_ISSUING}}"
trap 'bash "$DIR/05-stop.sh" "$ROUTE" || true' EXIT
bash "$DIR/00-build-and-install.sh" "$ROUTE"
bash "$DIR/01-start.sh" "$ROUTE"
bash "$DIR/02-provision.sh" "$ROUTE"
bash "$DIR/03-purchase.sh" "$ROUTE"
