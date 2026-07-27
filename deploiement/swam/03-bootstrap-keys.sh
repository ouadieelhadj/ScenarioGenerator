#!/usr/bin/env bash
set -euo pipefail
set +H
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

swam_require_secret SWAM_E2E_KEK_CLEAR
swam_wait "$SWAM_ISSUER_URL/api/swam/issuer/health" "SWAM Issuer"
swam_wait "$SWAM_MEMBER_URL/api/admin/swam/health" "SWAM Membre"

body="{\"memberGroupId\":\"$SWAM_MEMBER_GROUP_ID\",\"kekClear\":\"$SWAM_E2E_KEK_CLEAR\"}"
swam_post "$SWAM_ISSUER_URL/api/admin/swam/kek/bootstrap" \
  -H 'Content-Type: application/json' -d "$body" >/dev/null
swam_post "$SWAM_MEMBER_URL/api/admin/swam/kek/bootstrap" \
  -H 'Content-Type: application/json' -d "$body" >/dev/null

response="$(swam_post "$SWAM_MEMBER_URL/api/admin/swam/network/signon")"
grep -q '"success":true' <<<"$response" || {
  echo "[FAIL] Sign-on : $response" >&2
  exit 1
}

if [[ -n "$DB_PASSWORD" ]]; then
  match=""
  for _ in $(seq 1 30); do
    match="$(PGPASSWORD="$DB_PASSWORD" "$PSQL" -U "$DB_USER" -h "$DB_HOST" \
      -p "$DB_PORT" -d "$DB_NAME" -tAc "
      SELECT (i.kcv=a.kcv)::text
        FROM swam_iss_keys i JOIN swam_acq_keys a
          ON i.member_group_id=a.member_group_id AND i.key_type=a.key_type
       WHERE i.member_group_id='$SWAM_MEMBER_GROUP_ID'
         AND i.key_type='PEK' AND i.status='ACTIVE' AND a.status='ACTIVE'
       LIMIT 1;" | tr -d '[:space:]')"
    [[ "$match" == "t" || "$match" == "true" ]] && break
    sleep 1
  done
  [[ "$match" == "t" || "$match" == "true" ]] || {
    echo "[FAIL] KCV ZPK non concordants" >&2
    exit 1
  }
fi
echo "[OK] KEK injectee, sign-on effectue et cles de session echangees"
