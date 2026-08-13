#!/usr/bin/env bash
#
# clean_data.sh
#
# Bersihkan data aplikasi (expense, invoice/struk, top-up, idempotency) dari DB
# dan hapus semua file foto/PDF di folder upload. Tabel budgets TIDAK disentuh
# (nama, saldo, ambang notifikasi dipertahankan).
#
# Bisa dijalankan di lokal maupun VPS. Variabel yang bisa di-override:
#   MYSQL_CONTAINER (default expensetracker-mysql-1 ; di VPS pakai wordpress-db-1)
#   DB_USER         (default expense            ; di VPS baca dari backend/.env)
#   DB_PASSWORD     (default expense-pass)
#   DB_NAME         (default expense_tracker)
#   UPLOAD_DIR      (default <project>/uploads)
#
# Contoh:
#   Lokal:  scripts/clean_data.sh
#   VPS:    MYSQL_CONTAINER=wordpress-db-1 scripts/clean_data.sh
#   Tanpa konfirmasi: scripts/clean_data.sh -f

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-expensetracker-mysql-1}"
DB_NAME="${DB_NAME:-expense_tracker}"
UPLOAD_DIR="${UPLOAD_DIR:-${PROJECT_ROOT}/uploads}"

FORCE="${1:-}"

# Kredensial: baca dari backend/.env jika ada (VPS menyimpan DB_USER/DB_PASSWORD
# di sana); kalau tidak ada, pakai default lokal (expense/expense-pass).
if [ -f "${PROJECT_ROOT}/backend/.env" ]; then
    # shellcheck disable=SC1091
    source "${PROJECT_ROOT}/backend/.env"
fi
DB_USER="${DB_USER:-expense}"
DB_PASSWORD="${DB_PASSWORD:-expense-pass}"

confirm() {
    if [ "${FORCE}" = "-f" ]; then
        return 0
    fi
    read -r -p "Yakin hapus data (tabel budget dipertahankan)? Ketik 'y' untuk lanjut: " ans
    [ "${ans}" = "y" ] || [ "${ans}" = "Y" ]
}

main() {
    echo "==> Target DB : ${DB_NAME} (container ${MYSQL_CONTAINER})"
    echo "==> Upload dir: ${UPLOAD_DIR}"
    confirm || { echo "Dibatalkan."; exit 1; }

    echo "==> Bersihkan tabel: expenses, invoices, top_ups, idempotency_keys ..."
    docker exec "${MYSQL_CONTAINER}" mysql \
        -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" 2>/dev/null <<'SQL'
DELETE FROM expenses;
DELETE FROM invoices;
DELETE FROM top_ups;
DELETE FROM idempotency_keys;
SQL

    echo "==> Hapus semua file di ${UPLOAD_DIR} ..."
    mkdir -p "${UPLOAD_DIR}"
    find "${UPLOAD_DIR}" -type f -delete

    echo "==> Sisa isi tabel yang dipertahankan:"
    docker exec "${MYSQL_CONTAINER}" mysql -N -u"${DB_USER}" -p"${DB_PASSWORD}" "${DB_NAME}" 2>/dev/null \
        -e "SELECT CONCAT('budgets: ', COUNT(*)) FROM budgets;"
    echo "==> Selesai."
}

main
