#!/usr/bin/env bash
# Pasang cron harian untuk generate data + PDF demo (idempotent).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MARKER="# expense-tracker-demo-generator"
SCHEDULE="${DEMO_CRON_SCHEDULE:-5 0 * * *}"
LOG_FILE="${PROJECT_ROOT}/demo-generate.log"

LINE="${SCHEDULE} cd ${PROJECT_ROOT} && TZ=Asia/Jakarta flock -n /tmp/expense-demo-generator.lock ./scripts/generate_demo.sh >> ${LOG_FILE} 2>&1 ${MARKER}"

if ! command -v crontab >/dev/null 2>&1; then
    echo "ERROR: crontab tidak ditemukan" >&2
    exit 1
fi

{
    crontab -l 2>/dev/null | grep -vF "${MARKER}" || true
    echo "${LINE}"
} | crontab -

echo "Cron demo terpasang:"
echo "  ${LINE}"
echo "Log: ${LOG_FILE}"
