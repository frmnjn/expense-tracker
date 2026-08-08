#!/usr/bin/env bash
#
# restore_mysql.sh
#
# Restore backup database expense_tracker ke MySQL (container wordpress-db-1).
#
# Penggunaan:
#   ./scripts/restore_mysql.sh /path/ke/backup.sql.gz
#
# CATATAN: ini akan MENIMPA data expense_tracker saat ini dengan isi backup.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <backup.sql.gz>" >&2
    exit 1
fi

BACKUP_FILE="$1"
if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: file backup tidak ditemukan: ${BACKUP_FILE}" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-wordpress-db-1}"
DB_NAME="expense_tracker"

# shellcheck disable=SC1091
source "${PROJECT_ROOT}/backend/.env"

if [ -z "${DB_USER:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: DB_USER/DB_PASSWORD kosong di .env" >&2
    exit 1
fi

echo "==> Restore ${BACKUP_FILE} ke ${DB_NAME} (container ${MYSQL_CONTAINER})"
gzip -dc "${BACKUP_FILE}" | docker exec -i "${MYSQL_CONTAINER}" \
    mysql -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" 2>/dev/null
echo "==> Restore selesai."
