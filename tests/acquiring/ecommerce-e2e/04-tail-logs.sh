#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh" "${1:-}"
files=("$LOG_DIR/card-issuing.log" "$LOG_DIR/acquiring.log" "$LOG_DIR/ecommerce-simulator.log")
if [[ "$ROUTE" == "SWAM" ]]; then
  files+=("$RUNTIME/swam/logs/sg-swam-issuer.log" "$RUNTIME/swam/logs/sg-swam-acquirer.log")
else
  files+=("$RUNTIME/dmas-dmc/logs/sg-mc-dmas-mastercard-console.log" "$RUNTIME/dmas-dmc/logs/sg-mc-dmas-member-console.log")
fi
touch "${files[@]}"
tail -n 80 -f "${files[@]}"
