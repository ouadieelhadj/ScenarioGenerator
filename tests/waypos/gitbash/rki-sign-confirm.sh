#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

wait_http "$SIMULATOR_BASE_URL/api/simulator/v1/health" "POS Simulator" 5
response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

http_code="$(curl --silent --show-error --output "$response_file" \
  --write-out '%{http_code}' --connect-timeout 3 --max-time 65 \
  --request POST "$SIMULATOR_BASE_URL/api/simulator/v1/key-change/confirm")"
[[ "$http_code" == "200" ]] || fail "Sign/confirmation RKI refusé (HTTP $http_code)"

python - "$response_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    data = json.load(stream)
statuses = data.get("keyStatuses") or []
checks = (
    data.get("responseCode") == "00",
    data.get("responseMacVerified") is True,
    data.get("confirmed") is True,
    bool(statuses),
    all(item.get("status") == "0" for item in statuses),
)
if not all(checks):
    raise SystemExit("ERREUR: le sign/confirmation RKI n'est pas accepté et MAC-vérifié")
print("Sign/confirmation RKI accepté; tous les statuts de clés sont confirmés.")
PY
