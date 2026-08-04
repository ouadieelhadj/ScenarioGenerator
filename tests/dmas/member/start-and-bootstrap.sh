#!/usr/bin/env bash
set -euo pipefail
set +H

# Lance sg-mc-dmas-member et forme ses cles sous le LMK local.
# Les secrets viennent de l'environnement ou sont demandes sans affichage.

SG_ROOT="${SG_ROOT:-D:/MoneyCore/ScenarioGenerator}"
JAVA_BIN="${JAVA_BIN:-D:/MoneyCore/jdk-21.0.11/bin/java.exe}"
REST_PORT="${REST_PORT:-8084}"
INTERFACE_ID="${INTERFACE_ID:-DMAS_BANK_A}"
ADMIN_LOGIN="${ADMIN_LOGIN:-admin}"
MEMBER_GROUP_ID="${MEMBER_GROUP_ID:-TESTGRP01}"
BANK_CODE="${BANK_CODE:-022905}"
MODULE="sg-mc-dmas-member"
LOG_FILE="$SG_ROOT/logs/$MODULE-bootstrap.log"
BASE_URL="http://localhost:$REST_PORT"

secret() {
  local name="$1" prompt="$2"
  if [ -z "${!name:-}" ]; then
    read -r -s -p "$prompt: " "$name"
    echo
  fi
  [ -n "${!name:-}" ] || { echo "$name requis"; exit 1; }
}
find_jar() {
  find "$SG_ROOT/$MODULE/target" -maxdepth 1 -type f -name "$MODULE-*.jar" \
    ! -name "*.original" | head -1
}
login() {
  curl -fsS -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" \
    -d "{\"login\":\"$ADMIN_LOGIN\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

secret ADMIN_PASSWORD "Mot de passe admin"
secret KEK_CLEAR "KEK claire"
secret PEK_CLEAR "PEK/TPK claire"
secret MDK_CLEAR "MDK claire"
mkdir -p "$SG_ROOT/logs"

TOKEN="$(login 2>/dev/null || true)"
if [ -z "$TOKEN" ]; then
  JAR="$(find_jar)"
  [ -n "$JAR" ] || { echo "JAR absent : compiler $MODULE"; exit 1; }
  nohup "$JAVA_BIN" -jar "$JAR" \
    --sg.interface="$INTERFACE_ID" \
    --dmas.security.routing-permit-all="${DMAS_ROUTING_PERMIT_ALL:-true}" \
    >"$LOG_FILE" 2>&1 &
  echo "$!" >"$SG_ROOT/logs/$MODULE.pid"
  for _ in $(seq 1 60); do
    TOKEN="$(login 2>/dev/null || true)"; [ -n "$TOKEN" ] && break; sleep 1
  done
fi
[ -n "$TOKEN" ] || { echo "API indisponible sur $BASE_URL"; tail -30 "$LOG_FILE"; exit 1; }

AUTH=(-H "Authorization: Bearer $TOKEN")
JSON=(-H "Content-Type: application/json")
curl -fsS -X POST "$BASE_URL/api/admin/dmas/kek/bootstrap" "${AUTH[@]}" "${JSON[@]}" \
  -d "{\"memberGroupId\":\"$MEMBER_GROUP_ID\",\"kekClear\":\"$KEK_CLEAR\"}"; echo
curl -fsS -X POST "$BASE_URL/api/admin/dmas/keys/inject?clear=$PEK_CLEAR&keyType=PEK&bank=$BANK_CODE" \
  "${AUTH[@]}"; echo
curl -fsS -X POST "$BASE_URL/api/admin/dmas/mdk/bootstrap" "${AUTH[@]}" "${JSON[@]}" \
  -d "{\"mdkClear\":\"$MDK_CLEAR\",\"bank\":\"$BANK_CODE\"}"; echo
curl -fsS "$BASE_URL/api/admin/dmas/keys/current?bank=$BANK_CODE&keyType=PEK" "${AUTH[@]}"; echo
curl -fsS -X POST "$BASE_URL/api/admin/dmas/network/signon?bank=$BANK_CODE" \
  "${AUTH[@]}"; echo
curl -fsS "$BASE_URL/api/admin/dmas/network/status?bank=$BANK_CODE" \
  "${AUTH[@]}"; echo
echo "OK - $MODULE pret sur $BASE_URL"
