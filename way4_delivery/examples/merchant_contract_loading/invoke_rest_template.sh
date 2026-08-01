#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s <payload-json-validé>\n' "$0" >&2
  exit 2
fi

payload=$1
url=${WAY4_REST_URL:-}

if [[ "${WAY4_REST_CONFIRMED:-NO}" != "YES" ]]; then
  printf 'Envoi bloqué: le contrat REST Way4 n’est pas confirmé.\n' >&2
  exit 3
fi

if [[ -z "$url" || ! -f "$payload" ]]; then
  printf 'WAY4_REST_URL et un fichier JSON existant sont obligatoires.\n' >&2
  exit 2
fi

auth_args=()
if [[ -n "${WAY4_REST_TOKEN:-}" ]]; then
  auth_args=(-H "Authorization: Bearer ${WAY4_REST_TOKEN}")
fi

curl --fail-with-body --silent --show-error \
  --request POST \
  --header "Content-Type: application/json" \
  "${auth_args[@]}" \
  --data-binary "@$payload" \
  "$url"
