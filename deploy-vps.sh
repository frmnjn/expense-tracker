#!/usr/bin/env bash
#
# deploy-vps.sh
#
# Deploy penuh aplikasi ke VPS: build image backend JVM LANGSUNG di VPS,
# rebuild frontend, start/restart stack, bersihkan image lama, dan verifikasi.
#
# Backend JVM (expense-tracker-backend-jvm:latest) adalah image produksi.
# Image dibuild DI VPS (asal yang dulu "build di PC lalu transfer" diubah;
# sudah terbukti build JVM aman tanpa OOM di RAM 3.7GB VPS, bahkan di STB
# 1.7GB sekalipun).
#
# Prasyarat:
#   - SSH key root@example.com terdaftar (tanpa password).
#   - Perubahan di repo sudah di-commit & push ke origin/master (biar git pull
#     di VPS mendapat kode terbaru).
#
# Alur (semua dijalankan di VPS via SSH):
#   1. git pull (update kode + compose)
#   2. backup database sebelum migrasi
#   3. docker build image backend JVM (tag commit + latest)
#   4. docker compose up -d --build (rebuild frontend + start backend)
#   5. health gate (/ready) + bersihkan dangling

set -euo pipefail

VPS_HOST="${VPS_HOST:?set VPS_HOST (hostname/IP VPS)}"
VPS_DIR="${VPS_DIR:-/root/expense-tracker}"
IMAGE="${IMAGE:-expense-tracker-backend-jvm:latest}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-}"
TOTAL_MS=0

# step <label> <bash-cmd...>
# Menjalankan perintah dan menampilkan durasi step (dengan millis).
step() {
    local label="$1"
    shift
    local start end ms
    start=$(date +%s%3N)
    echo ""
    echo "==> ${label}"
    "$@"
    end=$(date +%s%3N)
    ms=$((end - start))
    TOTAL_MS=$((TOTAL_MS + ms))
    printf '    selesai dalam %d.%03ds\n' $((ms / 1000)) $((ms % 1000))
}

if ! ssh -o BatchMode=yes -o ConnectTimeout=10 "root@${VPS_HOST}" 'true' >/dev/null 2>&1; then
    echo "ERROR: SSH key ke root@${VPS_HOST} tidak berfungsi." >&2
    echo "Pastikan public key terdaftar di ~/.ssh/authorized_keys VPS." >&2
    exit 1
fi

step "[1/6] git pull di VPS" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && git checkout -- . && git pull --ff-only origin master"

step "[2/6] backup database sebelum migrasi" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && ./scripts/backup_mysql.sh"

step "[3/6] build image backend JVM di VPS (${IMAGE})" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && SHA=\$(git rev-parse --short HEAD) && \
     PREV=\$(docker image inspect ${IMAGE} --format '{{.Id}}' 2>/dev/null || true) && \
     if [ -n \"\$PREV\" ]; then docker tag \$PREV ${IMAGE%-latest}:rollback-\$(date +%Y%m%d%H%M%S); fi && \
     docker build -f backend/Dockerfile -t ${IMAGE} -t ${IMAGE%-latest}:\$SHA backend/"

step "[4/6] docker compose up -d --build (rebuild frontend + start backend)" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && docker compose -f docker-compose.prod.yml up -d --build"

step "[5/6] health gate /ready" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && for i in \$(seq 1 30); do \
       if docker compose -f docker-compose.prod.yml exec -T backend curl -fsS http://localhost:8080/ready >/dev/null; then \
         echo 'backend ready'; exit 0; fi; sleep 2; done; \
     echo 'ERROR: backend tidak ready' >&2; docker compose -f docker-compose.prod.yml logs --tail=80 backend >&2; exit 1"

step "[6/6] bersihkan image dangling di VPS" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" "docker image prune -f" || true

echo ""
printf 'Total semua step: %d.%03ds\n' $((TOTAL_MS / 1000)) $((TOTAL_MS % 1000))
echo ""
echo "Deploy VPS selesai."
if [ -n "${PUBLIC_BASE_URL}" ]; then
    echo "Cek health:"
    echo "  curl -s ${PUBLIC_BASE_URL%/}/api/health"
else
    echo "Cek health dari VPS:"
    echo "  docker compose -f docker-compose.prod.yml exec -T backend curl -fsS http://localhost:8080/health"
fi
echo "Rollback: gunakan image ${IMAGE%-latest}:rollback-<timestamp> lalu compose up -d."
