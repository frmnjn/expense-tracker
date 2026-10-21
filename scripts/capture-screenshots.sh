#!/usr/bin/env bash
# Capture screenshot desktop + mobile dari live demo memakai Playwright di Docker.
#
# Contoh:
#   ./scripts/capture-screenshots.sh
#   DEMO_URL=https://frmnjn.my.id/expense-tracker-demo/ DEMO_PIN=2026 ./scripts/capture-screenshots.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

DEMO_URL="${DEMO_URL:-https://frmnjn.my.id/expense-tracker-demo/}"
DEMO_PIN="${DEMO_PIN:-2026}"
OUT_DIR="${OUT_DIR:-${PROJECT_ROOT}/docs/screenshots}"
PLAYWRIGHT_IMAGE="${PLAYWRIGHT_IMAGE:-mcr.microsoft.com/playwright:v1.55.0-noble}"
PLAYWRIGHT_VERSION="${PLAYWRIGHT_VERSION:-1.55.0}"

if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker tidak ditemukan" >&2
    exit 1
fi

mkdir -p "${OUT_DIR}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

echo "==> Playwright image: ${PLAYWRIGHT_IMAGE}"
docker pull "${PLAYWRIGHT_IMAGE}" >/dev/null

echo "==> Capture ${DEMO_URL}"
docker run --rm \
    -v "${SCRIPT_DIR}/capture-screenshots.mjs:/capture.mjs:ro" \
    -v "${OUT_DIR}:/out" \
    -v "${TMP_DIR}:/pw" \
    -w /pw \
    "${PLAYWRIGHT_IMAGE}" \
    sh -c "npm init -y >/dev/null && npm i playwright@${PLAYWRIGHT_VERSION} >/dev/null 2>&1 && cp /capture.mjs /pw/capture.mjs && DEMO_URL='${DEMO_URL}' DEMO_PIN='${DEMO_PIN}' OUT_DIR=/out node /pw/capture.mjs"

echo "==> Selesai. Screenshot tersimpan di: ${OUT_DIR}"
