#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh" "${1:-}"

for name in CARD_ISSUING_DB_PASSWORD DB_PASSWORD; do require_var "$name"; done
if [[ "$ROUTE" == "DMAS_MASTERCARD" ]]; then
  require_var DMAS_ADMIN_PASSWORD
fi
if [[ "$ROUTE" == "SWAM" && "${ECOMMERCE_BOOTSTRAP_NETWORK_KEYS:-false}" == "true" ]]; then
  require_var SWAM_E2E_KEK_CLEAR
fi
export JAVA_TOOL_OPTIONS="${ISSUING_E2E_JAVA_TOOL_OPTIONS:--Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC}"

export CARD_ISSUING_CORE_BANKING_SANDBOX_ENABLED=true
export CARD_ISSUING_LOG_FILE="$LOG_DIR/card-issuing-app.log"
start_jar sg-card-issuing card-issuing "$ISSUING_URL/api/issuing/v1/health" \
  --spring.profiles.active=connected-e2e --server.address=127.0.0.1

if [[ "$ROUTE" == "SWAM" ]]; then
  curl -fsS http://127.0.0.1:8511/api/swam/issuer/health >/dev/null 2>&1 \
    && fail "SWAM Issuer est deja actif"
  curl -fsS http://127.0.0.1:8094/api/admin/swam/health >/dev/null 2>&1 \
    && fail "SWAM Membre est deja actif"
  export SWAM_RUNTIME="$RUNTIME/swam"
  export SWAM_LOG_DIR="$SWAM_RUNTIME/logs"
  export SWAM_PID_DIR="$SWAM_RUNTIME/pids"
  export SWAM_AUTHORIZATION_OWNER=EXTERNAL_MEMBER_SIMULATOR
  bash "$ROOT/deploiement/swam/01-start-issuer.sh"
  bash "$ROOT/deploiement/swam/02-start-member.sh"
  if [[ "${ECOMMERCE_BOOTSTRAP_NETWORK_KEYS:-false}" == "true" ]]; then
    bash "$ROOT/deploiement/swam/03-bootstrap-keys.sh"
  else
    bash "$ROOT/deploiement/swam/03c-signon-and-key-exchange.sh"
  fi
elif [[ "$ROUTE" == "DMAS_MASTERCARD" ]]; then
  export DMAS_DMC_RUNTIME="$RUNTIME/dmas-dmc"
  export DMAS_DMC_LOG_DIR="$DMAS_DMC_RUNTIME/logs"
  export DMAS_DMC_PID_DIR="$DMAS_DMC_RUNTIME/pids"
  export DMAS_AUTHORIZATION_OWNER=EXTERNAL_MEMBER_SIMULATOR
  # Le endpoint machine-to-machine reste protege par defaut. Cette ouverture
  # est strictement limitee au harnais local connected-e2e.
  export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Ddmas.security.routing-permit-all=true"
  bash "$ROOT/deploiement/mastercard/dmas-dmc/01-start-mastercard.sh"
  bash "$ROOT/deploiement/mastercard/dmas-dmc/02-start-member.sh"
  if [[ "${ECOMMERCE_BOOTSTRAP_NETWORK_KEYS:-false}" == "true" ]]; then
    bash "$ROOT/deploiement/mastercard/dmas-dmc/03a-bootstrap-mastercard.sh"
    bash "$ROOT/deploiement/mastercard/dmas-dmc/03b-bootstrap-member.sh"
  fi
  bash "$ROOT/deploiement/mastercard/dmas-dmc/03c-signon-and-key-exchange.sh"
fi

