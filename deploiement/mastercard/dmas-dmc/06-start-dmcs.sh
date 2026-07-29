#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

dmas_start_module sg-dmcs-acquirer "$(dmas_url_port "$DMCS_ACQUIRER_URL")" \
  "--dmcs.base-dir=$DMCS_ACQUIRER_BASE_DIR"
dmas_start_module sg-dmcs-issuer "$(dmas_url_port "$DMCS_ISSUER_URL")" \
  "--dmcs.base-dir=$DMCS_ISSUER_BASE_DIR"

dmas_wait_http "$DMCS_ACQUIRER_URL/actuator/health" "DMCS acquirer"
dmas_wait_http "$DMCS_ISSUER_URL/actuator/health" "DMCS issuer"
