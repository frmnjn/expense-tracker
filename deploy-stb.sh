#!/usr/bin/env bash
#
# deploy-stb.sh
#
# Deploy microservice notifier ke STB (Armbian) yang menyala 24/7.
# Mengakses STB lewat WireGuard (10.8.0.4) dan menjalankan notifier via
# docker-compose.stb.yml (build image arm64 langsung di STB).
#
# Prasyarat:
#   - Perubahan sudah di-commit & push ke origin/master.
#   - SSH key root@10.8.0.4 terdaftar.
#   - STB sudah punya repo (git clone) + file .env (SMTP/Resend, gitignored).

set -euo pipefail

STB_HOST="${STB_HOST:-10.8.0.4}"
STB_DIR="${STB_DIR:-/root/expense-tracker}"

if ! ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 "root@${STB_HOST}" 'true' >/dev/null 2>&1; then
    echo "ERROR: SSH key ke root@${STB_HOST} tidak berfungsi." >&2
    exit 1
fi

echo "==> [1/4] git pull di STB"
ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new "root@${STB_HOST}" "cd ${STB_DIR} && git checkout -- . && git clean -fd && git pull --ff-only origin master"

echo "==> [2/4] rebuild & up notifier"
ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new "root@${STB_HOST}" "cd ${STB_DIR} && docker compose -f docker-compose.stb.yml up -d --build"

echo "==> [3/4] bersihkan image dangling di STB"
ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new "root@${STB_HOST}" "docker image prune -f" || true

echo "==> [4/4] hapus image lama project yang tidak dipakai (jangan hapus notifier yang sedang jalan)"
ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new "root@${STB_HOST}" "docker rmi \
    expense-tracker-backend:latest \
    expense-tracker_backend:latest \
    expense-tracker_frontend:latest 2>/dev/null || true"

echo ""
echo "Deploy STB selesai. Cek: curl -s http://${STB_HOST}:8081/health"
