#!/usr/bin/env bash
#
# deploy-local.sh
#
# Build & jalankan seluruh stack secara lokal untuk keperluan development/test.
#
# Alur:
#   1. Jalankan unit test backend (bisa dilewati dengan SKIP_TESTS=1)
#   2. Build image backend JVM fallback (backend/Dockerfile) dan di-tag sebagai
#      expense-tracker-backend-native:latest agar docker-compose.yml jalan.
#      Catatan: ini MENIMPA tag tsb secara lokal — image native asli di PC ini
#      hanya hilang tag-nya, image tetap ada. Untuk rebuild native asli,
#      gunakan ./build-native.sh.
#   3. docker compose up --build -d (mysql, backend, notifier, frontend)
#   4. Bersihkan image dangling
#   5. Tunggu backend sehat, lalu tampilkan URL akses.
#
# Prasyarat:
#   - Docker berjalan (Docker Desktop / daemon lokal)
#   - File backend/.env sudah ada
#
# Skalakan:     SKIP_TESTS=1 ./deploy-local.sh        # tanpa mvn test
# Restart saja: docker compose restart

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}/backend"

BACKEND_IMAGE="${BACKEND_IMAGE:-expense-tracker-backend-native:latest}"

echo "==> [1/4] unit test backend"
if [ "${SKIP_TESTS:-0}" = "1" ]; then
    echo "    dilewati (SKIP_TESTS=1)"
else
    (cd "${BACKEND_DIR}" && mvn test)
fi

echo "==> [2/4] build image backend JVM (fallback dev): ${BACKEND_IMAGE}"
docker build -f "${BACKEND_DIR}/Dockerfile" -t "${BACKEND_IMAGE}" "${BACKEND_DIR}"

echo "==> [3/4] docker compose up --build -d"
(cd "${SCRIPT_DIR}" && docker compose up --build -d)

echo "==> [4/4] bersihkan image dangling"
docker image prune -f

echo "==> [5/5] tunggu backend sehat"
for i in $(seq 1 30); do
    if curl -fsS "http://localhost:8080/health" >/dev/null 2>&1; then
        echo "    backend sehat (${i}x probe)"
        break
    fi
    if [ "${i}" = "30" ]; then
        echo "ERROR: backend tidak sehat dalam 30 detik. Cek: docker compose logs backend" >&2
        exit 1
    fi
    sleep 2
done

echo ""
echo "Deploy local selesai."
echo "  Frontend : http://localhost:3000"
echo "  Backend  : http://localhost:8080  (health: http://localhost:8080/health)"
echo "  Notifier : http://localhost:8081"
echo ""
echo "Cek status: docker compose ps"
echo "Log:       docker compose logs -f backend"
