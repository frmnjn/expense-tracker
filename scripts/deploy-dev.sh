#!/usr/bin/env bash
#
# deploy-dev.sh
#
# Build & jalankan environment dev yang terisolasi dari produksi:
# - MySQL produksi, schema expense_tracker_dev
# - notifier produksi
# - email dipaksa test mode ke alamat developer di backend/.env.dev
#
# Prasyarat: backend/.env.dev sudah dibuat dari backend/.env.dev.example.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.dev.yml"
PROJECT_NAME="expense-dev"

cd "${PROJECT_ROOT}"

if [ ! -f backend/.env.dev ]; then
    echo "ERROR: backend/.env.dev tidak ditemukan." >&2
    echo "Salin backend/.env.dev.example lalu isi nilainya." >&2
    exit 1
fi

echo "==> [1/3] validasi compose dev"
docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" config >/dev/null

echo "==> [2/3] build & start dev stack"
docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d --build

echo "==> [3/3] tunggu backend dev siap"
for i in $(seq 1 60); do
    if docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" exec -T backend \
        curl -fsS http://127.0.0.1:8080/ready >/dev/null 2>&1; then
        echo "    backend dev siap (${i}x probe)"
        break
    fi
    if [ "${i}" = "60" ]; then
        echo "ERROR: backend dev tidak siap. Cek: docker compose -p ${PROJECT_NAME} -f ${COMPOSE_FILE} logs backend" >&2
        exit 1
    fi
    sleep 2
done

echo ""
echo "Dev environment selesai."
echo "  URL    : https://<domain-dev>"
echo "  Status : docker compose -p ${PROJECT_NAME} -f ${COMPOSE_FILE} ps"
echo "  Log    : docker compose -p ${PROJECT_NAME} -f ${COMPOSE_FILE} logs -f backend"
