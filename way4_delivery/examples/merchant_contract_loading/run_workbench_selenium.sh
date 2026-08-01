#!/usr/bin/env bash
set -euo pipefail

python_cmd=${WAY4_PYTHON:-python}

if ! "$python_cmd" -c "import selenium" >/dev/null 2>&1; then
  printf 'Le module Python selenium est absent de l’environnement sélectionné.\n' >&2
  printf 'Installez-le dans un environnement virtuel autorisé avant de relancer.\n' >&2
  exit 2
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec "$python_cmd" "$script_dir/workbench_create_merchant.py"
