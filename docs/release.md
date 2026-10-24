# Release Checklist (v1.0.0)

Dokumen ini merangkum gerbang rilis v1 untuk aplikasi Expense Tracker.

## Prasyarat produksi

- `ACCESS_CODE` terisi. API **fail-closed** (503) bila kosong; jangan set
  `ACCESS_ALLOW_BLANK=true` di produksi.
- `backend/.env` berisi kredensial DB, AI, dan notifier yang valid.
- Container MySQL di VPS dan notifier STB (`<stb-wireguard-ip>:8081`) sehat.
- Backup terakhir ada dan bisa di-restore.

## Gerbang otomatis

CI (`.github/workflows/ci.yml`) wajib hijau:

- backend: `mvn -B test`
- frontend: `npm ci`, `npm run lint`, `npm run build`
- notifier: `go build ./...`
- compose: `docker compose config` untuk dev, prod, dan demo

## Deploy

```bash
PUBLIC_BASE_URL=https://<domain-produksi-anda> \
VPS_HOST=<host-vps> VPS_DIR=/root/expense-tracker ./deploy-vps.sh
```

Script melakukan:

1. `git pull` di VPS.
2. Backup database sebelum migrasi.
3. Build image JVM dengan tag commit + `latest`, menyimpan tag
   `rollback-<timestamp>` untuk image sebelumnya.
4. `docker compose -f docker-compose.prod.yml up -d --build`.
5. Health gate `/ready` (cek koneksi DB) dengan retry.
6. `docker image prune -f` (hanya dangling image; tag rollback dipertahankan).

## Smoke test setelah deploy

- `https://<domain-produksi-anda>/api/health` mengembalikan `{"success":true}`.
- Login dengan access code berhasil.
- Catat pengeluaran manual + foto.
- Scan struk → review → submit; tanggal tidak boleh future.
- Voice note → review → submit.
- Inbox email → import.
- Riwayat: filter, edit, hapus.
- Dashboard: kartu budget, ranking, tren.
- Laporan: daftar dan unduh PDF.
- Log backend/notifier memuat `trace.id` yang sama untuk satu request.

## Rollback

1. Lihat tag image: `docker images | grep expense-tracker-backend-jvm`.
2. Jalankan image `expense-tracker-backend-jvm:rollback-<timestamp>`.

```bash
docker tag expense-tracker-backend-jvm:rollback-<timestamp> expense-tracker-backend-jvm:latest
cd /root/expense-tracker
docker compose -f docker-compose.prod.yml up -d --no-build
```

3. Bila migrasi DB bermasalah, restore backup pra-deploy:

```bash
./scripts/restore_mysql.sh -f backups/expense_tracker_<timestamp>.sql.gz
```

## Known limitations v1

- Single-user; autentikasi berupa satu shared access code, bukan akun per user.
- Notifikasi email bersifat fire-and-log (tidak ada outbox/retry permanen).
- Retry AI dibatasi 5 attempt default; fallback DeepSeek sekali.
- Native image tetap opsional/legacy; produksi memakai JVM.
- Perubahan periode (payday override) menghitung ulang data historis.
