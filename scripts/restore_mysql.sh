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

FORCE=0
if [ "${1:-}" = "-f" ] || [ "${1:-}" = "--force" ]; then
    FORCE=1
    shift
fi
if [ $# -lt 1 ]; then
    echo "Usage: $0 [-f] <backup.sql.gz>" >&2
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

DB_USER="$(grep -E '^DB_USER=' "${PROJECT_ROOT}/backend/.env" | head -n1 | cut -d= -f2- | tr -d '\r')"
DB_PASSWORD="$(grep -E '^DB_PASSWORD=' "${PROJECT_ROOT}/backend/.env" | head -n1 | cut -d= -f2- | tr -d '\r')"

if [ -z "${DB_USER}" ] || [ -z "${DB_PASSWORD}" ]; then
    echo "ERROR: DB_USER/DB_PASSWORD kosong di .env" >&2
    exit 1
fi

if [ "${FORCE}" != "1" ]; then
    read -r -p "Restore akan MENIMPA ${DB_NAME}. Lanjutkan? [y/N] " answer
    if [ "${answer}" != "y" ] && [ "${answer}" != "Y" ]; then
        echo "Dibatalkan."
        exit 1
    fi
fi

echo "==> Backup data saat ini sebelum restore"
"${SCRIPT_DIR}/backup_mysql.sh"

echo "==> Restore ${BACKUP_FILE} ke ${DB_NAME} (container ${MYSQL_CONTAINER})"
gzip -dc "${BACKUP_FILE}" | docker exec -i "${MYSQL_CONTAINER}" \
    mysql -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" 2>/dev/null
echo "==> Restore selesai."
