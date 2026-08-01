#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

for entry in "pos-simulator.pid:POS Simulator" "serverpos.pid:ServerPOS"; do
  pid_file="$WAY_POS_PID_DIR/${entry%%:*}"
  name="${entry#*:}"
  [[ -f "$pid_file" ]] || continue
  pid="$(tr -d '\r\n' <"$pid_file")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    if kill "$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null; then
      printf '%s arrêté (PID Git Bash %s).\n' "$name" "$pid"
    else
      win_pid="$(ps -W | awk -v target="$pid" '$1 == target {print $4; exit}')"
      if [[ "$win_pid" =~ ^[0-9]+$ ]]; then
        taskkill.exe //PID "$win_pid" //T //F >/dev/null \
          || fail "Impossible d'arrêter $name (PID Windows $win_pid)"
        printf '%s arrêté (PID Windows %s).\n' "$name" "$win_pid"
      else
        fail "Impossible d'identifier le PID Windows de $name"
      fi
    fi
  fi
  rm -f "$pid_file"
done
