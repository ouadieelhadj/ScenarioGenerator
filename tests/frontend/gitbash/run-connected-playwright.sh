#!/usr/bin/env bash
set -euo pipefail

: "${E2E_BASE_URL:?E2E_BASE_URL est requis (URL du frontend démarré)}"
: "${E2E_LOGIN:?E2E_LOGIN est requis}"
: "${E2E_PASSWORD:?E2E_PASSWORD est requis}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

cd "${REPO_ROOT}/sg-frontend"
echo "[FRONTEND] Tests Playwright connectés sur ${E2E_BASE_URL}"
node ./tools/run-playwright.mjs portal-modulaire.spec.ts
