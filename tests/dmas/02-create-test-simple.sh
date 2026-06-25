#!/bin/bash
# Cree un test SIMPLE (1 autorisation, pas de tpsSteps). Affiche le testId.
cd "$(dirname "$0")"; source lib-auth.sh
TOKEN=$(get_token "$ORCH_URL")
PAN="${1:-5321962145453348}"
echo "=== CREATE TEST SIMPLE ==="
curl -s -X POST "$ORCH_URL/api/admin/tests" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"TEST-SIMPLE-JPOS\",\"description\":\"Autorisation simple via connexion permanente\",\"category\":\"DMAS\",\"active\":true,\"config\":\"{\\\"DE002_PAN\\\":\\\"$PAN\\\",\\\"DE004_AMOUNT\\\":49,\\\"DE003_PROCESSING_CODE\\\":\\\"000000\\\",\\\"DE018_MCC\\\":\\\"5999\\\",\\\"DE022_POS_ENTRY_MODE\\\":\\\"051\\\",\\\"DE049_CURRENCY_CODE\\\":\\\"840\\\"}\",\"tpsSteps\":[]}"
echo ""
echo "(noter le 'id' retourne = testId)"
