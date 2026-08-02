#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"

ENTRY_SCRIPT="${BASH_SOURCE[1]}"
MODULE_DIR="$(cd "$(dirname "$ENTRY_SCRIPT")" && pwd)"
PLATFORM_E2E_DIR="$(cd "$MODULE_DIR/.." && pwd)"
ROOT="$(cd "$PLATFORM_E2E_DIR/../.." && pwd)"
MODULE_ID="$(basename "$MODULE_DIR")"
RUNTIME_ROOT="${PLATFORM_E2E_RUNTIME:-$ROOT/runtime/platform-e2e}"
MODULE_RUNTIME="$RUNTIME_ROOT/$MODULE_ID"
mkdir -p "$MODULE_RUNTIME"

load_env_file() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  set -a
  # shellcheck disable=SC1090
  source "$file"
  set +a
}

# Compatibilite avec la configuration locale deja utilisee par Issuing,
# Acquiring, ServerPOS et 3DS. Le fichier plateforme, s'il existe, surcharge
# ensuite ces valeurs sans imposer de duplication.
load_env_file "$ROOT/runtime/issuing-connected-e2e/connected-e2e.env"
load_env_file "${PLATFORM_E2E_CONFIG_FILE:-$RUNTIME_ROOT/platform-e2e.env}"
load_env_file "${PLATFORM_E2E_MODULE_CONFIG_FILE:-$RUNTIME_ROOT/$MODULE_ID.env}"

fail() {
  printf '[PLATFORM E2E][%s] ERREUR - %s\n' "$MODULE_ID" "$*" >&2
  exit 1
}

info() {
  printf '[PLATFORM E2E][%s] %s\n' "$MODULE_ID" "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Commande absente: $1"
}

require_file() {
  [[ -f "$1" ]] || fail "Fichier absent: $1"
}

require_var() {
  [[ -n "${!1-}" ]] || fail "Variable requise absente: $1"
}

require_git_bash() {
  [[ -n "${MSYSTEM:-}" ]] || fail "Ce parcours doit etre lance depuis Git Bash"
}

record_stage() {
  local stage="$1" status="$2"
  printf '%s\t%s\t%s\n' "$(date -Iseconds)" "$stage" "$status" \
    >>"$MODULE_RUNTIME/status.tsv"
}

stage_name="$(basename "$ENTRY_SCRIPT" .sh)"
case "$stage_name" in
  00-check-prerequisites) stage_function=stage_check_prerequisites ;;
  01-build) stage_function=stage_build ;;
  02-start) stage_function=stage_start ;;
  03-bootstrap-and-provision) stage_function=stage_bootstrap_and_provision ;;
  04-run-tests) stage_function=stage_run_tests ;;
  05-check-results) stage_function=stage_check_results ;;
  06-tail-logs) stage_function=stage_tail_logs ;;
  07-stop) stage_function=stage_stop ;;
  *) fail "Etape inconnue: $stage_name" ;;
esac

# shellcheck disable=SC1091
source "$MODULE_DIR/module.sh"
declare -F "$stage_function" >/dev/null || fail "Fonction absente: $stage_function"

on_error() {
  local code=$?
  record_stage "$stage_name" "FAILED($code)"
  exit "$code"
}
trap on_error ERR

info "DEBUT $stage_name"
if [[ "$stage_name" == "01-build" \
    && "${PLATFORM_E2E_SKIP_BUILD:-false}" == "true" ]]; then
  info "Build ignore sur demande; les JAR existants seront controles au demarrage"
  record_stage "$stage_name" SKIPPED
  info "FIN $stage_name"
  exit 0
fi
"$stage_function"
record_stage "$stage_name" PASSED
info "FIN $stage_name"
