#!/usr/bin/env bash
#
# backup_mysql.sh
#
# Backup database expense_tracker (MySQL di VPS, container wordpress-db-1) ke file gzip
# dengan rotasi (menyimpan N backup terakhir).
#
# Prasyarat di VPS:
#   - Docker berjalan
#   - Container MySQL: wordpress-db-1
#   - backend/.env berisi DB_USER & DB_PASSWORD
#
# Variabel yang bisa di-override:
#   MYSQL_CONTAINER (default wordpress-db-1)
#   BACKUP_DIR      (default /root/expense-tracker/backups)
#   KEEP            (default 14, jumlah backup yang disimpan)
#
# Contoh cron (harian 03:00 WIB):
#   0 20 * * * /root/expense-tracker/scripts/backup_mysql.sh >> /var/log/expense-backup.log 2>&1

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-wordpress-db-1}"
BACKUP_DIR="${BACKUP_DIR:-${PROJECT_ROOT}/backups}"
KEEP="${KEEP:-14}"
DB_NAME="expense_tracker"

# ambil kredensial DB dari .env
if [ ! -f "${PROJECT_ROOT}/backend/.env" ]; then
    echo "ERROR: ${PROJECT_ROOT}/backend/.env tidak ditemukan" >&2
    exit 1
fi
# shellcheck disable=SC1091
source "${PROJECT_ROOT}/backend/.env"

if [ -z "${DB_USER:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: DB_USER/DB_PASSWORD kosong di .env" >&2
    exit 1
fi

mkdir -p "${BACKUP_DIR}"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT="${BACKUP_DIR}/${DB_NAME}_${STAMP}.sql.gz"

echo "==> Backup ${DB_NAME} -> ${OUT}"
docker exec "${MYSQL_CONTAINER}" \
    mysqldump --single-transaction --routines --triggers \
        -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" 2>/dev/null \
    | gzip > "${OUT}"

# rotasi: hapus backup lama, sisakan KEEP terbaru
ls -1t "${BACKUP_DIR}/${DB_NAME}"_*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
    echo "   hapus lama: ${old}"
    rm -f "${old}"
done

echo "==> Selesai. Backup tersimpan:"
ls -lh "${BACKUP_DIR}/${DB_NAME}"_*.sql.gz | tail -n "${KEEP}"
