#!/usr/bin/env bash

DOMAINS=(issuing acquiring three-ds mastercard-dmas-dmcs swam visa)

selected_domains() {
  local requested="${PLATFORM_E2E_MODULES:-${DOMAINS[*]}}" domain
  for domain in $requested; do
    case " ${DOMAINS[*]} " in
      *" $domain "*) printf '%s\n' "$domain" ;;
      *) fail "Domaine inconnu dans PLATFORM_E2E_MODULES: $domain" ;;
    esac
  done
}

stage_check_prerequisites() {
  require_git_bash
  for command in bash curl python; do require_command "$command"; done
  local domain
  while IFS= read -r domain; do
    require_file "$PLATFORM_E2E_DIR/$domain/run-all.sh"
  done < <(selected_domains)
  info "Ordre global: $(selected_domains | tr '\n' ' ')"
}
stage_build() { info "Chaque domaine construit ses propres modules"; }
stage_start() { info "Les domaines seront lances sequentiellement pour eviter les conflits de ports"; }
stage_bootstrap_and_provision() { info "Chaque domaine applique son bootstrap fail-closed"; }
stage_run_tests() {
  local domain status failures=0
  : >"$MODULE_RUNTIME/summary.tsv"
  while IFS= read -r domain; do
    info "EXECUTION $domain"
    if bash "$PLATFORM_E2E_DIR/$domain/run-all.sh" \
      > >(tee "$MODULE_RUNTIME/$domain.log") \
      2> >(tee "$MODULE_RUNTIME/$domain.err.log" >&2); then
      status=PASSED
    else
      status=FAILED
      failures=$((failures + 1))
    fi
    printf '%s\t%s\n' "$domain" "$status" >>"$MODULE_RUNTIME/summary.tsv"
    if ((failures > 0)) && [[ "${PLATFORM_E2E_CONTINUE_ON_FAILURE:-false}" != "true" ]]; then
      break
    fi
  done < <(selected_domains)
  ((failures == 0)) || fail "$failures domaine(s) en echec"
}
stage_check_results() {
  require_file "$MODULE_RUNTIME/summary.tsv"
  if grep -q $'\tFAILED$' "$MODULE_RUNTIME/summary.tsv"; then
    cat "$MODULE_RUNTIME/summary.tsv"
    fail "Le bilan global contient des echecs"
  fi
  cat "$MODULE_RUNTIME/summary.tsv"
  info "Tous les domaines selectionnes sont PASSED"
}
stage_tail_logs() {
  mapfile -t files < <(find "$MODULE_RUNTIME" -maxdepth 1 -type f -name '*.log')
  ((${#files[@]})) || fail "Aucun journal global disponible"
  tail -n 80 -F "${files[@]}"
}
stage_stop() {
  local domain
  while IFS= read -r domain; do
    bash "$PLATFORM_E2E_DIR/$domain/07-stop.sh" || true
  done < <(selected_domains)
}
