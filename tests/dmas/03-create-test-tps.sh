#!/bin/bash
# Cree un test TPS (mode CHARGE : 5 TPS pendant 3s). Affiche le testId.
cd "$(dirname "$0")"; source lib-auth.sh
TOKEN=$(get_token "$ORCH_URL")
PAN="${1:-5321962145453348}"
echo "=== CREATE TEST TPS (5 TPS / 3s) ==="
curl -s -X POST "$ORCH_URL/api/admin/tests" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"TEST-TPS-JPOS\",\"description\":\"Charge 5 TPS via connexion permanente\",\"category\":\"DMAS\",\"active\":true,\"config\":\"{\\\"DE002_PAN\\\":\\\"$PAN\\\",\\\"DE004_AMOUNT\\\":49,\\\"DE003_PROCESSING_CODE\\\":\\\"000000\\\",\\\"DE018_MCC\\\":\\\"5999\\\",\\\"DE022_POS_ENTRY_MODE\\\":\\\"051\\\",\\\"DE049_CURRENCY_CODE\\\":\\\"840\\\"}\",\"tpsSteps\":[{\"stepOrder\":1,\"startSeconds\":0,\"endSeconds\":3,\"tpsValue\":5}]}"
echo ""
echo "(noter le 'id' retourne = testId)"
