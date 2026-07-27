#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

for pid_file in "$SWAM_PID_DIR"/*.pid; do
  [[ -e "$pid_file" ]] || continue
  pid="$(cat "$pid_file")"
  module="$(basename "$pid_file" .pid)"
  if swam_pid_alive "$pid"; then
    taskkill.exe //F //PID "$pid" >/dev/null 2>&1 || true
    echo "[STOP] $module PID=$pid"
  fi
  rm -f "$pid_file"
done
