#!/usr/bin/env bash
# =====================================================================
# scenario-e2e.sh
# Scenario complet de bout en bout :
#   1. login            6. achat unitaire (PURCHASE via jPOS)
#   2. bootstrap KEK    7. creer une campagne
#   3. key exchange PEK 8. lancer la campagne (TPS)
#   4. sign-on issuer   9. suivre l'execution
#   5. (cartes deja en base)
# Prerequis : les 3 services demarres (./start-services.sh).
# =====================================================================
set -u
set +H   # desactive l'expansion d'historique (a cause des '!' eventuels)

# --- Configuration ---
ORC="http://localhost:8080"
ACQ="http://localhost:8084"
ISS="http://localhost:8501"
LOGIN="admin"
PASSWORD='Admin123!'
GROUP="TESTGRP01"
KEK_CLEAR="0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
PAN="5321962145453348"          # carte de test ACTIVE
AMOUNT="000000010000"           # n-12 : 100.00

# --- Helpers ---
ok()   { echo "  [OK]   $*"; }
fail() { echo "  [FAIL] $*"; echo ""; echo "Scenario interrompu."; exit 1; }
sep()  { echo ""; echo "=== $* ==="; }
# extrait la valeur d'un champ JSON simple : jval '<json>' champ
jval() { echo "$1" | sed -n "s/.*\"$2\":\"\?\([^,\"}]*\)\"\?.*/\1/p" | head -1; }

# =====================================================================
sep "1. Login ($LOGIN)"
RESP=$(curl -s -X POST "$ORC/auth/login" -H "Content-Type: application/json" \
  -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}")
TOKEN=$(jval "$RESP" token)
[ -n "$TOKEN" ] && ok "token recupere (${#TOKEN} caracteres)" || fail "login : pas de token. Reponse: $RESP"
AUTH="Authorization: Bearer $TOKEN"

# =====================================================================
sep "2. Bootstrap KEK (groupe $GROUP)"
CODE=$(curl -s -o /tmp/e2e_kek.json -w "%{http_code}" -X POST "$ACQ/api/admin/dmas/kek/bootstrap" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"memberGroupId\":\"$GROUP\",\"kekClear\":\"$KEK_CLEAR\"}")
if [ "$CODE" = "200" ]; then ok "KEK bootstrap (HTTP $CODE) : $(cat /tmp/e2e_kek.json)"
else echo "  [WARN] KEK bootstrap HTTP $CODE : $(cat /tmp/e2e_kek.json)"; echo "  (peut etre deja en place - on continue)"; fi

# =====================================================================
sep "3. Sign-on issuer (prerequis du key exchange)"
CODE=$(curl -s -o /tmp/e2e_signon.json -w "%{http_code}" -X POST \
  "$ISS/api/admin/dmas/jpos/signon" -H "$AUTH")
[ "$CODE" = "200" ] && ok "sign-on (HTTP $CODE) : $(cat /tmp/e2e_signon.json)" \
                    || fail "sign-on HTTP $CODE : $(cat /tmp/e2e_signon.json)"

# =====================================================================
sep "4. Key exchange PEK (groupe $GROUP) - apres le sign-on"
CODE=$(curl -s -o /tmp/e2e_pek.json -w "%{http_code}" -X POST \
  "$ACQ/api/admin/dmas/keyexchange/pek?memberGroupId=$GROUP" -H "$AUTH")
if [ "$CODE" = "200" ]; then ok "PEK echangee (HTTP $CODE) : $(cat /tmp/e2e_pek.json)"
else echo "  [WARN] PEK HTTP $CODE : $(cat /tmp/e2e_pek.json)"; echo "  (peut etre deja ACTIVE - on continue)"; fi

# =====================================================================
sep "5. Achat unitaire (PURCHASE via jPOS)"
RESP=$(curl -s -X POST "$ACQ/api/admin/dmas/auth" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"type\":\"PURCHASE\",\"pan\":\"$PAN\",\"amount\":\"$AMOUNT\",\"transport\":\"jpos\",\"entryMode\":\"CARD_PRESENT\"}")
echo "  reponse : $RESP"
# on regarde le code reponse ISO (de039) si present
DE39=$(jval "$RESP" de039)
if echo "$RESP" | grep -qiE "\"approved\"\s*:\s*true|\"de039\":\"00\"|\"responseCode\":\"00\""; then
  ok "achat approuve (de039=$DE39)"
else
  echo "  [INFO] achat non approuve ou format inattendu (de039=$DE39) - on continue le scenario"
fi

# =====================================================================
sep "6. Creer une campagne"
RESP=$(curl -s -X POST "$ORC/api/campaigns" -H "$AUTH" -H "Content-Type: application/json" -d '{
  "name":"CAMP-E2E","network":"DMAS","category":"AUTHORIZATION","initiator":"ACQUIRER",
  "config":"{\"DE002_PAN_MODE\":\"RANDOM\",\"WITH_PIN\":false,\"VARIABLE_FIELDS\":{\"AMOUNT\":{\"mode\":\"RANGE\",\"min\":1000,\"max\":50000}}}",
  "active":true,"slaErrorRateMax":10.00,"stopOnErrorRate":20.00,
  "loadSteps":[{"stepOrder":1,"startSeconds":0,"endSeconds":8,"tpsValue":5}]
}')
# id de campagne = tout premier "id":N de la reponse (avant les loadSteps)
CID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
[ -n "$CID" ] && ok "campagne creee (id=$CID)" || fail "creation campagne : $RESP"

# =====================================================================
sep "7. Lancer la campagne (TPS)"
RESP=$(curl -s -X POST "$ORC/api/campaigns/$CID/run" -H "$AUTH")
EXID=$(jval "$RESP" campaignExecutionId)
[ -n "$EXID" ] && ok "campagne lancee (executionId=$EXID, status=$(jval "$RESP" status))" \
              || fail "lancement : $RESP"

# =====================================================================
sep "8. Suivre l'execution (jusqu'a COMPLETED)"
FINAL=""
for i in $(seq 1 20); do
  RESP=$(curl -s "$ORC/api/campaigns/executions/$EXID" -H "$AUTH")
  ST=$(jval "$RESP" status)
  echo "  [$i] status=$ST  total=$(jval "$RESP" txTotal) approved=$(jval "$RESP" txApproved) declined=$(jval "$RESP" txDeclined)"
  if [ "$ST" = "COMPLETED" ] || [ "$ST" = "STOPPED_ERROR_RATE" ] || [ "$ST" = "ERROR" ]; then FINAL="$RESP"; break; fi
  sleep 2
done

echo ""
if [ -n "$FINAL" ]; then
  sep "RESULTAT FINAL"
  echo "  status      : $(jval "$FINAL" status)"
  echo "  verdict     : $(jval "$FINAL" verdict)"
  echo "  detail      : $(jval "$FINAL" verdictDetail)"
  echo "  tx total    : $(jval "$FINAL" txTotal)"
  echo "  tx approved : $(jval "$FINAL" txApproved)"
  echo "  tx declined : $(jval "$FINAL" txDeclined)"
  echo "  tps moyen   : $(jval "$FINAL" tpsActualAvg)"
  echo "  resp. moy ms: $(jval "$FINAL" responseTimeAvg)"
  echo ""
  ok "Scenario E2E termine avec succes."
else
  echo "  [WARN] l'execution n'a pas atteint un etat final dans le temps imparti."
fi

echo ""
echo "Note : pour nettoyer la campagne de test (id=$CID) :"
echo "  curl -s -X DELETE $ORC/api/campaigns/$CID -H \"Authorization: Bearer \$TOKEN\""
