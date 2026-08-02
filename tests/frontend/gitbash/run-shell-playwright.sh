#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

cd "${REPO_ROOT}/sg-frontend"
echo "[FRONTEND] Build Angular puis tests Playwright contractuels"
npm.cmd run test:e2e -- frontend-global-shell.spec.ts
