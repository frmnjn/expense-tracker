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
#   - SSH key root@frmnjn.my.id terdaftar (tanpa password).
#   - Perubahan di repo sudah di-commit & push ke origin/master (biar git pull
#     di VPS mendapat kode terbaru).
#
# Alur (semua dijalankan di VPS via SSH):
#   1. git pull (update kode + compose)
#   2. docker build image backend JVM (backend/Dockerfile)
#   3. docker compose up -d --build (rebuild frontend + start backend)
#   4. docker image prune -f (bersihkan dangling)
#   5. verifikasi container & health

set -euo pipefail

VPS_HOST="${VPS_HOST:-frmnjn.my.id}"
VPS_DIR="${VPS_DIR:-/root/expense-tracker}"
IMAGE="${IMAGE:-expense-tracker-backend-jvm:latest}"

# step <label> <bash-cmd...>
# Menjalankan perintah dan menampilkan durasi step (detik).
step() {
    local label="$1"
    shift
    local start=$SECONDS
    echo ""
    echo "==> ${label}"
    "$@"
    echo "    selesai dalam $((SECONDS - start))s"
}

if ! ssh -o BatchMode=yes -o ConnectTimeout=10 "root@${VPS_HOST}" 'true' >/dev/null 2>&1; then
    echo "ERROR: SSH key ke root@${VPS_HOST} tidak berfungsi." >&2
    echo "Pastikan public key terdaftar di ~/.ssh/authorized_keys VPS." >&2
    exit 1
fi

step "[1/5] git pull di VPS" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && git checkout -- . && git pull --ff-only origin master"

step "[2/5] build image backend JVM di VPS (${IMAGE})" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && start=\$(date +%s) && docker build -f backend/Dockerfile -t ${IMAGE} backend/ && echo \"    build selesai dalam \$((\$(date +%s) - start))s\""

step "[3/5] docker compose up -d --build (rebuild frontend + start backend)" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && docker compose -f docker-compose.prod.yml up -d --build"

step "[4/5] bersihkan image dangling di VPS" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" "docker image prune -f" || true

step "[5/5] verifikasi" \
    ssh -o BatchMode=yes "root@${VPS_HOST}" \
    "cd ${VPS_DIR} && docker compose -f docker-compose.prod.yml ps"

echo ""
echo "Deploy VPS selesai. Cek health:"
echo "  curl -s http://localhost:23824/api/health"
