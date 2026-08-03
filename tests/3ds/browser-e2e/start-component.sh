#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"

component="${1:-}"
case "$component" in
  issuing)
    require_var CARD_ISSUING_DB_PASSWORD
    export CARD_ISSUING_CORE_BANKING_SANDBOX_ENABLED=true
    export CARD_ISSUING_LOG_FILE="$LOG_DIR/card-issuing-app.log"
    start_jar sg-card-issuing card-issuing "$ISSUING_URL/api/issuing/v1/health" \
      --spring.profiles.active=connected-e2e --server.address=127.0.0.1
    ;;
  gateway)
    export CARD_NETWORK_MASTERCARD_ENABLED=true
    export CARD_NETWORK_MASTERCARD_BASE_URL="${DMAS_MEMBER_URL:-http://127.0.0.1:8084}"
    export CARD_NETWORK_VISA_ENABLED=false
    export CARD_NETWORK_GATEWAY_LOG_FILE="$LOG_DIR/card-network-gateway.log"
    start_jar sg-visa-mastercard-gateway-simulator card-network-gateway \
      "$CARD_GATEWAY_URL/api/routing/v1/health" \
      --spring.profiles.active=connected-e2e --server.address=127.0.0.1
    ;;
  acquiring)
    export ACQUIRING_SWAM_ENABLED=true
    export ACQUIRING_ISSUING_ENABLED=true
    export ACQUIRING_ISSUING_ISSUER_ID="${ECOMMERCE_LOCAL_ISSUER_ID:-002202}"
    export ACQUIRING_SWAM_BASE_URL="${SWAM_MEMBER_URL:-http://127.0.0.1:8094}"
    export ACQUIRING_CARD_GATEWAY_ENABLED=true
    export ACQUIRING_CARD_GATEWAY_BASE_URL="$CARD_GATEWAY_URL"
    export ACQUIRING_THREE_DS_ENABLED=true
    export ACQUIRING_THREE_DS_BASE_URL="$THREE_DS_MEMBER_URL"
    export ACQUIRING_LOG_FILE="$LOG_DIR/acquiring-app.log"
    start_jar sg-acquiring acquiring "$ACQUIRING_URL/api/acquiring/v1/health" \
      --spring.profiles.active=connected-e2e --server.address=127.0.0.1 \
      --acquiring.issuing.enabled=true --acquiring.network.card-gateway.enabled=true \
      --acquiring.network.swam.enabled=true --acquiring.three-ds.enabled=true
    ;;
  3ds-member)
    for name in THREE_DS_SANDBOX_HMAC_KEY THREE_DS_SANDBOX_CHALLENGE_OTP; do require_var "$name"; done
    export THREE_DS_DB_URL="$ACQUIRING_DB_URL"
    export THREE_DS_DB_USER="$ACQUIRING_DB_USER"
    export THREE_DS_DB_PASSWORD="$ACQUIRING_DB_PASSWORD"
    export THREE_DS_MEMBER_LOG_FILE="$LOG_DIR/3ds-member.log"
    start_jar sg-3ds-member 3ds-member "$THREE_DS_MEMBER_URL/api/3ds/member/v1/health" \
      --spring.profiles.active=connected-e2e --server.address=127.0.0.1
    ;;
  3ds-network)
    for name in THREE_DS_NETWORK_SANDBOX_HMAC_KEY THREE_DS_SANDBOX_CHALLENGE_OTP; do require_var "$name"; done
    export THREE_DS_NETWORK_LOG_FILE="$LOG_DIR/3ds-network.log"
    export THREE_DS_MERCHANT_SITE_BASE_URL="$SIMULATOR_URL"
    start_jar sg-3ds-network-simulator 3ds-network \
      "$THREE_DS_NETWORK_URL/api/3ds/network/v1/health" \
      --spring.profiles.active=connected-e2e --server.address=127.0.0.1
    ;;
  merchant-site)
    export ECOMMERCE_SIMULATOR_ACQUIRING_URL="$ACQUIRING_URL"
    export THREE_DS_MEMBER_BASE_URL="$THREE_DS_MEMBER_URL"
    export THREE_DS_NETWORK_BASE_URL="$THREE_DS_NETWORK_URL"
    export ECOMMERCE_SIMULATOR_PROFILE_ID_FILE="$RUNTIME/profile-id"
    export MERCHANT_SITE_SIMULATOR_LOG_FILE="$LOG_DIR/merchant-site-simulator.log"
    start_jar sg-merchant-site-simulator merchant-site-simulator \
      "$SIMULATOR_URL/api/merchant-site-simulator/v1/health" \
      --spring.profiles.active=connected-e2e --server.address=127.0.0.1
    ;;
  *)
    fail "Composant attendu: issuing|gateway|acquiring|3ds-member|3ds-network|merchant-site"
    ;;
esac

printf '[3DS BROWSER] COMPOSANT PRET - %s\n' "$component"
