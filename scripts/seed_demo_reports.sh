#!/usr/bin/env bash
# Generate contoh report PDF untuk database demo dari data yang sudah di-seed.
# Aman dijalankan berulang: period_reports lama dihapus dan PDF ditulis ulang.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/backend/.env.demo"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-wordpress-db-1}"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-25}"
M2_VOLUME="${M2_VOLUME:-expense-tracker-m2}"

if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: ${ENV_FILE} tidak ditemukan" >&2
    exit 1
fi

DB_USER="$(grep -E '^DB_USER=' "${ENV_FILE}" | head -n1 | cut -d= -f2- | tr -d '\r')"
DB_PASSWORD="$(grep -E '^DB_PASSWORD=' "${ENV_FILE}" | head -n1 | cut -d= -f2- | tr -d '\r')"
DB_URL="$(grep -E '^DB_URL=' "${ENV_FILE}" | head -n1 | cut -d= -f2- | tr -d '\r')"
DB_NAME="$(printf '%s' "${DB_URL}" | sed -E 's#.*/([^?]+).*#\1#')"

if [ -z "${DB_USER}" ] || [ -z "${DB_PASSWORD}" ] || [ -z "${DB_NAME}" ]; then
    echo "ERROR: DB_USER/DB_PASSWORD/DB_NAME tidak lengkap di .env.demo" >&2
    exit 1
fi
if [[ "${DB_NAME}" != *_demo ]]; then
    echo "ERROR: safety check gagal, DB '${DB_NAME}' bukan database demo" >&2
    exit 1
fi

mysql_demo() {
    docker exec -e MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_CONTAINER}" \
        mysql -h127.0.0.1 -u"${DB_USER}" -N -B "${DB_NAME}" -e "$1"
}

if [ "$(mysql_demo "SHOW TABLES LIKE 'period_reports';")" != "period_reports" ]; then
    echo "WARN: tabel period_reports belum ada. Jalankan demo backend (Flyway V25) dulu." >&2
    exit 0
fi

# Report demo disimulasikan untuk 3 periode terakhir yang sudah selesai
# (periode paling baru = periode berjalan, tidak dibuatkan report).
PERIODS="$(mysql_demo "SELECT period FROM (SELECT period, MAX(period_start) AS ps FROM expenses WHERE deleted=FALSE GROUP BY period) x ORDER BY ps DESC LIMIT 3 OFFSET 1;")"
if [ -z "${PERIODS}" ]; then
    echo "WARN: belum ada data expense untuk membuat report demo." >&2
    exit 0
fi

mysql_demo "DELETE FROM period_reports;"
rm -rf "${PROJECT_ROOT}/uploads-demo/reports"

echo "==> Generate report demo: $(printf '%s' "${PERIODS}" | tr '\n' ' ')"
# shellcheck disable=SC2086
docker run --rm --network host --add-host=host.docker.internal:host-gateway \
    -e DB_URL="${DB_URL}" -e DB_USER="${DB_USER}" -e DB_PASSWORD="${DB_PASSWORD}" \
    -e UPLOAD_DIR=/uploads-demo \
    -v "${PROJECT_ROOT}/backend:/app" \
    -v "${PROJECT_ROOT}/uploads-demo:/uploads-demo" \
    -v "${PROJECT_ROOT}/scripts/demo-report:/demo-report:ro" \
    -v "${M2_VOLUME}:/root/.m2" \
    -w /app "${MAVEN_IMAGE}" \
    sh -c 'mvn -q -DskipTests compile && mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp && javac -cp "target/classes:$(cat /tmp/cp)" -d /tmp /demo-report/BackfillReport.java && java -cp "/tmp:target/classes:$(cat /tmp/cp)" BackfillReport "$@"' sh ${PERIODS}
