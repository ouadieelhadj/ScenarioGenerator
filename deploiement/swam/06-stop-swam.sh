#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

failed=0

for pid_file in "$SWAM_PID_DIR"/*.pid; do
  [[ -e "$pid_file" ]] || continue
  pid="$(cat "$pid_file")"
  module="$(basename "$pid_file" .pid)"
  if swam_pid_alive "$pid"; then
    swam_stop_pid "$pid" "$module" || true
  fi
  rm -f "$pid_file"
done

url_port() {
  printf '%s' "$1" | sed -nE 's#^[a-zA-Z]+://[^/:]+:([0-9]+).*$#\1#p'
}

if [[ -n "${SWAM_STOP_PORTS:-}" ]]; then
  ports="$SWAM_STOP_PORTS"
else
  ports="${SWAM_ISSUER_ISO_PORT:-8510} \
$(url_port "$SWAM_ISSUER_URL") \
$(url_port "$SWAM_MEMBER_URL") \
${SWAM_LIS_MEMBER_HTTP_PORT:-8521} \
${SWAM_LIS_SWITCH_HTTP_PORT:-8522}"
fi

unique_ports="$(printf '%s\n' $ports | awk 'NF && !seen[$0]++')"
port_pids=""
for port in $unique_ports; do
  [[ "$port" =~ ^[0-9]+$ ]] || continue
  port_pids="$port_pids $(swam_listening_pids "$port")"
done

for pid in $(printf '%s\n' $port_pids | awk 'NF && !seen[$0]++'); do
  swam_stop_pid "$pid" "service detecte par port" true || failed=1
done

# Une seule attente globale, même lorsqu'un processus écoute sur plusieurs
# ports (par exemple l'issuer sur REST + ISO).
for _ in $(seq 1 20); do
  busy=false
  for port in $unique_ports; do
    if swam_port_is_listening "$port"; then
      busy=true
      break
    fi
  done
  [[ "$busy" == "false" ]] && break
  sleep 1
done

for port in $unique_ports; do
  if swam_port_is_listening "$port"; then
    echo "[FAIL] Port SWAM toujours occupe apres arret : $port" >&2
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  echo "[FAIL] Certains services SWAM sont encore actifs." >&2
  echo "       Relancer ce script dans un terminal avec les droits necessaires." >&2
  exit 1
fi

echo "[OK] Tous les services SWAM sont arretes et les ports sont libres."
