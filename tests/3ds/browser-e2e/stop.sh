#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
bash "$ROOT/tests/acquiring/ecommerce-e2e/05-stop.sh" LOCAL_ISSUING
