#!/usr/bin/env bash
set -euo pipefail
set +H

# Lance le switch SWAM et pose sa KEK. Il genere ZPK/ZAK sur sollicitation.
SG_ROOT="${SG_ROOT:-D:/MoneyCore/ScenarioGenerator}"
JAVA_BIN="${JAVA_BIN:-D:/MoneyCore/jdk-21.0.11/bin/java.exe}"
REST_PORT="${REST_PORT:-8511}"
MEMBER_GROUP_ID="${MEMBER_GROUP_ID:-TESTGRP01}"
MODULE="sg-swam-issuer"
LOG_FILE="$SG_ROOT/logs/$MODULE-bootstrap.log"
BASE_URL="http://localhost:$REST_PORT"
HEALTH_URL="$BASE_URL/api/swam/issuer/health"

if [ -z "${KEK_CLEAR:-}" ]; then read -r -s -p "KEK/ZMK claire: " KEK_CLEAR; echo; fi
[ -n "${KEK_CLEAR:-}" ] || { echo "KEK_CLEAR requise"; exit 1; }
mkdir -p "$SG_ROOT/logs"

if ! curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
  JAR="$(find "$SG_ROOT/$MODULE/target" -maxdepth 1 -type f -name "$MODULE-*.jar" ! -name "*.original" | head -1)"
  [ -n "$JAR" ] || { echo "JAR absent : compiler $MODULE"; exit 1; }
  nohup "$JAVA_BIN" -jar "$JAR" >"$LOG_FILE" 2>&1 &
  echo "$!" >"$SG_ROOT/logs/$MODULE.pid"
  for _ in $(seq 1 60); do curl -fsS "$HEALTH_URL" >/dev/null 2>&1 && break; sleep 1; done
fi
curl -fsS "$HEALTH_URL" >/dev/null || { tail -30 "$LOG_FILE"; exit 1; }
curl -fsS -X POST "$BASE_URL/api/admin/swam/kek/bootstrap" -H "Content-Type: application/json" \
  -d "{\"memberGroupId\":\"$MEMBER_GROUP_ID\",\"kekClear\":\"$KEK_CLEAR\"}"; echo
echo "OK - $MODULE pret. Lancer ensuite le script acquereur."
