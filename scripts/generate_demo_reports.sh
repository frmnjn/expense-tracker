#!/usr/bin/env bash
# Generate PDF report demo dari frontend/public/demo/data.json memakai
# PeriodReportPdfRenderer yang sama dengan produksi (tanpa database).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DATA_FILE="${PROJECT_ROOT}/frontend/public/demo/data.json"
OUT_DIR="${PROJECT_ROOT}/frontend/public/demo/reports"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-25}"
M2_VOLUME="${M2_VOLUME:-expense-tracker-m2}"

if [ ! -f "${DATA_FILE}" ]; then
    echo "ERROR: ${DATA_FILE} belum ada. Jalankan scripts/generate_demo_data.py dulu." >&2
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker tidak ditemukan" >&2
    exit 1
fi

mkdir -p "${OUT_DIR}"

echo "==> Generate report demo dari ${DATA_FILE}"
docker run --rm \
    --memory=768m \
    -e TZ=Asia/Jakarta \
    -e MAVEN_OPTS=-Xmx384m \
    -v "${PROJECT_ROOT}/backend:/app" \
    -v "${PROJECT_ROOT}/frontend/public/demo:/demo-data" \
    -v "${PROJECT_ROOT}/scripts/demo-report:/demo-report:ro" \
    -v "${M2_VOLUME}:/root/.m2" \
    -w /app "${MAVEN_IMAGE}" \
    sh -c 'mvn -q -DskipTests compile && mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp && javac -cp "target/classes:$(cat /tmp/cp)" -d /tmp /demo-report/GenerateDemoReports.java && java -Duser.timezone=Asia/Jakarta -cp "/tmp:target/classes:$(cat /tmp/cp)" GenerateDemoReports /demo-data/data.json /demo-data/reports'
