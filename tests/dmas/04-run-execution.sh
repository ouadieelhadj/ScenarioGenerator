#!/bin/bash
# Execute un test via l'orchestrator. Usage : ./04-run-execution.sh <testId>
cd "$(dirname "$0")"; source lib-auth.sh
TEST_ID="$1"
[ -z "$TEST_ID" ] && { echo "Usage: $0 <testId>"; exit 1; }
TOKEN=$(get_token "$ORCH_URL")
echo "=== RUN EXECUTION testId=$TEST_ID ==="
curl -s --max-time 30 -X POST "$ORCH_URL/api/executions/start/$TEST_ID" -H "Authorization: Bearer $TOKEN"
echo ""
