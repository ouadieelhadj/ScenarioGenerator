#!/usr/bin/env bash
set -Eeuo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"
MODULE_ID="$(basename "$MODULE_DIR")"
PLATFORM_E2E_DIR="$(cd "$MODULE_DIR/.." && pwd)"
ROOT="$(cd "$PLATFORM_E2E_DIR/../.." && pwd)"
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
load_env_file "$ROOT/runtime/issuing-connected-e2e/connected-e2e.env"
load_env_file "${PLATFORM_E2E_CONFIG_FILE:-$RUNTIME_ROOT/platform-e2e.env}"
load_env_file "${PLATFORM_E2E_MODULE_CONFIG_FILE:-$RUNTIME_ROOT/$MODULE_ID.env}"

info() {
  printf '[PLATFORM E2E][%s] %s\n' "$MODULE_ID" "$*"
}

cleanup() {
  if [[ "${PLATFORM_E2E_KEEP_RUNNING:-false}" != "true" ]]; then
    bash "$MODULE_DIR/07-stop.sh" || true
  fi
}
trap cleanup EXIT

if [[ -f "$MODULE_DIR/module.sh" ]]; then
  # shellcheck disable=SC1090
  source "$MODULE_DIR/module.sh"
fi
if declare -F module_run_all >/dev/null; then
  module_run_all
  exit 0
fi

for script in \
  00-check-prerequisites.sh \
  01-build.sh \
  02-start.sh \
  03-bootstrap-and-provision.sh \
  04-run-tests.sh \
  05-check-results.sh; do
  bash "$MODULE_DIR/$script"
done
