#!/usr/bin/env bash
set -Eeuo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Le simulateur reconstruit ses PIN blocks avec la TPK importee. Il faut donc
# une RKI locale complete, distincte du rejeu opaque des blocs du F20 physique.
bash "$script_dir/../gitbash/rki-exchange.sh"
bash "$script_dir/../gitbash/rki-sign-confirm.sh"
