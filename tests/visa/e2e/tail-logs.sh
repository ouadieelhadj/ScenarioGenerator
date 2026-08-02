#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LOG_DIR="${VISA_E2E_RUNTIME:-$ROOT/runtime/visa-e2e}/logs"
[[ -d "$LOG_DIR" ]] || { printf 'Logs absents: %s\n' "$LOG_DIR" >&2; exit 1; }
tail -f "$LOG_DIR"/*.log
