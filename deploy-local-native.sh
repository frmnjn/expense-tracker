#!/usr/bin/env bash
#
# deploy-local-native.sh
#
# Build & jalankan seluruh stack secara lokal dengan BACKEND NATIVE (GraalVM).
#
# Berbeda dengan deploy-local.sh (JVM fallback), skrip ini membuild image
# native asli via backend/Dockerfile.native lalu menjalankan docker-compose.
#
# Alur:
#   1. Jalankan unit test backend (bisa dilewati dengan SKIP_TESTS=1)
#   2. Generate/cek reachability-metadata.json (bila belum ada)
#   3. Build image backend native (Dockerfile.native) tag
#      expense-tracker-backend-native:latest
#   4. docker compose up --build -d (mysql, backend, notifier, frontend)
#   5. Bersihkan image dangling
#   6. Tunggu backend sehat, lalu tampilkan URL akses.
#
# Prasyarat:
#   - Docker berjalan
#   - File backend/.env sudah ada
#   - RAM lokal >= ~7GB untuk kompilasi native
#
# Skalakan:     SKIP_TESTS=1 ./deploy-local-native.sh   # tanpa mvn test
# Restart saja: docker compose restart

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}/backend"

BACKEND_IMAGE="${BACKEND_IMAGE:-expense-tracker-backend-native:latest}"
CONFIG_FILE="${BACKEND_DIR}/native-config/reachability-metadata.json"

echo "==> [1/5] unit test backend"
if [ "${SKIP_TESTS:-0}" = "1" ]; then
    echo "    dilewati (SKIP_TESTS=1)"
else
    (cd "${BACKEND_DIR}" && mvn test)
fi

if [ ! -f "${CONFIG_FILE}" ]; then
    echo "==> [2/5] generate reachability-metadata.json"
    (cd "${BACKEND_DIR}" && ./generate-native-config.sh)
else
    echo "==> [2/5] reachability-metadata.json sudah ada (pakai ${CONFIG_FILE})"
fi

echo "==> [3/5] build image backend native via build-native.sh"
IMAGE="${BACKEND_IMAGE}" "${SCRIPT_DIR}/build-native.sh"

echo "==> [4/5] docker compose up --build -d"
(cd "${SCRIPT_DIR}" && docker compose up --build -d)

echo "==> [5/5] bersihkan image dangling"
docker image prune -f >/dev/null 2>&1 || true

echo "==> [6/6] tunggu backend sehat"
for i in $(seq 1 60); do
    if curl -fsS "http://localhost:8080/health" >/dev/null 2>&1; then
        echo "    backend sehat (${i}x probe)"
        break
    fi
    if [ "${i}" = "60" ]; then
        echo "ERROR: backend tidak sehat dalam 60 detik. Cek: docker compose logs backend" >&2
        exit 1
    fi
    sleep 2
done

echo ""
echo "Deploy local (native) selesai."
echo "  Backend  : http://localhost:8080  (health: http://localhost:8080/health)"
echo "  Frontend : http://localhost:3000"
echo "  Notifier : http://localhost:8081"
echo ""
echo "Cek status: docker compose ps"
echo "Log:       docker compose logs -f backend"
