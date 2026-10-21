#!/usr/bin/env bash
# Reset data demo dari backend/.env.demo. Hanya boleh untuk database *_demo.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/backend/.env.demo"

if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: ${ENV_FILE} tidak ditemukan" >&2
    exit 1
fi

DB_USER="$(grep -E '^DB_USER=' "${ENV_FILE}" | head -n1 | cut -d= -f2- | tr -d '\r')"
DB_PASSWORD="$(grep -E '^DB_PASSWORD=' "${ENV_FILE}" | head -n1 | cut -d= -f2- | tr -d '\r')"
DB_URL="$(grep -E '^DB_URL=' "${ENV_FILE}" | head -n1 | cut -d= -f2- | tr -d '\r')"
DB_NAME="$(printf '%s' "${DB_URL}" | sed -E 's#.*/([^?]+).*#\1#')"

if [ -z "${DB_USER}" ] || [ -z "${DB_PASSWORD}" ] || [ -z "${DB_NAME}" ]; then
    echo "ERROR: DB_USER/DB_PASSWORD/DB_NAME tidak lengkap di .env.demo" >&2
    exit 1
fi
if [[ "${DB_NAME}" != *_demo ]]; then
    echo "ERROR: safety check gagal, DB '${DB_NAME}' bukan database demo" >&2
    exit 1
fi

DEMO_DB_PASSWORD="${DB_PASSWORD}" python3 "${SCRIPT_DIR}/seed_demo_data.py" \
    --container "${MYSQL_CONTAINER:-wordpress-db-1}" \
    --db-user "${DB_USER}" \
    --db "${DB_NAME}"
