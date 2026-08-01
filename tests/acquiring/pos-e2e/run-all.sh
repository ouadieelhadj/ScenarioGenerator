#!/usr/bin/env bash
set -Eeuo pipefail
export PATH="/usr/bin:/mingw64/bin:$PATH"
DIR="$(cd "$(dirname "$0")" && pwd)"
trap 'bash "$DIR/05-stop.sh" || true' EXIT
bash "$DIR/00-build.sh"
bash "$DIR/01-start.sh"
bash "$DIR/02-provision.sh"
bash "$DIR/03-purchase.sh"
