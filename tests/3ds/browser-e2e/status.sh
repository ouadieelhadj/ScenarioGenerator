#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

check() {
  local label="$1" url="$2"
  curl -fsS --connect-timeout 2 --max-time 3 "$url" >/dev/null
  printf '[OK] %s - %s\n' "$label" "$url"
}

check Issuing http://127.0.0.1:8540/api/issuing/v1/health
check Gateway http://127.0.0.1:8563/api/routing/v1/health
check Acquisition http://127.0.0.1:8550/api/acquiring/v1/health
check 3DS-Member http://127.0.0.1:8560/api/3ds/member/v1/health
check 3DS-Network http://127.0.0.1:8561/api/3ds/network/v1/health
check Merchant-Site http://127.0.0.1:8551/api/merchant-site-simulator/v1/health

printf '[OK] Boutique: http://127.0.0.1:8551/\n'
