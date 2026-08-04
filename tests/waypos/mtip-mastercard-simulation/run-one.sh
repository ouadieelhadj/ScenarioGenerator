#!/usr/bin/env bash
set -Eeuo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ $# -eq 1 ]] || { echo "Usage: $0 MCD01.Test.01.Scenario.01" >&2; exit 2; }
exec python "$script_dir/run_scenarios.py" "$1"
