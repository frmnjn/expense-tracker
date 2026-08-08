#!/usr/bin/env bash
#
# deploy-native.sh
#
# Deploy backend GraalVM native image dari PC ke VPS.
#
# Prasyarat:
#   - SSH key id_ed25519 terdaftar di VPS (root).
#   - Image native sudah dibuild lokal: expense-tracker-backend-native:latest
#   - docker-compose.prod.yml sudah mengarah ke image native.
#
# Alur:
#   1. Export image native ke tar.gz (lokal)
#   2. scp tar.gz ke VPS
#   3. VPS: git pull (update compose file)
#   4. VPS: docker load image
#   5. VPS: docker compose -f docker-compose.prod.yml up -d --build
#   6. Verifikasi health

set -euo pipefail

VPS_HOST="${VPS_HOST:-expense.frmnjn.my.id}"
VPS_DIR="${VPS_DIR:-/root/expense-tracker}"
IMAGE="${IMAGE:-expense-tracker-backend-native:latest}"
TARBALL="${TARBALL:-/tmp/backend-native.tar.gz}"

if ! ssh -o BatchMode=yes -o ConnectTimeout=10 "root@${VPS_HOST}" 'true' >/dev/null 2>&1; then
    echo "ERROR: SSH key ke root@${VPS_HOST} tidak berfungsi." >&2
    echo "Pastikan public key terdaftar di ~/.ssh/authorized_keys VPS." >&2
    exit 1
fi

echo "==> [1/6] Export image ${IMAGE} ke ${TARBALL}"
docker save "${IMAGE}" | gzip > "${TARBALL}"
echo "    $(du -h "${TARBALL}" | cut -f1)"

echo "==> [2/6] scp ${TARBALL} ke VPS"
scp -o BatchMode=yes "${TARBALL}" "root@${VPS_HOST}:/tmp/backend-native.tar.gz"

echo "==> [3/6] git pull di VPS"
ssh -o BatchMode=yes "root@${VPS_HOST}" "cd ${VPS_DIR} && git checkout -- . && git pull --ff-only origin master"

echo "==> [4/6] docker load image di VPS"
ssh -o BatchMode=yes "root@${VPS_HOST}" "docker load < /tmp/backend-native.tar.gz"

echo "==> [5/6] up -d dengan docker-compose.prod.yml"
ssh -o BatchMode=yes "root@${VPS_HOST}" "cd ${VPS_DIR} && docker compose -f docker-compose.prod.yml up -d --build"

echo "==> [6/6] Bersihkan image lama (dangling) di VPS"
ssh -o BatchMode=yes "root@${VPS_HOST}" "docker image prune -f" || true

echo "==> [7/7] Verifikasi"
sleep 5
ssh -o BatchMode=yes "root@${VPS_HOST}" "cd ${VPS_DIR} && docker compose -f docker-compose.prod.yml ps; echo '--- memory ---'; docker stats --no-stream --format '{{.Name}} | {{.MemUsage}}' \$(docker ps -q --filter name=expense-tracker_backend) 2>/dev/null || true"

echo ""
echo "Deploy selesai. Cek health:"
echo "  curl -s http://${VPS_HOST}/health"
