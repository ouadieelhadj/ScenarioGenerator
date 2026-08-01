#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

server_log="$WAY_POS_LOG_DIR/serverpos-console.log"
simulator_log="$WAY_POS_LOG_DIR/pos-simulator-console.log"
touch "$server_log" "$simulator_log"

printf 'Suivi ServerPOS et POS Simulator — Ctrl+C pour quitter.\n'
tail -f "$server_log" "$simulator_log"
