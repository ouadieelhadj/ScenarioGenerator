#!/bin/bash
# Active la session permanente jPOS (issuer -> acquereur). PREREQUIS avant toute autorisation jpos.
cd "$(dirname "$0")"; source lib-auth.sh
TOKEN=$(get_token "$ISS_URL")
echo "=== SIGN-ON (active la session permanente) ==="
curl -s --max-time 15 -X POST "$ISS_URL/api/admin/dmas/jpos/signon" -H "Authorization: Bearer $TOKEN"
echo ""