export ACQUIRING_DB_URL="jdbc:postgresql://${DB_HOST:-localhost}:${DB_PORT:-5432}/${DB_NAME:-scenariogenerator}"
export ACQUIRING_DB_USER="${DB_USER:-postgres}"
export ACQUIRING_DB_PASSWORD="$DB_PASSWORD"
export ACQUIRING_SWAM_ENABLED=true
export ACQUIRING_ISSUING_ENABLED=true
export ACQUIRING_ISSUING_ISSUER_ID="${ECOMMERCE_LOCAL_ISSUER_ID:-002202}"
export ACQUIRING_SWAM_BASE_URL="${SWAM_MEMBER_URL:-http://127.0.0.1:8094}"
export ACQUIRING_CARD_GATEWAY_ENABLED=true
export ACQUIRING_CARD_GATEWAY_BASE_URL="$CARD_GATEWAY_URL"
export ACQUIRING_LOG_FILE="$LOG_DIR/acquiring-app.log"
if [[ "${THREE_DS_ENABLED:-false}" == "true" ]]; then
  export ACQUIRING_THREE_DS_ENABLED=true
  export ACQUIRING_THREE_DS_BASE_URL="$THREE_DS_MEMBER_URL"
fi

export CARD_NETWORK_MASTERCARD_ENABLED=true
export CARD_NETWORK_MASTERCARD_BASE_URL="${DMAS_MEMBER_URL:-http://127.0.0.1:8084}"
export CARD_NETWORK_VISA_ENABLED=false
export CARD_NETWORK_GATEWAY_LOG_FILE="$LOG_DIR/card-network-gateway.log"
start_jar sg-visa-mastercard-gateway-simulator card-network-gateway \
  "$CARD_GATEWAY_URL/api/routing/v1/health" \
  --spring.profiles.active=connected-e2e --server.address=127.0.0.1

start_jar sg-acquiring acquiring "$ACQUIRING_URL/api/acquiring/v1/health" \
  --spring.profiles.active=connected-e2e --server.address=127.0.0.1 \
  --acquiring.issuing.enabled=true --acquiring.network.card-gateway.enabled=true \
  --acquiring.network.swam.enabled=true

export ECOMMERCE_SIMULATOR_ACQUIRING_URL="$ACQUIRING_URL"
if [[ "${THREE_DS_ENABLED:-false}" == "true" ]]; then
  require_var THREE_DS_SANDBOX_HMAC_KEY
  require_var THREE_DS_NETWORK_SANDBOX_HMAC_KEY
  require_var THREE_DS_SANDBOX_CHALLENGE_OTP
  export THREE_DS_DB_URL="$ACQUIRING_DB_URL"
  export THREE_DS_DB_USER="$ACQUIRING_DB_USER"
  export THREE_DS_DB_PASSWORD="$ACQUIRING_DB_PASSWORD"
  export THREE_DS_MEMBER_LOG_FILE="$LOG_DIR/3ds-member.log"
  export THREE_DS_NETWORK_LOG_FILE="$LOG_DIR/3ds-network.log"
  export THREE_DS_MERCHANT_SITE_BASE_URL="$SIMULATOR_URL"
  start_jar sg-3ds-member 3ds-member \
    "$THREE_DS_MEMBER_URL/api/3ds/member/v1/health" \
    --spring.profiles.active=connected-e2e --server.address=127.0.0.1
  start_jar sg-3ds-network-simulator 3ds-network \
    "$THREE_DS_NETWORK_URL/api/3ds/network/v1/health" \
    --spring.profiles.active=connected-e2e --server.address=127.0.0.1
fi
export THREE_DS_MEMBER_BASE_URL="$THREE_DS_MEMBER_URL"
export THREE_DS_NETWORK_BASE_URL="$THREE_DS_NETWORK_URL"
export ECOMMERCE_SIMULATOR_PROFILE_ID_FILE="$RUNTIME/profile-id"
export MERCHANT_SITE_SIMULATOR_LOG_FILE="$LOG_DIR/merchant-site-simulator.log"
start_jar sg-merchant-site-simulator merchant-site-simulator \
  "$SIMULATOR_URL/api/merchant-site-simulator/v1/health" \
  --spring.profiles.active=connected-e2e --server.address=127.0.0.1
printf '%s\n' "$ROUTE" >"$RUNTIME/route"
printf '[ECOM E2E] Services demarres pour la route %s.\n' "$ROUTE"
