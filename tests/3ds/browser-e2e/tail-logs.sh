#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
LOG_DIR="${ECOMMERCE_E2E_RUNTIME:-$ROOT/runtime/acquiring-ecommerce-e2e}/logs"
mapfile -t files < <(find "$LOG_DIR" -maxdepth 1 -type f -name '*.log')
((${#files[@]})) || { printf 'Aucun journal disponible\n' >&2; exit 1; }
tail -n 80 -F "${files[@]}"
