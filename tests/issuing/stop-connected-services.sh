#!/usr/bin/env bash

set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME="${ISSUING_E2E_RUNTIME:-$ROOT/runtime/issuing-connected-e2e}"
PID_DIR="$RUNTIME/pids"

pid_matches_module() {
  local pid="$1" module="$2"
  powershell.exe -NoProfile -Command \
    "\$p=Get-CimInstance Win32_Process -Filter \"ProcessId=$pid\" -ErrorAction SilentlyContinue; if (\$p -and \$p.CommandLine -like '*$module*') { exit 0 }; exit 1" \
    >/dev/null 2>&1
}

stop_pid_file() {
  local pid_file="$1" label="$2" module="$3" pid
  [[ -f "$pid_file" ]] || return
  pid="$(tr -d '[:space:]' <"$pid_file")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    if pid_matches_module "$pid" "$module"; then
      taskkill.exe //T //F //PID "$pid" >/dev/null 2>&1 \
        || kill "$pid" 2>/dev/null || true
      echo "[Issuing services] STOP - $label PID=$pid"
    else
      echo "[Issuing services] WARN - PID=$pid ne correspond pas a $module; non arrete" >&2
    fi
  fi
  rm -f -- "$pid_file"
}

stop_pid_file "$PID_DIR/dmas/sg-mc-dmas-mastercard.pid" \
  dmas-mastercard sg-mc-dmas-mastercard
stop_pid_file "$PID_DIR/swam/sg-swam-issuer.pid" \
  swam-issuer sg-swam-issuer
stop_pid_file "$PID_DIR/swam/sg-swam-acquirer.pid" \
  swam-acquirer sg-swam-acquirer
stop_pid_file "$PID_DIR/sg-way-pos-server.pid" \
  way-pos-server sg-way-pos-server
stop_pid_file "$PID_DIR/sg-card-issuing.pid" \
  card-issuing sg-card-issuing

echo "[Issuing services] STOP complete"
