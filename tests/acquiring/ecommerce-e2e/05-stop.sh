#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh" "${1:-}"
stop_module sg-ecommerce-simulator ecommerce-simulator
stop_module sg-acquiring acquiring
stop_module sg-card-issuing card-issuing
if [[ -f "$RUNTIME/route" ]]; then ROUTE="$(tr -d '[:space:]' <"$RUNTIME/route")"; fi
if [[ "$ROUTE" == "SWAM" ]]; then
  export SWAM_RUNTIME="$RUNTIME/swam" SWAM_LOG_DIR="$RUNTIME/swam/logs" SWAM_PID_DIR="$RUNTIME/swam/pids"
  if ! bash "$ROOT/deploiement/swam/06-stop-swam.sh"; then
    # taskkill peut recevoir un PID POSIX incorrect sous Git Bash. Le PID
    # Windows fourni par netstat est alors arrete explicitement.
    for pid in $(for port in 8510 8511 8094; do
      netstat -ano 2>/dev/null | awk -v suffix=":$port" \
        '$2 ~ suffix"$" && $4=="LISTENING" {print $5}'
    done | tr -d '\r' | sort -u); do
      powershell.exe -NoProfile -Command \
        "Stop-Process -Id $pid -Force -ErrorAction Stop" >/dev/null
    done
    sleep 2
    for port in 8510 8511 8094; do
      netstat -ano 2>/dev/null | awk -v suffix=":$port" \
        '$2 ~ suffix"$" && $4=="LISTENING" {found=1} END {exit found ? 0 : 1}' \
        && fail "Port SWAM toujours occupe apres arret: $port"
    done
    printf '[ECOM E2E] STOP - services SWAM (repli PID Windows)\n'
  fi
elif [[ "$ROUTE" == "DMAS_MASTERCARD" ]]; then
  export DMAS_DMC_RUNTIME="$RUNTIME/dmas-dmc" DMAS_DMC_LOG_DIR="$RUNTIME/dmas-dmc/logs" DMAS_DMC_PID_DIR="$RUNTIME/dmas-dmc/pids"
  bash "$ROOT/deploiement/mastercard/dmas-dmc/08-stop-dmas-dmc.sh"
fi
rm -f -- "$RUNTIME/route"
