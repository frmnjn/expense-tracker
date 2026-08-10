#!/usr/bin/env bash
#
# deploy-vps.sh
#
# Deploy perubahan aplikasi (frontend rebuild + restart backend) ke VPS.
# Digunakan untuk perubahan frontend/notifier/compose TANPA rebuild image
# native backend. Kalau kode backend (Java) berubah, pakai ./deploy-native.sh
# (transfer image native).
#
# Prasyarat:
#   - Perubahan sudah di-commit & push ke origin/master.
#   - SSH key root@expense.frmnjn.my.id terdaftar.

set -euo pipefail

VPS_HOST="${VPS_HOST:-expense.frmnjn.my.id}"
VPS_DIR="${VPS_DIR:-/root/expense-tracker}"

if ! ssh -o BatchMode=yes -o ConnectTimeout=10 "root@${VPS_HOST}" 'true' >/dev/null 2>&1; then
    echo "ERROR: SSH key ke root@${VPS_HOST} tidak berfungsi." >&2
    exit 1
fi

echo "==> [1/4] git pull di VPS"
ssh -o BatchMode=yes "root@${VPS_HOST}" "cd ${VPS_DIR} && git checkout -- . && git pull --ff-only origin master"

echo "==> [2/4] docker compose up -d --build (rebuild frontend, restart backend)"
ssh -o BatchMode=yes "root@${VPS_HOST}" "cd ${VPS_DIR} && docker compose -f docker-compose.prod.yml up -d --build"

echo "==> [3/4] bersihkan image dangling di VPS"
ssh -o BatchMode=yes "root@${VPS_HOST}" "docker image prune -f" || true

echo "==> [4/4] hapus image lama project yang tidak dipakai"
ssh -o BatchMode=yes "root@${VPS_HOST}" "docker rmi \
    expense-tracker-backend:latest \
    expense-tracker_backend:latest \
    expense-tracker_frontend:latest \
    expense-tracker-notifier:latest 2>/dev/null || true"

echo ""
echo "Deploy VPS selesai. Cek: curl -s https://${VPS_HOST}/health"
