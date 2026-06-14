#!/bin/bash
# ═══════════════════════════════════════════════════════════
# ScenarioGenerator — TPS Test Script
# Test : 100_Achat_Par_Seconde
# Paliers : 10 TPS → 50 TPS → 100 TPS
# ═══════════════════════════════════════════════════════════

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

title() { echo -e "\n${YELLOW}══════════════════════════════════════${NC}"; echo -e "${YELLOW}  $1${NC}"; echo -e "${YELLOW}══════════════════════════════════════${NC}"; }
info()  { echo -e "${BLUE}ℹ️  $1${NC}"; }
pass()  { echo -e "${GREEN}✅ $1${NC}"; }
fail()  { echo -e "${RED}❌ $1${NC}"; }

# ── 1. Login ─────────────────────────────────────────────────
title "1. LOGIN"
RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"Admin123!"}')
TOKEN=$(echo $RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  fail "Login failed"
  exit 1
fi
pass "Login OK"
AUTH="Authorization: Bearer $TOKEN"

# ── 2. Créer le type de message si nécessaire ─────────────────
title "2. MESSAGE TYPE"
RESPONSE=$(curl -s -X GET "$BASE_URL/api/admin/message-types" -H "$AUTH")
MSG_TYPE_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*,"code":"0100"' | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
if [ -z "$MSG_TYPE_ID" ]; then
  fail "MessageType 0100 non trouvé"
  exit 1
fi
pass "MessageType 0100 — id=$MSG_TYPE_ID"

# ── 3. Créer le test TPS ──────────────────────────────────────
title "3. CRÉATION TEST TPS"

# Vérifier si le test existe déjà
RESPONSE=$(curl -s -X GET "$BASE_URL/api/admin/tests" -H "$AUTH")
TEST_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*,"name":"100_Achat_Par_Seconde"' | grep -o '"id":[0-9]*' | grep -o '[0-9]*')

if [ -n "$TEST_ID" ]; then
  info "Test 100_Achat_Par_Seconde existe déjà — id=$TEST_ID"
else
  RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/tests" \
    -H "$AUTH" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"100_Achat_Par_Seconde\",
      \"description\": \"Test de charge — montée progressive 10 → 50 → 100 TPS\",
      \"category\": \"AUTHORIZATION\",
      \"messageTypeId\": $MSG_TYPE_ID,
      \"config\": \"{\\\"DE003_PROCESSING_CODE\\\":\\\"000000\\\",\\\"DE004_AMOUNT\\\":5000,\\\"DE018_MCC\\\":\\\"5411\\\",\\\"DE022_POS_ENTRY_MODE\\\":\\\"051\\\",\\\"DE049_CURRENCY_CODE\\\":\\\"978\\\",\\\"DE052_PIN\\\":\\\"1234\\\"}\",
      \"expectedDe039\": \"00\",
      \"tpsSteps\": [
        {\"stepOrder\": 1, \"startSeconds\":  0, \"endSeconds\":  30, \"tpsValue\":  10},
        {\"stepOrder\": 2, \"startSeconds\": 30, \"endSeconds\":  60, \"tpsValue\":  50},
        {\"stepOrder\": 3, \"startSeconds\": 60, \"endSeconds\":  90, \"tpsValue\": 100}
      ]
    }")

  if echo "$RESPONSE" | grep -q '"name":"100_Achat_Par_Seconde"'; then
    pass "Test 100_Achat_Par_Seconde créé"
    TEST_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
    info "Test id=$TEST_ID"
  else
    fail "Création test échouée"
    echo "Response : $RESPONSE"
    exit 1
  fi
fi

# ── 4. Lancer l'exécution TPS ─────────────────────────────────
title "4. LANCEMENT EXÉCUTION TPS — MODE CHARGE"
info "Test     : 100_Achat_Par_Seconde"
info "Paliers  : 10 TPS (30s) → 50 TPS (30s) → 100 TPS (30s)"
info "Durée    : 90 secondes"

RESPONSE=$(curl -s -X POST "$BASE_URL/api/executions/start/$TEST_ID?mode=CHARGE" \
  -H "$AUTH")

if echo "$RESPONSE" | grep -q '"executionId"'; then
  EXEC_ID=$(echo "$RESPONSE" | grep -o '"executionId":[0-9]*' | grep -o '[0-9]*')
  pass "Exécution démarrée — id=$EXEC_ID"
else
  fail "Lancement échoué"
  echo "Response : $RESPONSE"
  exit 1
fi

# ── 5. Monitoring temps réel ──────────────────────────────────
title "5. MONITORING TEMPS RÉEL"
info "Appuie sur Ctrl+C pour arrêter"
echo ""

PREV_TX=0
START_TIME=$(date +%s)

for i in $(seq 1 95); do
  sleep 1

  RESPONSE=$(curl -s -X GET "$BASE_URL/api/executions/$EXEC_ID/status" -H "$AUTH")

  STATUS=$(echo "$RESPONSE"      | grep -o '"status":"[^"]*"'      | cut -d'"' -f4)
  TX=$(echo "$RESPONSE"          | grep -o '"txTotal":[0-9]*'       | grep -o '[0-9]*')
  APPROVED=$(echo "$RESPONSE"    | grep -o '"txApproved":[0-9]*'    | grep -o '[0-9]*')
  DECLINED=$(echo "$RESPONSE"    | grep -o '"txDeclined":[0-9]*'    | grep -o '[0-9]*')
  CURRENT_TPS=$(echo "$RESPONSE" | grep -o '"currentTps":[0-9]*'    | grep -o '[0-9]*')
  STEP=$(echo "$RESPONSE"        | grep -o '"currentStep":[0-9]*'   | grep -o '[0-9]*')
  AVG_TPS=$(echo "$RESPONSE"     | grep -o '"avgTps":"[^"]*"'       | cut -d'"' -f4)
  AVG_MS=$(echo "$RESPONSE"      | grep -o '"avgResponseMs":"[^"]*"'| cut -d'"' -f4)
  ELAPSED=$(echo "$RESPONSE"     | grep -o '"elapsedSeconds":"[^"]*"'| cut -d'"' -f4)

  TX=${TX:-0}
  APPROVED=${APPROVED:-0}
  DECLINED=${DECLINED:-0}
  CURRENT_TPS=${CURRENT_TPS:-0}
  STEP=${STEP:-0}

  DELTA=$((TX - PREV_TX))
  PREV_TX=$TX

  echo -e "${BLUE}[${i}s]${NC} Step=${STEP} TPS=${CURRENT_TPS} | TX=${TX}(+${DELTA}) ✅${APPROVED} ❌${DECLINED} | AvgTPS=${AVG_TPS} AvgMs=${AVG_MS}ms | Elapsed=${ELAPSED}s | Status=${STATUS}"

  if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "STOPPED" ] || [ "$STATUS" = "ERROR" ]; then
    echo ""
    break
  fi
done

# ── 6. Résumé final ───────────────────────────────────────────
title "6. RÉSUMÉ FINAL"

RESPONSE=$(curl -s -X GET "$BASE_URL/api/executions/$EXEC_ID/status" -H "$AUTH")

TX=$(echo "$RESPONSE"       | grep -o '"txTotal":[0-9]*'       | grep -o '[0-9]*')
APPROVED=$(echo "$RESPONSE" | grep -o '"txApproved":[0-9]*'    | grep -o '[0-9]*')
DECLINED=$(echo "$RESPONSE" | grep -o '"txDeclined":[0-9]*'    | grep -o '[0-9]*')
AVG_TPS=$(echo "$RESPONSE"  | grep -o '"avgTps":"[^"]*"'       | cut -d'"' -f4)
AVG_MS=$(echo "$RESPONSE"   | grep -o '"avgResponseMs":"[^"]*"'| cut -d'"' -f4)
MIN_MS=$(echo "$RESPONSE"   | grep -o '"minResponseMs":[0-9]*' | grep -o '[0-9]*')
MAX_MS=$(echo "$RESPONSE"   | grep -o '"maxResponseMs":[0-9]*' | grep -o '[0-9]*')
P95=$(echo "$RESPONSE"      | grep -o '"p95ResponseMs":"[^"]*"'| cut -d'"' -f4)
P99=$(echo "$RESPONSE"      | grep -o '"p99ResponseMs":"[^"]*"'| cut -d'"' -f4)
STATUS=$(echo "$RESPONSE"   | grep -o '"status":"[^"]*"'       | cut -d'"' -f4)

echo -e "${YELLOW}  Test      : 100_Achat_Par_Seconde${NC}"
echo -e "${YELLOW}  Execution : $EXEC_ID${NC}"
echo -e "${YELLOW}  Status    : $STATUS${NC}"
echo ""
echo -e "${GREEN}  TX Total    : $TX${NC}"
echo -e "${GREEN}  Approuvées  : $APPROVED${NC}"
echo -e "${RED}  Refusées    : $DECLINED${NC}"
echo ""
echo -e "  TPS Moyen   : $AVG_TPS /s"
echo -e "  Réponse Avg : ${AVG_MS}ms"
echo -e "  Réponse Min : ${MIN_MS}ms"
echo -e "  Réponse Max : ${MAX_MS}ms"
echo -e "  P95         : ${P95}ms"
echo -e "  P99         : ${P99}ms"
echo ""

# Sauvegarder le rapport TNR
REPORT="scripts/TNR_TPS_$(date +%Y%m%d_%H%M%S).txt"
{
  echo "═══════════════════════════════════════════════════"
  echo "  ScenarioGenerator — Rapport TPS"
  echo "  Date      : $(date)"
  echo "  Test      : 100_Achat_Par_Seconde"
  echo "  Execution : $EXEC_ID"
  echo "  Status    : $STATUS"
  echo "═══════════════════════════════════════════════════"
  echo "  TX Total    : $TX"
  echo "  Approuvées  : $APPROVED"
  echo "  Refusées    : $DECLINED"
  echo "  TPS Moyen   : $AVG_TPS /s"
  echo "  Réponse Avg : ${AVG_MS}ms"
  echo "  Réponse Min : ${MIN_MS}ms"
  echo "  Réponse Max : ${MAX_MS}ms"
  echo "  P95         : ${P95}ms"
  echo "  P99         : ${P99}ms"
  echo "═══════════════════════════════════════════════════"
} > "$REPORT"

pass "Rapport sauvegardé : $REPORT"
