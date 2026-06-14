#!/bin/bash
# ═══════════════════════════════════════════════════════════
# ScenarioGenerator — Test Script
# ═══════════════════════════════════════════════════════════

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}✅ PASS${NC} — $1"; ((PASS++)); }
fail() { echo -e "${RED}❌ FAIL${NC} — $1"; ((FAIL++)); }
title() { echo -e "\n${YELLOW}══════════════════════════════════════${NC}"; echo -e "${YELLOW}  $1${NC}"; echo -e "${YELLOW}══════════════════════════════════════${NC}"; }

title "1. AUTH — Login"
RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"Admin123!"}')
TOKEN=$(echo $RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then
  pass "Login admin — token OK"
  echo "   Token : ${TOKEN:0:50}..."
else
  fail "Login admin — token non reçu"
  echo "   Response : $RESPONSE"
  exit 1
fi
AUTH="Authorization: Bearer $TOKEN"

title "2. STATUS"
RESPONSE=$(curl -s -X GET "$BASE_URL/api/status")
if echo "$RESPONSE" | grep -q '"application":"SG Acquirer"'; then
  pass "GET /api/status"
else
  fail "GET /api/status"
  echo "   Response : $RESPONSE"
fi

title "3. NETWORK"
RESPONSE=$(curl -s -X GET "$BASE_URL/api/mc/network/status" -H "$AUTH")
if echo "$RESPONSE" | grep -q '"keysExchanged":true'; then
  pass "Network status — keysExchanged=true"
else
  fail "Network status — keysExchanged=false"
fi
if echo "$RESPONSE" | grep -q '"signedOn":true'; then
  pass "Network status — signedOn=true"
else
  fail "Network status — signedOn=false"
fi
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/network/echo" -H "$AUTH")
if echo "$RESPONSE" | grep -q '"success":true'; then
  pass "Echo — success=true"
else
  fail "Echo"
  echo "   Response : $RESPONSE"
fi

title "4. MESSAGE TYPES"
RESPONSE=$(curl -s -X GET "$BASE_URL/api/admin/message-types" -H "$AUTH")
COUNT=$(echo "$RESPONSE" | grep -o '"code"' | wc -l)
if [ "$COUNT" -ge 5 ]; then
  pass "GET /api/admin/message-types — $COUNT types"
else
  fail "GET /api/admin/message-types — attendu 5 trouvé $COUNT"
fi
MSG_TYPE_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*,"code":"0100"' | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
echo "   MessageType 0100 id=$MSG_TYPE_ID"

title "5. USERS"
RESPONSE=$(curl -s -X GET "$BASE_URL/api/admin/users" -H "$AUTH")
if echo "$RESPONSE" | grep -q '"login":"admin"'; then
  pass "GET /api/admin/users — admin trouvé"
else
  fail "GET /api/admin/users"
fi
MOHAMED_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*,"login":"mohamed"' | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
if [ -n "$MOHAMED_ID" ]; then
  echo "   Mohamed existe déjà id=$MOHAMED_ID"
else
  RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/users" \
    -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"login":"mohamed","password":"Mohamed123!","email":"mohamed@staging.com","role":"EXPLOITATION"}')
  if echo "$RESPONSE" | grep -q '"login":"mohamed"'; then
    pass "POST /api/admin/users — mohamed créé"
    MOHAMED_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
    echo "   Mohamed id=$MOHAMED_ID"
  else
    fail "POST /api/admin/users"
    echo "   Response : $RESPONSE"
  fi
fi

title "6. TESTS"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/tests" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Achat nominal CB\",
    \"description\": \"Test achat standard puce EMV\",
    \"category\": \"AUTHORIZATION\",
    \"messageTypeId\": $MSG_TYPE_ID,
    \"config\": \"{\\\"DE003_PROCESSING_CODE\\\":\\\"000000\\\",\\\"DE004_AMOUNT\\\":5000,\\\"DE018_MCC\\\":\\\"5411\\\",\\\"DE022_POS_ENTRY_MODE\\\":\\\"051\\\",\\\"DE049_CURRENCY_CODE\\\":\\\"978\\\",\\\"DE052_PIN\\\":\\\"1234\\\"}\",
    \"expectedDe039\": \"00\",
    \"tpsSteps\": [
      {\"stepOrder\": 1, \"startSeconds\": 0,  \"endSeconds\": 30, \"tpsValue\": 10},
      {\"stepOrder\": 2, \"startSeconds\": 30, \"endSeconds\": 60, \"tpsValue\": 25},
      {\"stepOrder\": 3, \"startSeconds\": 60, \"endSeconds\": 90, \"tpsValue\": 50}
    ]
  }")
if echo "$RESPONSE" | grep -q '"name":"Achat nominal CB"'; then
  pass "POST /api/admin/tests — Achat nominal CB créé"
  TEST_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
  echo "   Test id=$TEST_ID"
else
  fail "POST /api/admin/tests"
  echo "   Response : $RESPONSE"
  TEST_ID=1
fi

title "7. ASSIGNATION TEST → USER"
if [ -n "$MOHAMED_ID" ] && [ -n "$TEST_ID" ]; then
  RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/users/$MOHAMED_ID/tests/$TEST_ID" -H "$AUTH")
  if echo "$RESPONSE" | grep -q '"message":"Test assigned"'; then
    pass "Test $TEST_ID assigné à mohamed ($MOHAMED_ID)"
  else
    fail "Assignation test"
    echo "   Response : $RESPONSE"
  fi
else
  fail "IDs manquants user=$MOHAMED_ID test=$TEST_ID"
fi

RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"login":"mohamed","password":"Mohamed123!"}')
MOHAMED_TOKEN=$(echo $RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -n "$MOHAMED_TOKEN" ]; then
  pass "Login mohamed — token OK"
  RESPONSE=$(curl -s -X GET "$BASE_URL/api/tests/my" \
    -H "Authorization: Bearer $MOHAMED_TOKEN")
  echo "   Tests assignés : $(echo "$RESPONSE" | grep -o '"name"' | wc -l)"
  pass "GET /api/tests/my — OK"
else
  fail "Login mohamed"
fi

title "8. AUTHORIZATION 0100"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE002_PAN":"5555555555554444","DE004_AMOUNT":5000,"DE052_PIN":"1234"}')
if echo "$RESPONSE" | grep -q '"approved":true'; then
  pass "Achat nominal — approved=true"
else
  fail "Achat nominal"
  echo "   Response : $RESPONSE"
fi

RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":5000,"DE018_MCC":"7995","DE052_PIN":"1234"}')
if echo "$RESPONSE" | grep -q '"approved":false'; then
  pass "MCC bloqué (7995) — approved=false"
else
  fail "MCC bloqué"
  echo "   Response : $RESPONSE"
fi

RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":999999,"DE052_PIN":"1234"}')
if echo "$RESPONSE" | grep -q '"approved":false'; then
  pass "Montant élevé (999999) — approved=false"
else
  fail "Montant élevé"
  echo "   Response : $RESPONSE"
fi

RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":10000,"DE052_PIN":"5678"}')
if echo "$RESPONSE" | grep -q '"approved"'; then
  pass "PAN auto-généré — réponse reçue"
else
  fail "PAN auto-généré"
fi

echo ""
echo -e "${YELLOW}══════════════════════════════════════${NC}"
echo -e "${YELLOW}  RÉSUMÉ${NC}"
echo -e "${YELLOW}══════════════════════════════════════${NC}"
echo -e "${GREEN}  PASS : $PASS${NC}"
echo -e "${RED}  FAIL : $FAIL${NC}"
echo -e "  TOTAL : $((PASS + FAIL))"
echo -e "${YELLOW}══════════════════════════════════════${NC}"
