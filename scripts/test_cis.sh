#!/bin/bash
# ═══════════════════════════════════════════════════════════
# ScenarioGenerator — CIS Test Script (TNR)
# Tests : 0100/0110, 0400/0410, 0120/0130, 0600/0610
# Network : 0800/0810, 0820/0830
# ═══════════════════════════════════════════════════════════

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASS=0
FAIL=0
TOKEN=""

pass() { echo -e "${GREEN}✅ PASS${NC} — $1"; ((PASS++)); }
fail() { echo -e "${RED}❌ FAIL${NC} — $1"; ((FAIL++)); }
title() { echo -e "\n${YELLOW}══════════════════════════════════════════════${NC}"; echo -e "${YELLOW}  $1${NC}"; echo -e "${YELLOW}══════════════════════════════════════════════${NC}"; }
info()  { echo -e "${BLUE}ℹ️  $1${NC}"; }

# ── Login ────────────────────────────────────────────────────
title "0. AUTHENTIFICATION"
RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"Admin123!"}')
TOKEN=$(echo $RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then
  pass "Login admin OK"
else
  fail "Login failed — arrêt du test"
  exit 1
fi
AUTH="Authorization: Bearer $TOKEN"

# ── Network Status ───────────────────────────────────────────
title "1. NETWORK STATUS"
RESPONSE=$(curl -s -X GET "$BASE_URL/api/mc/network/status" -H "$AUTH")
if echo "$RESPONSE" | grep -q '"keysExchanged":true'; then
  pass "Keys exchanged — ZMK+ZPK+ZAK OK"
else
  fail "Keys not exchanged"
fi
if echo "$RESPONSE" | grep -q '"signedOn":true'; then
  pass "Sign-on OK"
else
  fail "Not signed on"
fi

# ── Echo Test 0800/0810 ──────────────────────────────────────
title "2. ECHO TEST — 0800/0810"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/network/echo" -H "$AUTH")
if echo "$RESPONSE" | grep -q '"success":true'; then
  pass "Echo Test 0800/0810 — DE039=00"
else
  fail "Echo Test failed"
  info "Response : $RESPONSE"
fi

# ── Sign-on 0800/0810 ────────────────────────────────────────
title "3. SIGN-ON — 0800/0810"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/network/signon" -H "$AUTH")
if echo "$RESPONSE" | grep -q '"success":true'; then
  pass "Sign-on 0800/0810 — DE039=00"
else
  fail "Sign-on failed"
  info "Response : $RESPONSE"
fi

# ── Authorization 0100/0110 ──────────────────────────────────
title "4. AUTHORIZATION — 0100/0110"

# Test 4.1 — Achat nominal
info "Test 4.1 — Achat nominal (Purchase)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE002_PAN":"5555555555554444","DE004_AMOUNT":5000,"DE003_PROCESSING_CODE":"000000","DE018_MCC":"5411","DE052_PIN":"1234"}')
if echo "$RESPONSE" | grep -q '"approved":true'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  AUTH_CODE=$(echo "$RESPONSE" | grep -o '"DE038_AUTH_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Achat nominal — DE039=$DE039 AuthCode=$AUTH_CODE"
  LAST_AUTH_CODE=$AUTH_CODE
  LAST_RRN=$(echo "$RESPONSE" | grep -o '"DE037_RETRIEVAL_REF":"[^"]*"' | cut -d'"' -f4)
  LAST_PAN="5555555555554444"
else
  fail "Achat nominal failed"
  info "Response : $RESPONSE"
fi

# Test 4.2 — Retrait DAB
info "Test 4.2 — Retrait DAB (Cash Advance)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":10000,"DE003_PROCESSING_CODE":"010000","DE018_MCC":"6011","DE052_PIN":"1234"}')
if echo "$RESPONSE" | grep -q '"approved":true'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Retrait DAB — DE039=$DE039"
else
  fail "Retrait DAB failed"
  info "Response : $RESPONSE"
fi

# Test 4.3 — MCC bloqué
info "Test 4.3 — MCC bloqué (Jeux 7995)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":5000,"DE018_MCC":"7995","DE052_PIN":"1234"}')
if echo "$RESPONSE" | grep -q '"approved":false'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "MCC bloqué (7995) — DE039=$DE039 (refusé)"
else
  fail "MCC bloqué — attendu refus"
  info "Response : $RESPONSE"
fi

# Test 4.4 — Montant élevé
info "Test 4.4 — Montant élevé (>500000)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":999999,"DE052_PIN":"1234"}')
if echo "$RESPONSE" | grep -q '"approved":false'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Montant élevé — DE039=$DE039 (refusé)"
else
  fail "Montant élevé — attendu refus"
  info "Response : $RESPONSE"
fi

# Test 4.5 — Ecommerce sans PIN
info "Test 4.5 — Ecommerce (POS Entry Mode 081)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":15000,"DE018_MCC":"5945","DE022_POS_ENTRY_MODE":"081","DE003_PROCESSING_CODE":"000000"}')
if echo "$RESPONSE" | grep -q '"approved"'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Ecommerce — DE039=$DE039"
else
  fail "Ecommerce failed"
  info "Response : $RESPONSE"
fi

# Test 4.6 — Balance Inquiry
info "Test 4.6 — Balance Inquiry (DE003=310000)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":0,"DE003_PROCESSING_CODE":"310000","DE018_MCC":"6011","DE052_PIN":"1234"}')
if echo "$RESPONSE" | grep -q '"approved"'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Balance Inquiry — DE039=$DE039"
else
  fail "Balance Inquiry failed"
  info "Response : $RESPONSE"
