#!/usr/bin/env bash

set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ISSUING_E2E_CONFIG_FILE="${ISSUING_E2E_CONFIG_FILE:-$ROOT/runtime/issuing-connected-e2e/connected-e2e.env}"
if [[ -f "$ISSUING_E2E_CONFIG_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ISSUING_E2E_CONFIG_FILE"
  set +a
fi
# shellcheck source=../../deploiement/common/runtime/platform-env.sh
source "$ROOT/deploiement/common/runtime/platform-env.sh"

RUNTIME="${ISSUING_E2E_RUNTIME:-$ROOT/runtime/issuing-connected-e2e}"
PID_DIR="$RUNTIME/pids"
LOG_DIR="$RUNTIME/logs"
mkdir -p "$PID_DIR" "$LOG_DIR"

fail() {
  echo "[Issuing services] FAIL - $*" >&2
  exit 1
}

for name in CARD_ISSUING_DB_PASSWORD WAY_POS_DB_PASSWORD \
  WAY_POS_PAN_PEPPER WAY_POS_OUTBOX_KEY_HEX; do
  [[ -n "${!name-}" ]] || fail "$name est obligatoire"
done

[[ -x "$JAVA" ]] || fail "Java introuvable : $JAVA"
command -v curl >/dev/null 2>&1 || fail "curl est obligatoire"

# Les quatre applications Spring tournent simultanement sur la machine de
# recette. Une limite explicite evite que l'ergonomie par defaut de la JVM
# consomme toute la memoire native de l'hote. La recette peut la surcharger.
export JAVA_TOOL_OPTIONS="${ISSUING_E2E_JAVA_TOOL_OPTIONS:--Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC}"

pid_alive() {
  local pid="$1"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

wait_http() {
  local url="$1" label="$2" pid="$3"
  for _ in $(seq 1 30); do
    curl -fsS --connect-timeout 2 --max-time 3 "$url" \
      >/dev/null 2>&1 && {
      echo "[Issuing services] UP - $label"
      return
    }
    if ! pid_alive "$pid"; then
      tail -80 "$LOG_DIR/$label.log" >&2 || true
      fail "$label arrete; voir $LOG_DIR/$label.log"
    fi
    sleep 1
  done
  fail "$label indisponible : $url"
}

start_jar() {
  local module="$1" label="$2" health="$3"
  shift 3
  local jar="$ROOT/$module/target/$module-1.0.0-SNAPSHOT.jar"
  local pid_file="$PID_DIR/$module.pid" pid

  [[ -f "$jar" ]] || fail "JAR absent : $jar"
  if curl -fsS --connect-timeout 2 --max-time 3 "$health" \
      >/dev/null 2>&1; then
    echo "[Issuing services] UP - $label deja demarre"
    return
  fi
  if [[ -f "$pid_file" ]] && pid_alive "$(cat "$pid_file")"; then
    wait_http "$health" "$label" "$(cat "$pid_file")"
    return
  fi
  rm -f -- "$pid_file"

  nohup "$JAVA" -jar "$jar" "$@" >"$LOG_DIR/$label.log" 2>&1 &
  pid=$!
  printf '%s\n' "$pid" >"$pid_file"
  wait_http "$health" "$label" "$pid"
}

export CARD_ISSUING_CORE_BANKING_SANDBOX_ENABLED=true
export CARD_ISSUING_LOG_FILE="$LOG_DIR/card-issuing-app.log"
start_jar sg-card-issuing card-issuing \
  http://127.0.0.1:8540/api/issuing/v1/health \
  --spring.profiles.active=connected-e2e \
  --server.address=127.0.0.1

export WAY_POS_LOG_FILE="$LOG_DIR/way-pos-server-app.log"
export WAY_POS_LMK_FILE="${ISSUING_E2E_WAY_POS_LMK_FILE:-$RUNTIME/keys/way-pos.lmk}"
export WAY_POS_LMK_REBUILD="${ISSUING_E2E_WAY_POS_LMK_REBUILD:-true}"
DMAS_LMK_FILE_BEFORE_WAY_POS="${DMAS_LMK_FILE-}"
export DMAS_LMK_FILE="$WAY_POS_LMK_FILE"
mkdir -p "$(dirname "$WAY_POS_LMK_FILE")"
start_jar sg-way-pos-server way-pos-server \
  http://127.0.0.1:8530/api/routing/v1/health \
  --spring.profiles.active=connected-e2e \
  --server.address=127.0.0.1
export DMAS_LMK_FILE="$DMAS_LMK_FILE_BEFORE_WAY_POS"

export SWAM_LOG_DIR="$LOG_DIR/swam"
export SWAM_PID_DIR="$PID_DIR/swam"
bash "$ROOT/deploiement/swam/01-start-issuer.sh"

export DMAS_DMC_LOG_DIR="$LOG_DIR/dmas"
export DMAS_DMC_PID_DIR="$PID_DIR/dmas"
DMAS_STARTER="$ROOT/deploiement/mastercard/dmas-dmc/01-start-mastercard.sh"
DMAS_HEALTH_URL="${DMAS_MASTERCARD_URL:-http://127.0.0.1:8501}/auth/login"
bash "$DMAS_STARTER" &
dmas_starter_pid=$!
for _ in $(seq 1 90); do
  dmas_http_code="$(curl -s --connect-timeout 2 --max-time 3 \
    -o /dev/null -w '%{http_code}' "$DMAS_HEALTH_URL" || true)"
  if [[ "$dmas_http_code" != "000" ]]; then
    if kill -0 "$dmas_starter_pid" 2>/dev/null; then
      kill "$dmas_starter_pid" 2>/dev/null || true
    fi
    wait "$dmas_starter_pid" 2>/dev/null || true
    echo "[Issuing services] UP - dmas-mastercard HTTP=$dmas_http_code"
    break
  fi
  if ! kill -0 "$dmas_starter_pid" 2>/dev/null; then
    wait "$dmas_starter_pid" || fail "demarrage DMAS en echec"
  fi
  sleep 1
done
[[ "${dmas_http_code:-000}" != "000" ]] \
  || fail "DMAS Mastercard indisponible : $DMAS_HEALTH_URL"

echo "[Issuing services] SUCCESS - Issuing, ServerPOS, SWAM et DMAS demarres"
