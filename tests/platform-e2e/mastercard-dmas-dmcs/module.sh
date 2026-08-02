#!/usr/bin/env bash

# shellcheck disable=SC1091
source "$ROOT/deploiement/common/runtime/platform-env.sh"
DMAS_DIR="$ROOT/deploiement/mastercard/dmas-dmc"
DMAS_RUNTIME="${DMAS_DMC_RUNTIME:-$ROOT/runtime/dmas-dmc}"

stage_check_prerequisites() {
  require_git_bash
  for command in bash curl python; do require_command "$command"; done
  require_file "$MAVEN"; require_file "$PSQL"
  for name in DB_PASSWORD DMAS_ADMIN_PASSWORD DMAS_KEK_CLEAR \
    DMAS_MDK_CLEAR DMAS_TEST_PIN; do require_var "$name"; done
  info "Les cles doivent etre synthetiques et reservees au LAB/RECETTE"
}

stage_build() {
  bash "$DMAS_DIR/00-install-database.sh"
  "$MAVEN" -f "$ROOT/pom.xml" \
    -pl sg-mc-dmas-member,sg-mc-dmas-mastercard,sg-dmcs-acquirer,sg-dmcs-issuer \
    -am verify -Dmaven.repo.local="$MAVEN_REPO"
}
stage_start() {
  # Ce parcours autonome simule le reseau Mastercard et ne reutilise pas une
  # instance Issuing externe, volontairement arretee entre les domaines de la
  # campagne globale.
  export DMAS_AUTHORIZATION_OWNER="${DMAS_AUTHORIZATION_OWNER:-EXTERNAL_MEMBER_SIMULATOR}"
  bash "$DMAS_DIR/01-start-mastercard.sh"
  bash "$DMAS_DIR/02-start-member.sh"
}
stage_bootstrap_and_provision() {
  bash "$DMAS_DIR/03a-bootstrap-mastercard.sh"
  bash "$DMAS_DIR/03b-bootstrap-member.sh"
  bash "$DMAS_DIR/03c-signon-and-key-exchange.sh"
}
stage_run_tests() {
  {
    bash "$DMAS_DIR/04-test-pin.sh"
    bash "$DMAS_DIR/04a-test-advice-reversal.sh"
    bash "$DMAS_DIR/05-test-emv-arqc-arpc.sh"
    bash "$DMAS_DIR/06-start-dmcs.sh"
    bash "$DMAS_DIR/07-run-dmc-eod.sh"
  } 2>&1 | tee "$MODULE_RUNTIME/test-output.log"
}
stage_check_results() {
  require_file "$MODULE_RUNTIME/test-output.log"
  grep -q 'DMC' "$MODULE_RUNTIME/test-output.log" \
    || fail "Aucune preuve d'execution DMC dans le journal"
  info "DMAS financier et EOD DMCS executes sans erreur"
  info "DE31/ARN reel, settlement et litiges bilateraux restent hors de ce test"
}
stage_tail_logs() {
  mapfile -t files < <(find "$DMAS_RUNTIME/logs" -maxdepth 1 -type f -name '*.log' 2>/dev/null)
  ((${#files[@]})) || fail "Aucun journal DMAS/DMCS disponible"
  tail -n 80 -F "${files[@]}"
}
stage_stop() { bash "$DMAS_DIR/08-stop-dmas-dmc.sh"; }
