#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib-dmas-dmc.sh"

token="$(dmas_login "$DMAS_MEMBER_URL")"
[[ -n "$token" ]] || { echo "[FAIL] Authentification DMAS membre" >&2; exit 1; }

dmas_auth_post "$DMAS_MEMBER_URL" \
  "/api/admin/dmas/network/signon?bank=$DMAS_MEMBER_BANK_CODE" "$token" \
  >/dev/null
echo "[OK] Sign-on DMAS membre"

dmas_auth_post "$DMAS_MEMBER_URL" \
  "/api/admin/dmas/keys/solicit?bank=$DMAS_MEMBER_BANK_CODE" "$token" \
  >/dev/null

dmas_require_secret DB_PASSWORD "Mot de passe PostgreSQL"
match=""
for _ in $(seq 1 30); do
  match="$(dmas_psql --tuples-only --no-align --command="
SELECT CASE WHEN m.kcv=i.kcv THEN 'MATCH' ELSE 'DIFF' END
  FROM mc_dmas_member_keys m
  JOIN mc_dmas_mastercard_keys i
    ON i.member_group_id=m.member_group_id
   AND i.key_type=m.key_type
 WHERE m.member_group_id='$DMAS_MEMBER_GROUP_ID'
   AND m.key_type='PEK'
   AND m.status='ACTIVE'
   AND i.status='ACTIVE'
 ORDER BY m.id DESC, i.id DESC
 LIMIT 1;" | tr -d '[:space:]')"
  [[ "$match" == "MATCH" ]] && break
  sleep 1
done
[[ "$match" == "MATCH" ]] || {
  echo "[FAIL] Echange PEK termine sans KCV concordants" >&2
  exit 1
}
echo "[OK] Echange dynamique PEK; KCV concordants des deux cotes"
