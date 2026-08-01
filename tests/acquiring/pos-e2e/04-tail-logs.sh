#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"
touch "$LOG_DIR/card-issuing.log" "$LOG_DIR/serverpos.log" "$LOG_DIR/pos-simulator.log"
tail -n 80 -f "$LOG_DIR/card-issuing.log" "$LOG_DIR/serverpos.log" "$LOG_DIR/pos-simulator.log"
