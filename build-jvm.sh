#!/usr/bin/env bash
#
# build-jvm.sh
#
# Build image backend JVM secara lokal (PC).
#
# Menghasilkan image: expense-tracker-backend-jvm:latest
# yang kemudian dikirim ke VPS via deploy-jvm.sh (docker save/scp/load).
#
# Backend JVM adalah DEFAULT produksi. Build memakai backend/Dockerfile
# (multi-stage Maven/Temurin). Berbeda dengan native (~7GB), build JVM lebih
# ringan tapi tetap sebaiknya dijalankan di PC, bukan di VPS yang RAM-nya
# terbatas (VPS dipakai banyak service lain).
#
# Prasyarat:
#   - Docker berjalan di PC
#
# Build native (opsional/legacy) pakai: ./build-native.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}/backend"

IMAGE="${IMAGE:-expense-tracker-backend-jvm:latest}"

echo "==> Build image backend JVM: ${IMAGE}"
echo "    Dockerfile: backend/Dockerfile"
docker build --no-cache -f "${BACKEND_DIR}/Dockerfile" -t "${IMAGE}" "${BACKEND_DIR}"

echo ""
echo "Build selesai. Cek image:"
docker images --format "{{.Repository}}:{{.Tag}} | {{.Size}}" | grep "${IMAGE}"
echo ""
echo "Langkah berikutnya:"
echo "  1. Test lokal (opsional):  docker run --rm -p 8080:8080 ${IMAGE} ..."
echo "  2. Deploy ke VPS:          ./deploy-jvm.sh"
