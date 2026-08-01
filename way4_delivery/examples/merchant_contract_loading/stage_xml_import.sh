#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s <fichier-xml>\n' "$0" >&2
  exit 2
fi

source_file=$1
inbox=${WAY4_XML_INBOX:-}

if [[ ! -f "$source_file" ]]; then
  printf 'Fichier introuvable: %s\n' "$source_file" >&2
  exit 2
fi

if [[ -z "$inbox" || ! -d "$inbox" ]]; then
  printf 'WAY4_XML_INBOX doit désigner un répertoire existant.\n' >&2
  exit 2
fi

if [[ "${WAY4_XML_STAGE_CONFIRMED:-NO}" != "YES" ]]; then
  printf 'Dépôt non exécuté. Définir WAY4_XML_STAGE_CONFIRMED=YES après validation du répertoire.\n' >&2
  exit 3
fi

target="$inbox/$(basename "$source_file")"
if [[ -e "$target" ]]; then
  printf 'La cible existe déjà, aucun écrasement: %s\n' "$target" >&2
  exit 4
fi

cp -- "$source_file" "$target"
printf 'Fichier déposé: %s\n' "$target"
printf 'Aucun pipe Way4 n’a été déclenché par ce script.\n'
