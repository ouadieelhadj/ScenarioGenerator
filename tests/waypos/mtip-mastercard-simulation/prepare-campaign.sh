#!/usr/bin/env bash
set -Eeuo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$script_dir/load-cards.sh"
"$script_dir/prepare-routes.sh"
"$script_dir/prepare-rki.sh"

echo "Preparation MTIP terminee. Lancez run-all.sh lorsque vous etes pret."
