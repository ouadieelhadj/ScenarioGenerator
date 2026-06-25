#!/bin/bash
# Fonctions communes : login + token (sans jq). A sourcer dans les autres scripts.
ORCH_URL="http://localhost:8080"
ACQ_URL="http://localhost:8084"
ISS_URL="http://localhost:8501"
USER_LOGIN="admin"
USER_PASS="Admin123!"

get_token() {  # $1 = base url
  curl -s -X POST "$1/auth/login" -H "Content-Type: application/json" \
    -d "{\"login\":\"$USER_LOGIN\",\"password\":\"$USER_PASS\"}" \
    | grep -o '"token":"[^"]*"' | cut -d'"' -f4
}
