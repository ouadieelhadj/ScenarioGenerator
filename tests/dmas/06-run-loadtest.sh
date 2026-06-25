#!/bin/bash
# Load test ORCHESTRE : cree un test TPS, lance le loadtest via l'orchestrator
# (qui delegue la charge a sg-dmas-acquirer sur la connexion permanente),
# attend la fin, affiche le status et liste les rapports generes.
# Usage : ./06-run-loadtest.sh [tpsValue] [durationSeconds]
cd "$(dirname "$0")"; source lib-auth.sh
TPS="${1:-10}"; DUR="${2:-5}"
TOKEN_ISS=$(get_token "$ISS_URL")
TOKEN=$(get_token "$ORCH_URL")

echo "=== Sign-on (session permanente) ==="
curl -s --max-time 15 -X POST "$ISS_URL/api/admin/dmas/jpos/signon" -H "Authorization: Bearer $TOKEN_ISS"
echo ""

echo "=== Creer test TPS ($TPS TPS / ${DUR}s, montant 0) ==="
RESP=$(curl -s -X POST "$ORCH_URL/api/admin/tests" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"name\":\"LOADTEST-${TPS}TPS-${DUR}s\",\"category\":\"DMAS\",\"active\":true,\"config\":\"{\\\"DE002_PAN\\\":\\\"5321962145453348\\\",\\\"DE004_AMOUNT\\\":0,\\\"DE003_PROCESSING_CODE\\\":\\\"000000\\\"}\",\"tpsSteps\":[{\"stepOrder\":1,\"startSeconds\":0,\"endSeconds\":$DUR,\"tpsValue\":$TPS}]}")
TID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "testId = $TID"

echo "=== Lancer le loadtest orchestre ==="
START=$(curl -s -X POST "$ORCH_URL/api/executions/loadtest/$TID" -H "Authorization: Bearer $TOKEN")
echo "$START"
EID=$(echo "$START" | grep -o '"executionId":[0-9]*' | cut -d: -f2)
echo "executionId = $EID"

echo "=== Attente fin (~$((DUR + 6))s) ==="
sleep $((DUR + 6))
echo "=== Status execution ==="
curl -s -X GET "$ORCH_URL/api/executions/$EID/status" -H "Authorization: Bearer $TOKEN"
echo ""
echo "=== Rapports generes ==="
ls -la ../../reports/loadtest-$EID.* 2>/dev/null || echo "(rapports non trouves)"
