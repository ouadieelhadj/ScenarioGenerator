#!/bin/bash
# Cree/MAJ une carte de test cote issuer (PAN avec solde).
cd "$(dirname "$0")"; source lib-auth.sh
TOKEN=$(get_token "$ISS_URL")
PAN="${1:-5321962145453348}"; BAL="${2:-1000000}"
echo "=== CREATE CARD PAN=$PAN solde=$BAL centimes ==="
curl -s -X POST "$ISS_URL/api/admin/dmas/cards" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"pan\":\"$PAN\",\"pin\":\"1234\",\"balance\":$BAL}"
echo ""
