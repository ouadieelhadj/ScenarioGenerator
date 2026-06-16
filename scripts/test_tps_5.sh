#!/bin/bash
# ═══════════════════════════════════════════════════════════
# Test TPS — 5 TPS pendant 10 secondes
# ═══════════════════════════════════════════════════════════

BASE_URL="http://localhost:8080"

echo "══════════════════════════════════════"
echo "  1. LOGIN"
echo "══════════════════════════════════════"
TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"Admin123!"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ Login failed"
  exit 1
fi
echo "✅ Login OK"
AUTH="Authorization: Bearer $TOKEN"

echo ""
echo "══════════════════════════════════════"
echo "  2. CRÉATION TEST 5 TPS / 10s"
echo "══════════════════════════════════════"

CREATE=$(curl -s -X POST "$BASE_URL/api/admin/tests" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{
    "name": "5_TPS_10_Secondes",
    "description": "Test de charge 5 TPS pendant 10 secondes",
    "category": "AUTHORIZATION",
    "messageTypeId": 1,
    "config": "{\"DE003_PROCESSING_CODE\":\"000000\",\"DE004_AMOUNT\":5000,\"DE018_MCC\":\"5411\",\"DE052_PIN\":\"1234\"}",
    "expectedDe039": "00",
    "tpsSteps": [
      {"stepOrder":1,"startSeconds":0,"endSeconds":10,"tpsValue":5}
    ]
  }')

TEST_ID=$(echo "$CREATE" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

if [ -z "$TEST_ID" ]; then
  # Test existe déjà — récupérer son id
  TEST_ID=$(curl -s -X GET "$BASE_URL/api/admin/tests" -H "$AUTH" \
    | grep -o '{"id":[0-9]*,"name":"5_TPS_10_Secondes"' \
    | grep -o '"id":[0-9]*' | cut -d: -f2)
  echo "ℹ️  Test existe déjà — id=$TEST_ID"
else
  echo "✅ Test créé — id=$TEST_ID"
fi

echo ""
echo "══════════════════════════════════════"
echo "  3. LANCEMENT EXÉCUTION (persist=true)"
echo "══════════════════════════════════════"
echo "ℹ️  Palier : 5 TPS pendant 10 secondes"
echo "ℹ️  Total attendu : ~50 transactions"

START=$(curl -s -X POST \
  "$BASE_URL/api/executions/start/$TEST_ID?mode=CHARGE&persist=true" \
  -H "$AUTH")

EXEC_ID=$(echo "$START" | grep -o '"executionId":[0-9]*' | cut -d: -f2)
echo "✅ Exécution démarrée — id=$EXEC_ID"

echo ""
echo "══════════════════════════════════════"
echo "  4. MONITORING"
echo "══════════════════════════════════════"

for i in $(seq 1 8); do
  sleep 2
  RESPONSE=$(curl -s -X GET "$BASE_URL/api/executions/$EXEC_ID/status" -H "$AUTH")
  STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
  TX=$(echo "$RESPONSE" | grep -o '"txTotal":[0-9]*' | cut -d: -f2)
  APP=$(echo "$RESPONSE" | grep -o '"txApproved":[0-9]*' | cut -d: -f2)
  echo "[$((i*2))s] TX=$TX ✅$APP | Status=$STATUS"
  if [ "$STATUS" = "COMPLETED" ]; then break; fi
done

echo ""
echo "══════════════════════════════════════"
echo "  5. RÉSUMÉ FINAL"
echo "══════════════════════════════════════"
RESPONSE=$(curl -s -X GET "$BASE_URL/api/executions/$EXEC_ID/status" -H "$AUTH")
echo "  Test      : 5_TPS_10_Secondes"
echo "  Execution : $EXEC_ID"
echo "  Status    : $(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)"
echo "  TX Total  : $(echo "$RESPONSE" | grep -o '"txTotal":[0-9]*' | cut -d: -f2)"
echo "  Approuvées: $(echo "$RESPONSE" | grep -o '"txApproved":[0-9]*' | cut -d: -f2)"
echo "  Refusées  : $(echo "$RESPONSE" | grep -o '"txDeclined":[0-9]*' | cut -d: -f2)"
echo "══════════════════════════════════════"
echo "✅ Pour générer l'IPM :"
echo "   curl -s -u 'admin:Admin123!' -X POST \\"
echo "     'http://localhost:8082/api/dmcs/generate?executionId=$EXEC_ID'"