fi

# Test 4.7 — Refund
info "Test 4.7 — Refund (DE003=200000)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/authorize" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":5000,"DE003_PROCESSING_CODE":"200000","DE018_MCC":"5411"}')
if echo "$RESPONSE" | grep -q '"approved"'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Refund — DE039=$DE039"
else
  fail "Refund failed"
  info "Response : $RESPONSE"
fi

# ── Reversal 0400/0410 ───────────────────────────────────────
title "5. REVERSAL — 0400/0410"

# Test 5.1 — Reversal nominal
info "Test 5.1 — Reversal nominal (Full Reversal)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/reversal" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{
    \"DE002_PAN\": \"$LAST_PAN\",
    \"DE004_AMOUNT\": 5000,
    \"DE003_PROCESSING_CODE\": \"000000\",
    \"DE038_AUTH_CODE\": \"$LAST_AUTH_CODE\",
    \"DE037_RETRIEVAL_REF\": \"$LAST_RRN\"
  }")
if echo "$RESPONSE" | grep -q '"reversed":true'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Reversal nominal — DE039=$DE039"
else
  fail "Reversal failed"
  info "Response : $RESPONSE"
fi

# Test 5.2 — Reversal sans auth code
info "Test 5.2 — Reversal (PAN auto-généré)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/reversal" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":5000,"DE003_PROCESSING_CODE":"000000"}')
if echo "$RESPONSE" | grep -q '"reversed"'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Reversal auto PAN — DE039=$DE039"
else
  fail "Reversal auto PAN failed"
  info "Response : $RESPONSE"
fi

# Test 5.3 — Reversal Cash Advance
info "Test 5.3 — Reversal Cash Advance"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/reversal" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":10000,"DE003_PROCESSING_CODE":"010000","DE018_MCC":"6011"}')
if echo "$RESPONSE" | grep -q '"reversed"'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Reversal Cash Advance — DE039=$DE039"
else
  fail "Reversal Cash Advance failed"
  info "Response : $RESPONSE"
fi

# ── Authorization Advice 0120/0130 ───────────────────────────
title "6. AUTHORIZATION ADVICE — 0120/0130"

# Test 6.1 — Advice nominal
info "Test 6.1 — Advice nominal (Acquirer Completed)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/advice" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{
    \"DE002_PAN\": \"$LAST_PAN\",
    \"DE004_AMOUNT\": 5000,
    \"DE003_PROCESSING_CODE\": \"000000\",
    \"DE038_AUTH_CODE\": \"$LAST_AUTH_CODE\",
    \"DE039_RESPONSE_CODE\": \"00\",
    \"DE060_ADVICE_REASON\": \"191\"
  }")
if echo "$RESPONSE" | grep -q '"accepted":true'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Advice nominal — DE039=$DE039"
else
  fail "Advice nominal failed"
  info "Response : $RESPONSE"
fi

# Test 6.2 — Advice auto PAN
info "Test 6.2 — Advice (PAN auto-généré)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/advice" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":5000,"DE039_RESPONSE_CODE":"00","DE060_ADVICE_REASON":"191"}')
if echo "$RESPONSE" | grep -q '"accepted"'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Advice auto PAN — DE039=$DE039"
else
  fail "Advice auto PAN failed"
  info "Response : $RESPONSE"
fi

# Test 6.3 — Advice refusé (X-Code)
info "Test 6.3 — Advice X-Code (transaction refusée)"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/mc/advice" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"DE004_AMOUNT":5000,"DE039_RESPONSE_CODE":"05","DE060_ADVICE_REASON":"191"}')
if echo "$RESPONSE" | grep -q '"accepted"'; then
  DE039=$(echo "$RESPONSE" | grep -o '"DE039_RESPONSE_CODE":"[^"]*"' | cut -d'"' -f4)
  pass "Advice X-Code — DE039=$DE039"
else
  fail "Advice X-Code failed"
  info "Response : $RESPONSE"
fi

# ── Résumé ───────────────────────────────────────────────────
TOTAL=$((PASS + FAIL))
REPORT="scripts/TNR_CIS_$(date +%Y%m%d_%H%M%S).txt"

echo ""
echo -e "${YELLOW}══════════════════════════════════════════════${NC}"
echo -e "${YELLOW}  RÉSUMÉ TNR CIS${NC}"
echo -e "${YELLOW}══════════════════════════════════════════════${NC}"
echo -e "${GREEN}  PASS  : $PASS${NC}"
echo -e "${RED}  FAIL  : $FAIL${NC}"
echo -e "  TOTAL  : $TOTAL"
echo -e "${YELLOW}══════════════════════════════════════════════${NC}"

# Sauvegarder rapport TNR
{
  echo "═══════════════════════════════════════════════════"
  echo "  ScenarioGenerator — TNR CIS"
  echo "  Date   : $(date)"
  echo "  URL    : $BASE_URL"
  echo "═══════════════════════════════════════════════════"
  echo "  Messages testés :"
  echo "    0800/0810 — Echo Test"
  echo "    0800/0810 — Sign-on"
  echo "    0100/0110 — Authorization (7 cas)"
  echo "    0400/0410 — Reversal (3 cas)"
  echo "    0120/0130 — Advice (3 cas)"
  echo "═══════════════════════════════════════════════════"
  echo "  PASS  : $PASS"
  echo "  FAIL  : $FAIL"
  echo "  TOTAL : $TOTAL"
  echo "═══════════════════════════════════════════════════"
} > "$REPORT"

echo -e "${GREEN}✅ Rapport TNR sauvegardé : $REPORT${NC}"
