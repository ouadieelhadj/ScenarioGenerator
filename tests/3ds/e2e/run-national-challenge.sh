#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../../.." && pwd)"
export THREE_DS_ENABLED=true
export MERCHANT_SITE_TYPE=NATIONAL
export THREE_DS_PROGRAM="${THREE_DS_PROGRAM:-MASTERCARD}"
export THREE_DS_FLOW=CHALLENGE
export THREE_DS_ISSUER_MODE=MEMBER
exec bash "$ROOT/tests/acquiring/ecommerce-e2e/run-all.sh" LOCAL_ISSUING
