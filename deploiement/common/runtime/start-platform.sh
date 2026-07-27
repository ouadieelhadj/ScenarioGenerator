#!/usr/bin/env bash
set -euo pipefail

export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=platform-env.sh
source "$SCRIPT_DIR/platform-env.sh"

ACTION="${1:-start}"
BUILD="${BUILD:-true}"

MODULES=(
  sg-generator-orchestrator
  sg-dmcs-issuer
  sg-dmcs-acquirer
  sg-mc-dmas-mastercard
  sg-mc-dmas-member
  sg-swam-issuer
  sg-swam-acquirer
  sg-swam-lis-switch
  sg-swam-lis-member
  sg-mc-sms-issuer
  sg-mc-sms-acquirer
)

mkdir -p "$PLATFORM_LOG_DIR" "$PLATFORM_PID_DIR" "$ROOT/keys"

check_tools() {
  local missing=0
  for item in "$JAVA" "$MAVEN" "$PSQL"; do
    if [[ ! -f "$item" ]]; then
      echo "[ERREUR] Outil introuvable : $item" >&2
      missing=1
    fi
  done
  [[ "$missing" -eq 0 ]] || exit 1
  [[ -f "$ROOT/pom.xml" ]] || {
    echo "[ERREUR] ROOT ne contient pas le projet : $ROOT" >&2
    exit 1
  }
}

pid_alive() {
  local pid="$1"
  tasklist.exe //FI "PID eq $pid" //FO CSV //NH 2>/dev/null |
    tr -d '\r' | grep -q "\"$pid\""
}

find_jar() {
  find "$ROOT/$1/target" -maxdepth 1 -type f -name '*.jar' \
    ! -name '*.original' | head -1
}

build_all() {
  echo "[INFO] Compilation de la plateforme..."
  "$MAVEN" -f "$ROOT/pom.xml" package -DskipTests \
    -Dmaven.repo.local="$MAVEN_REPO"
  echo "[OK] Compilation terminee"
}

start_module() {
  local module="$1" pid_file="$PLATFORM_PID_DIR/$module.pid"
  local log_file="$PLATFORM_LOG_DIR/$module.log" jar pid
  if [[ -f "$pid_file" ]] && pid_alive "$(cat "$pid_file")"; then
    echo "  [DEJA UP] $module PID=$(cat "$pid_file")"
    return
  fi
  jar="$(find_jar "$module")"
  [[ -n "$jar" ]] || {
    echo "  [ERREUR] JAR absent pour $module" >&2
    return 1
  }
  nohup "$JAVA" -jar "$jar" >"$log_file" 2>&1 &
  pid=$!
  echo "$pid" >"$pid_file"
  sleep 1
  if pid_alive "$pid"; then
    echo "  [UP] $module PID=$pid"
  else
    echo "  [FAIL] $module - voir $log_file" >&2
    return 1
  fi
}

stop_module() {
  local module="$1" pid_file="$PLATFORM_PID_DIR/$module.pid" pid
  [[ -f "$pid_file" ]] || { echo "  [ARRETE] $module"; return; }
  pid="$(cat "$pid_file")"
  if pid_alive "$pid"; then
    taskkill.exe //F //PID "$pid" >/dev/null 2>&1 || true
    echo "  [STOP] $module PID=$pid"
  else
    echo "  [ARRETE] $module"
  fi
  rm -f "$pid_file"
}

status_module() {
  local module="$1" pid_file="$PLATFORM_PID_DIR/$module.pid"
  if [[ -f "$pid_file" ]] && pid_alive "$(cat "$pid_file")"; then
    echo "  [UP] $module PID=$(cat "$pid_file")"
  else
    echo "  [DOWN] $module"
  fi
}

case "$ACTION" in
  start)
    check_tools
    [[ "$BUILD" == "true" ]] && build_all
    echo "[INFO] Demarrage depuis ROOT=$ROOT"
    for module in "${MODULES[@]}"; do start_module "$module"; done
    echo "[OK] Plateforme demarree. Logs : $PLATFORM_LOG_DIR"
    ;;
  stop)
    for ((i=${#MODULES[@]}-1; i>=0; i--)); do stop_module "${MODULES[$i]}"; done
    ;;
  restart)
    "$0" stop
    "$0" start
    ;;
  status)
    for module in "${MODULES[@]}"; do status_module "$module"; done
    ;;
  build)
    check_tools
    build_all
    ;;
  *)
    echo "Usage: bash $0 {start|stop|restart|status|build}" >&2
    exit 2
    ;;
esac
