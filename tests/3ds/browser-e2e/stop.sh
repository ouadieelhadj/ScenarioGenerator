#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
bash "$ROOT/tests/acquiring/ecommerce-e2e/05-stop.sh" LOCAL_ISSUING

# Sous Git Bash/Windows, le PID POSIX de nohup peut disparaitre alors que son
# enfant java Windows conserve le port. Le repli ci-dessous ne cible que les
# six ports reserves a ce harnais et doit etre execute dans le meme terminal
# administrateur que celui qui a demarre les composants.
mapfile -t windows_pids < <(
  for port in 8540 8563 8550 8560 8561 8551; do
    netstat -ano 2>/dev/null | awk -v suffix=":$port" \
      '$2 ~ suffix"$" && $4=="LISTENING" {print $5}'
  done | tr -d '\r' | sort -u
)
for pid in "${windows_pids[@]}"; do
  [[ "$pid" =~ ^[0-9]+$ ]] || continue
  taskkill.exe //PID "$pid" //T //F >/dev/null
  printf '[3DS BROWSER] STOP Windows PID %s\n' "$pid"
done
sleep 2
for port in 8540 8563 8550 8560 8561 8551; do
  if netstat -ano 2>/dev/null | awk -v suffix=":$port" \
      '$2 ~ suffix"$" && $4=="LISTENING" {found=1} END {exit found ? 0 : 1}'; then
    printf '[3DS BROWSER] ERREUR - port %s toujours occupe\n' "$port" >&2
    exit 1
  fi
done
printf '[3DS BROWSER] Tous les composants sont arretes\n'
