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
  kill -0 "$1" 2>/dev/null || {
    local win_pid
    win_pid="$(swam_windows_pid "$1")"
    [[ -n "$win_pid" ]] && tasklist.exe //FI "PID eq $win_pid" //FO CSV //NH 2>/dev/null |
      tr -d '\r' | grep -q "\"$win_pid\""
  }
}

swam_windows_pid() {
  local value="$1"
  ps -W 2>/dev/null | awk -v target="$value" \
    'NR > 1 && ($1 == target || $4 == target) {print $4; exit}'
}

swam_posix_pid() {
  local value="$1"
  ps -W 2>/dev/null | awk -v target="$value" \
    'NR > 1 && ($1 == target || $4 == target) {print $1; exit}'
}

swam_listening_pids() {
  local port="$1"
  netstat -ano 2>/dev/null |
    awk -v suffix=":$port" \
      '$2 ~ suffix"$" && $4=="LISTENING" {print $5}' |
    tr -d '\r' | sort -u
}

swam_port_is_listening() {
  [[ -n "$(swam_listening_pids "$1")" ]]
}

swam_stop_pid() {
  local pid="$1" label="${2:-processus SWAM}" known_alive="${3:-false}"
  local posix_pid win_pid
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  posix_pid="$(swam_posix_pid "$pid")"
  win_pid="$(swam_windows_pid "$pid")"
  [[ -n "$posix_pid" ]] && kill "$posix_pid" 2>/dev/null || true
  sleep 1
  [[ -n "$posix_pid" ]] && kill -9 "$posix_pid" 2>/dev/null || true
  [[ -n "$win_pid" ]] && taskkill.exe //T //F //PID "$win_pid" >/dev/null 2>&1 || true
  for _ in $(seq 1 10); do
    if [[ -z "$posix_pid" || ! -d "/proc/$posix_pid" ]] \
        && { [[ -z "$win_pid" ]] || ! tasklist.exe //FI "PID eq $win_pid" //FO CSV //NH 2>/dev/null |
          tr -d '\r' | grep -q "\"$win_pid\""; }; then
      echo "[STOP] $label PID=$pid"
      return 0
    fi
    sleep 1
  done
  if [[ "$known_alive" != "true" ]] && ! swam_pid_alive "$pid"; then
    return 0
  fi
  echo "[WARN] Impossible d'arreter $label PID=$pid" >&2
  return 1
}

swam_stop_port() {
  local port="$1" pid
  for pid in $(swam_listening_pids "$port"); do
    swam_stop_pid "$pid" "port $port" || true
  done
  for _ in $(seq 1 20); do
    swam_port_is_listening "$port" || return 0
    sleep 1
  done
  echo "[FAIL] Port SWAM toujours occupe apres arret : $port" >&2
  return 1
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
