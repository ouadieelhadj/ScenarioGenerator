#!/usr/bin/env bash
set -euo pipefail
set +H

# Lance l'acquereur SWAM, pose sa KEK puis sollicite ZPK et ZAK.
SG_ROOT="${SG_ROOT:-D:/MoneyCore/ScenarioGenerator}"
JAVA_BIN="${JAVA_BIN:-D:/MoneyCore/jdk-21.0.11/bin/java.exe}"
INTERFACE_ID="${INTERFACE_ID:-SWAM_MEMBER_A}"
REST_PORT="${REST_PORT:-8094}"
MEMBER_GROUP_ID="${MEMBER_GROUP_ID:-TESTGRP01}"
MODULE="sg-swam-acquirer"
LOG_FILE="$SG_ROOT/logs/$MODULE-bootstrap.log"
BASE_URL="http://localhost:$REST_PORT/api/admin/swam"
HEALTH_URL="$BASE_URL/health"

if [ -z "${KEK_CLEAR:-}" ]; then read -r -s -p "KEK/ZMK claire: " KEK_CLEAR; echo; fi
[ -n "${KEK_CLEAR:-}" ] || { echo "KEK_CLEAR requise"; exit 1; }
mkdir -p "$SG_ROOT/logs"

if ! curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
  JAR="$(find "$SG_ROOT/$MODULE/target" -maxdepth 1 -type f -name "$MODULE-*.jar" ! -name "*.original" | head -1)"
  [ -n "$JAR" ] || { echo "JAR absent : compiler $MODULE"; exit 1; }
  nohup "$JAVA_BIN" -jar "$JAR" --sg.interface="$INTERFACE_ID" \
    >"$LOG_FILE" 2>&1 &
  echo "$!" >"$SG_ROOT/logs/$MODULE.pid"
  for _ in $(seq 1 60); do curl -fsS "$HEALTH_URL" >/dev/null 2>&1 && break; sleep 1; done
fi
curl -fsS "$HEALTH_URL" >/dev/null || { tail -30 "$LOG_FILE"; exit 1; }
curl -fsS -X POST "$BASE_URL/kek/bootstrap" -H "Content-Type: application/json" \
  -d "{\"memberGroupId\":\"$MEMBER_GROUP_ID\",\"kekClear\":\"$KEK_CLEAR\"}"; echo
curl -fsS -X POST "$BASE_URL/network/signon" || {
  echo "KEK posee, mais switch injoignable : lancer d'abord le script issuer."; exit 2;
}
echo
curl -fsS -X POST "$BASE_URL/keyexchange/zpk"; echo
curl -fsS -X POST "$BASE_URL/keyexchange/zak"; echo
echo "OK - $MODULE pret sur le port $REST_PORT"
