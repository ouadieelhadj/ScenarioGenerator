#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
export THREE_DS_ENABLED=true
bash "$ROOT/tests/acquiring/ecommerce-e2e/02-provision.sh" LOCAL_ISSUING
