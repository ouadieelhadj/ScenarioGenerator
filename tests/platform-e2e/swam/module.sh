#!/usr/bin/env bash

# shellcheck disable=SC1091
source "$ROOT/deploiement/common/runtime/platform-env.sh"
SWAM_DIR="$ROOT/deploiement/swam"
SWAM_RUNTIME="${SWAM_RUNTIME:-$ROOT/runtime/swam}"

stage_check_prerequisites() {
  require_git_bash
  for command in bash curl python; do require_command "$command"; done
  require_file "$MAVEN"; require_file "$PSQL"
  require_var DB_PASSWORD
  require_var SWAM_E2E_KEK_CLEAR
  info "La KEK doit etre synthetique et reservee au LAB/RECETTE"
}
stage_build() {
  "$MAVEN" -f "$ROOT/pom.xml" \
    -pl sg-swam-issuer,sg-swam-acquirer,sg-swam-lis-member,sg-swam-lis-switch \
    -am verify -Dmaven.repo.local="$MAVEN_REPO"
}
stage_start() {
  bash "$SWAM_DIR/01-start-issuer.sh"
  bash "$SWAM_DIR/02-start-member.sh"
}
stage_bootstrap_and_provision() {
  require_var DB_PASSWORD
  PGPASSWORD="$DB_PASSWORD" "$PSQL" --no-password -v ON_ERROR_STOP=1 \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    -f "$SWAM_DIR/swam_cartes_test.sql" >/dev/null
  info "Cartes sandbox SWAM reprovisionnees de facon idempotente"
  bash "$SWAM_DIR/03-bootstrap-keys.sh"
}
stage_run_tests() {
  {
    bash "$SWAM_DIR/04-run-purchases.sh"
    RUN_SID_BOOTSTRAP=false bash "$SWAM_DIR/05-run-lis-clearing.sh"
  } 2>&1 | tee "$MODULE_RUNTIME/test-output.log"
}
stage_check_results() {
  require_file "$MODULE_RUNTIME/test-output.log"
  grep -q 'RESULTAT : PASSED' "$MODULE_RUNTIME/test-output.log" \
    || fail "Marqueur PASSED LIS absent"
  info "Autorisation SID, clearing LIS, chargeback et comptabilite valides"
}
stage_tail_logs() {
  mapfile -t files < <(find "$SWAM_RUNTIME/logs" -maxdepth 1 -type f -name '*.log' 2>/dev/null)
  ((${#files[@]})) || fail "Aucun journal SWAM disponible"
  tail -n 80 -F "${files[@]}"
}
stage_stop() { bash "$SWAM_DIR/06-stop-swam.sh"; }
