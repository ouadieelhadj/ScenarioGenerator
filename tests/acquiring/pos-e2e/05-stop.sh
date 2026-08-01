#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck disable=SC1091
source "$(cd "$(dirname "$0")" && pwd)/_common.sh"
stop_module sg-way-pos-simulator pos-simulator
stop_module sg-way-pos-server ServerPOS
stop_module sg-card-issuing card-issuing
