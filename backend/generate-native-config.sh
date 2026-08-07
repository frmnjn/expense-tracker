#!/usr/bin/env bash
#
# generate-native-config.sh
#
# Generate reachability-metadata.json untuk GraalVM Native Image.
#
# Alur:
#   1. Build jar JVM biasa (maven + temurin) via Docker
#   2. Jalankan jar dengan tracing agent (GraalVM java) di dalam Docker
#   3. Panggil endpoint ke test sheet (health, options, POST /expenses)
#   4. Agent menulis reachability-metadata.json
#   5. Copy ke backend/native-config/reachability-metadata.json
#
# Aman: memakai GOOGLE_TEST_SHEET_ID (bukan produksi).
#
# Prasyarat:
#   - Docker berjalan
#   - backend/.env berisi GOOGLE_TEST_SHEET_ID & GOOGLE_APPLICATION_CREDENTIALS
#   - Image builder GraalVM sudah tersedia (di-pull saat Dockerfile.native di-build)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}"
PROJECT_ROOT="$(cd "${BACKEND_DIR}/.." && pwd)"

# image GraalVM builder (sama seperti di Dockerfile.native)
GRAALVM_IMAGE="container-registry.oracle.com/graalvm/native-image:25"
AGENT_LIB="/usr/lib64/graalvm/graalvm-java25/lib/libnative-image-agent.so"
OUTPUT_DIR="/tmp/native-config-out"

# pastikan .env ada
if [ ! -f "${BACKEND_DIR}/.env" ]; then
    echo "ERROR: ${BACKEND_DIR}/.env tidak ditemukan" >&2
    exit 1
fi

# set env dari .env
set -a
# shellcheck disable=SC1091
source "${BACKEND_DIR}/.env"
set +a

if [ -z "${GOOGLE_TEST_SHEET_ID:-}" ]; then
    echo "ERROR: GOOGLE_TEST_SHEET_ID kosong di .env" >&2
    exit 1
fi

echo "==> [1/5] Pull image GraalVM (bila belum ada)"
docker pull "${GRAALVM_IMAGE}" >/dev/null 2>&1 || true

echo "==> [2/5] Build jar JVM"
rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"
docker run --rm \
    -v "${PROJECT_ROOT}/backend:/src" \
    -v expense-native-m2:/root/.m2 \
    --entrypoint sh \
    "${GRAALVM_IMAGE}" -c \
    "microdnf -y install maven >/dev/null 2>&1 && cd /src && mvn -q -DskipTests package"

# copy credential + env ke dir kerja agar bisa dimount baca
WORKDIR="$(mktemp -d)"
cp "${BACKEND_DIR}/credentials.json" "${WORKDIR}/credentials.json"

echo "==> [3/5] Jalankan jar dengan tracing agent & hit test sheet"
docker run --rm \
    -v "${PROJECT_ROOT}/backend:/app:ro" \
    -v "${WORKDIR}:/work:ro" \
    -v "${OUTPUT_DIR}:/config" \
    -e "GOOGLE_APPLICATION_CREDENTIALS=/work/credentials.json" \
    -e "GOOGLE_SHEET_ID=${GOOGLE_TEST_SHEET_ID}" \
    -e "GOOGLE_BUDGET_SHEET=${GOOGLE_BUDGET_SHEET:-Budget}" \
    -e "GOOGLE_BANK_SHEET=${GOOGLE_BANK_SHEET:-Bank}" \
    --entrypoint sh \
    "${GRAALVM_IMAGE}" -c '
        /usr/lib64/graalvm/graalvm-java25/bin/java \
            -agentpath:'"${AGENT_LIB}"'=config-output-dir=/config \
            -jar /app/target/app.jar &
        APP=$!
        # tunggu sampai health siap
        for i in $(seq 1 60); do
            curl -s http://localhost:8080/health >/dev/null 2>&1 && break
            sleep 1
        done
        curl -s http://localhost:8080/health >/dev/null
        curl -s http://localhost:8080/options >/dev/null
        curl -s -X POST http://localhost:8080/expenses \
            -H "Content-Type: application/json" \
            -d "{\"dateTime\":\"2026-01-01 09:00\",\"name\":\"native-config-gen\",\"budget\":\"Household\",\"bank\":\"BCA IRENE\",\"amount\":1,\"description\":\"tracing agent\"}" >/dev/null
        sleep 3
        kill $APP 2>/dev/null
        wait $APP 2>/dev/null || true
    '

echo "==> [4/5] Validasi output agent"
if [ ! -f "${OUTPUT_DIR}/reachability-metadata.json" ]; then
    echo "ERROR: reachability-metadata.json tidak dihasilkan" >&2
    exit 1
fi

echo "==> [5/5] Copy ke backend/native-config/"
mkdir -p "${BACKEND_DIR}/native-config"
cp "${OUTPUT_DIR}/reachability-metadata.json" "${BACKEND_DIR}/native-config/reachability-metadata.json"

rm -rf "${WORKDIR}"
echo ""
echo "Selesai. Config baru: ${BACKEND_DIR}/native-config/reachability-metadata.json"
echo "Review diff-nya sebelum commit: git diff backend/native-config/reachability-metadata.json"
