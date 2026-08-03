#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
CONFIG_FILE="${ACQUIRING_E2E_CONFIG_FILE:-$ROOT/runtime/issuing-connected-e2e/connected-e2e.env}"
[[ -f "$CONFIG_FILE" ]] || {
  printf '[3DS BROWSER] ERREUR - Configuration absente: %s\n' "$CONFIG_FILE" >&2
  exit 1
}

set -a
# shellcheck disable=SC1090
source "$CONFIG_FILE"
set +a

cd "$ROOT/sg-frontend"
npx playwright test e2e/merchant-site-3ds.spec.ts --project=chromium
