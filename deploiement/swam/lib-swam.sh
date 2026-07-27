#!/usr/bin/env bash

SWAM_DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../common/runtime/platform-env.sh
source "$SWAM_DEPLOY_DIR/../common/runtime/platform-env.sh"

export SWAM_RUNTIME="${SWAM_RUNTIME:-$ROOT/runtime/swam}"
export SWAM_LOG_DIR="${SWAM_LOG_DIR:-$SWAM_RUNTIME/logs}"
export SWAM_PID_DIR="${SWAM_PID_DIR:-$SWAM_RUNTIME/pids}"
export SWAM_ISSUER_URL="${SWAM_ISSUER_URL:-http://localhost:8511}"
export SWAM_MEMBER_URL="${SWAM_MEMBER_URL:-http://localhost:8094}"
export SWAM_MEMBER_GROUP_ID="${SWAM_MEMBER_GROUP_ID:-TESTGRP01}"

mkdir -p "$SWAM_LOG_DIR" "$SWAM_PID_DIR"

swam_wait() {
  local url="$1" label="$2"
  for _ in $(seq 1 90); do
    curl -fsS "$url" >/dev/null 2>&1 && { echo "[OK] $label"; return; }
    sleep 1
  done
  echo "[FAIL] $label indisponible : $url" >&2
  return 1
}

swam_pid_alive() {
  tasklist.exe //FI "PID eq $1" //FO CSV //NH 2>/dev/null |
    tr -d '\r' | grep -q "\"$1\""
}

swam_build_module() {
  "$MAVEN" -f "$ROOT/pom.xml" -pl "$1" -am package -DskipTests \
    -Dmaven.repo.local="$MAVEN_REPO"
}

swam_start_module() {
  local module="$1" iface="$2" health="$3"
  local pid_file="$SWAM_PID_DIR/$module.pid" log="$SWAM_LOG_DIR/$module.log"
  local jar pid
  if [[ -f "$pid_file" ]] && swam_pid_alive "$(cat "$pid_file")"; then
    swam_wait "$health" "$module deja demarre"
    return
  fi
  jar="$(find "$ROOT/$module/target" -maxdepth 1 -name '*.jar' \
    ! -name '*.original' | head -1)"
  if [[ -z "$jar" ]]; then
    swam_build_module "$module"
    jar="$(find "$ROOT/$module/target" -maxdepth 1 -name '*.jar' \
      ! -name '*.original' | head -1)"
  fi
  [[ -n "$jar" ]] || { echo "[FAIL] JAR absent : $module" >&2; return 1; }
  nohup "$JAVA" -jar "$jar" "--sg.interface=$iface" >"$log" 2>&1 &
  pid=$!
  echo "$pid" >"$pid_file"
  swam_wait "$health" "$module"
}

swam_post() {
  curl -fsS -X POST "$@"
}

swam_require_secret() {
  local name="$1" value="${!1:-}"
  if [[ -z "$value" ]]; then
    read -r -s -p "$name: " value
    echo
    printf -v "$name" '%s' "$value"
    export "$name"
  fi
  [[ -n "${!name:-}" ]] || { echo "[FAIL] $name obligatoire" >&2; return 1; }
}
