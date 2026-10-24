#!/usr/bin/env bash
# Generate ulang seluruh data demo: JSON + PDF report.
# Dipakai saat deploy pertama dan oleh cron harian.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export TZ="${TZ:-Asia/Jakarta}"

python3 "${SCRIPT_DIR}/generate_demo_data.py"
"${SCRIPT_DIR}/generate_demo_reports.sh"
