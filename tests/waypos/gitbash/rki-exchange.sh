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
  --request POST "$SIMULATOR_BASE_URL/api/simulator/v1/key-change?confirm=false")"
[[ "$http_code" == "200" ]] || fail "Échange RKI refusé (HTTP $http_code)"

python - "$response_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    data = json.load(stream)
statuses = data.get("importedKeyStatuses") or []
if data.get("responseCode") != "00" or data.get("responseMacVerified") is not True:
    raise SystemExit("ERREUR: réponse RKI ou MAC invalide")
if not statuses or any(item.get("status") != "0" for item in statuses):
    raise SystemExit("ERREUR: aucune clé importée ou statut de clé en échec")
if data.get("confirmationSent") is not False:
    raise SystemExit("ERREUR: la confirmation ne devait pas être envoyée à cette étape")
types = ", ".join(sorted({item.get("keyType", "?") for item in statuses}))
print(f"Échange RKI accepté et MAC vérifié; clés importées: {types}.")
PY
