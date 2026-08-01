#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

require_var WAY_POS_TAK_HEX
require_var WAY_POS_TAMK_HEX
require_var WAY_POS_TPMK_HEX
terminal_id="${WAY_POS_TERMINAL_ID:-TERM0001}"
wait_http "$SERVER_BASE_URL/api/routing/v1/health" "ServerPOS" 5

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT
http_code="$(curl --silent --show-error --output "$response_file" \
  --write-out '%{http_code}' --connect-timeout 3 --max-time 65 \
  --request POST \
  "$SERVER_BASE_URL/api/admin/waypos/v1/local-test/terminals/$terminal_id/rki-bootstrap")"
[[ "$http_code" == "200" ]] \
  || fail "Bootstrap RKI refusé (HTTP $http_code). Rebuild et redémarrage ServerPOS requis."

python - "$response_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    data = json.load(stream)
checks = (
    data.get("initialTak") == "ACTIVE",
    data.get("rkiTak") == "PENDING_OR_PRESENT",
    data.get("rkiTpk") == "PENDING_OR_PRESENT",
)
if not all(checks):
    raise SystemExit("ERREUR: bootstrap RKI incomplet")
print("Bootstrap local accepté : TAK initiale active, TAK/TPK RKI préparées.")
PY
