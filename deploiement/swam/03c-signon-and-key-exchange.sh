#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-swam.sh"

swam_wait "$SWAM_ISSUER_URL/api/swam/issuer/health" "SWAM Issuer"
swam_wait "$SWAM_MEMBER_URL/api/admin/swam/health" "SWAM Membre"

response="$(swam_post "$SWAM_MEMBER_URL/api/admin/swam/network/signon")"
grep -q '"success":true' <<<"$response" || {
  echo "[FAIL] Sign-on membre : $response" >&2
  exit 1
}
echo "[OK] Sign-on membre accepté par le switch"

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
    echo "[FAIL] KCV des clés de session switch/membre non concordants" >&2
    exit 1
  }
  echo "[OK] KCV des clés de session switch/membre concordants"
else
  echo "[WARN] DB_PASSWORD absent : contrôle croisé des KCV non exécuté"
fi

echo "[OK] Échange des clés de session terminé"
