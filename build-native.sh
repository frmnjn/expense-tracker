#!/usr/bin/env bash
#
# build-native.sh
#
# Build image backend GraalVM native image secara lokal.
#
# Menghasilkan image: expense-tracker-backend-native:latest
# yang kemudian dikirim ke VPS via deploy-native.sh (docker save/scp/load).
#
# Alur internal (lihat backend/Dockerfile.native):
#   1. Build jar JVM biasa (maven + temurin)
#   2. Compile native executable (GraalVM 25, AOT + tracing config)
#   3. Bungkus executable ke runtime minimal distroless + zlib
#
# Prasyarat:
#   - Docker berjalan
#   - backend/native-config/reachability-metadata.json sudah ada
#     (generate via ./backend/generate-native-config.sh bila belum)
#
# Catatan: build native butuh ~5-6 menit dan Peak RAM ~7GB.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}/backend"

IMAGE="${IMAGE:-expense-tracker-backend-native:latest}"
DOCKERFILE="${DOCKERFILE:-backend/Dockerfile.native}"

# pastikan config refleksi ada
if [ ! -f "${BACKEND_DIR}/native-config/reachability-metadata.json" ]; then
    echo "ERROR: ${BACKEND_DIR}/native-config/reachability-metadata.json tidak ditemukan." >&2
    echo "Generate dulu: ./backend/generate-native-config.sh" >&2
    exit 1
fi

echo "==> Build native image: ${IMAGE}"
echo "    Dockerfile: ${DOCKERFILE}"
echo "    (butuh ~5-6 menit, Peak RAM ~7GB)"
docker build --no-cache -f "${DOCKERFILE}" -t "${IMAGE}" "${BACKEND_DIR}"

echo ""
echo "Build selesai. Cek image:"
docker images --format "{{.Repository}}:{{.Tag}} | {{.Size}}" | grep "${IMAGE}"
echo ""
echo "Langkah berikutnya:"
echo "  1. Test lokal (opsional):  docker run --rm -p 8080:8080 ${IMAGE} ..."
echo "  2. Deploy ke VPS:          ./deploy-native.sh"
