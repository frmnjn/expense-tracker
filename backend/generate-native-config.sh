#!/usr/bin/env bash
#
# generate-native-config.sh
#
# Generate reachability-metadata.json untuk GraalVM Native Image.
#
# Alur:
#   1. Build jar JVM biasa (maven + temurin) via Docker
#   2. Jalankan jar dengan tracing agent (GraalVM java) di dalam Docker
#   3. Panggil endpoint (health, options, periods, expenses CRUD, summary, trend, topups)
#   4. Agent menulis reachability-metadata.json
#   5. Copy ke backend/native-config/reachability-metadata.json
#
# Berjalan terhadap MySQL (DB_URL harus bisa dijangkau dari container, pakai host.docker.internal).
#
# Prasyarat:
#   - Docker berjalan
#   - backend/.env berisi DB_URL, DB_USER, DB_PASSWORD (menunjuk ke MySQL test/scratch)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${SCRIPT_DIR}"
PROJECT_ROOT="$(cd "${BACKEND_DIR}/.." && pwd)"

# image GraalVM builder (sama seperti di Dockerfile.native)
GRAALVM_IMAGE="container-registry.oracle.com/graalvm/native-image:25"
AGENT_LIB="/usr/lib64/graalvm/graalvm-java25/lib/libnative-image-agent.so"
OUTPUT_DIR="/tmp/native-config-out"

if [ ! -f "${BACKEND_DIR}/.env" ]; then
    echo "ERROR: ${BACKEND_DIR}/.env tidak ditemukan" >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
source "${BACKEND_DIR}/.env"
set +a

if [ -z "${DB_URL:-}" ] || [ -z "${DB_USER:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: DB_URL/DB_USER/DB_PASSWORD kosong di .env" >&2
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

echo "==> [3/5] Jalankan jar dengan tracing agent & hit endpoint"
docker run --rm \
    -v "${PROJECT_ROOT}/backend:/app:ro" \
    -v "${PROJECT_ROOT}/backend/src/main/resources/db/migration:/migrations:ro" \
    -v "${OUTPUT_DIR}:/config" \
    -e "DB_URL=${DB_URL}" \
    -e "DB_USER=${DB_USER}" \
    -e "DB_PASSWORD=${DB_PASSWORD}" \
    -e "FLYWAY_LOCATIONS=filesystem:/migrations" \
    --add-host=host.docker.internal:host-gateway \
    --entrypoint sh \
    "${GRAALVM_IMAGE}" -c '
        /usr/lib64/graalvm/graalvm-java25/bin/java \
            -agentpath:'"${AGENT_LIB}"'=config-output-dir=/config \
            -jar /app/target/app.jar &
        APP=$!
        for i in $(seq 1 90); do
            curl -s http://localhost:8080/health >/dev/null 2>&1 && break
            sleep 1
        done
        curl -s http://localhost:8080/health >/dev/null
        curl -s http://localhost:8080/options >/dev/null
        curl -s http://localhost:8080/periods >/dev/null
        curl -s -X POST http://localhost:8080/budgets \
            -H "Content-Type: application/json" \
            -d '{"name":"NativeConfigBudget","balance":1}' >/dev/null
        curl -s -X PUT http://localhost:8080/budgets/NativeConfigBudget \
            -H "Content-Type: application/json" \
            -d '{"name":"NativeConfigBudget2","balance":2}' >/dev/null
        curl -s -X DELETE http://localhost:8080/budgets/NativeConfigBudget2 >/dev/null
        curl -s -X POST http://localhost:8080/expenses \
            -H "Content-Type: application/json" \
            -d "{\"dateTime\":\"2026-01-01 09:00\",\"name\":\"native-config-gen\",\"budget\":\"Household\",\"amount\":1,\"description\":\"tracing agent\"}" >/dev/null
        PERIOD=$(curl -s http://localhost:8080/periods | sed -n "s/.*\"periods\":\[\"\([^\"]*\)\".*/\1/p")
        [ -z "$PERIOD" ] && PERIOD="2025-DEC-JAN"
        curl -s "http://localhost:8080/expenses?period=${PERIOD}" >/dev/null
        curl -s "http://localhost:8080/summary?period=${PERIOD}" >/dev/null
        curl -s "http://localhost:8080/trend?months=3" >/dev/null
        curl -s "http://localhost:8080/topups" >/dev/null
        curl -s -X POST http://localhost:8080/topups \
            -H "Content-Type: application/json" \
            -d '{"budget":"Household","amount":1}' >/dev/null
        ID=$(curl -s "http://localhost:8080/expenses?period=${PERIOD}" | sed -n "s/.*\"id\":\"\([^\"]*\)\".*/\1/p" | head -n1)
        if [ -n "$ID" ]; then
            printf '\x89PNG\r\n\x1a\n' > /tmp/p.png
            curl -s -X POST "http://localhost:8080/expenses/${ID}/photo" \
                -F "file=@/tmp/p.png;type=image/png" >/dev/null
            curl -s "http://localhost:8080/expenses/${ID}/photo" >/dev/null
            curl -s -X PUT "http://localhost:8080/expenses/${ID}" \
                -H "Content-Type: application/json" \
                -d "{\"dateTime\":\"2026-01-01 09:00\",\"name\":\"native-config-gen\",\"budget\":\"Household\",\"amount\":2,\"description\":\"edit\"}" >/dev/null
            curl -s -X DELETE "http://localhost:8080/expenses/${ID}" >/dev/null
        fi
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

echo ""
echo "Selesai. Config baru: ${BACKEND_DIR}/native-config/reachability-metadata.json"
echo "Review diff-nya sebelum commit: git diff backend/native-config/reachability-metadata.json"
