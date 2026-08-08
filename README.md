# Expense Tracker

Aplikasi web sederhana untuk mencatat pengeluaran harian.

Data disimpan di database **MySQL** (berbagi instance dengan WordPress di VPS, memakai database `expense_tracker` terpisah).

---

# Features

* Menambah pengeluaran
* Input waktu otomatis atau manual
* Dropdown budget dari database
* Saldo per budget (otomatis berkurang saat pengeluaran, ditambah via top-up tiap gajian)
* Preview "Saldo nanti" saat mengisi nominal
* Daftar pengeluaran per periode (halaman Riwayat)
* Filter & sort di Riwayat (search nama, filter budget, urutkan)
* Edit pengeluaran
* Hapus pengeluaran (soft delete, saldo dikembalikan)
* Preview perubahan saldo saat edit/hapus
* Dashboard ringkasan per periode (total, saldo per budget, pengeluaran terbesar)
* Dashboard 3 bulan terakhir (total + transaksi per periode, bar visual)
* Top-up saldo via aplikasi (tambah saldo budget + riwayat)
* Tambah & hapus budget (soft delete, hapus juga expense-nya)
* Upload & lihat foto invoice (opsional)
* Validasi input
* Menyimpan data ke MySQL
* REST API menggunakan Java Spring Boot
* React Frontend
* Docker Compose
* Mobile-friendly & PWA (bisa ditambahkan ke Home Screen dari HP)
* Dark mode (toggle, tersimpan di perangkat)

---

# Tech Stack

## Frontend

* React
* TypeScript
* Vite
* Mantine UI
* TanStack Query
* Axios

## Backend

* Java 25 (LTS)
* Spring Boot 4
* Maven
* Spring JDBC (JdbcTemplate)
* **GraalVM 25 Native Image** (produksi memakai native executable, bukan JVM)

## Storage

* MySQL 8+

## Infrastructure

* Docker
* Docker Compose
* nginx (reverse proxy + SSL)

---

# Project Structure

```text
expense-tracker/
│
├── PRD.md
├── AGENTS.md
├── TASKS.md
├── STACK.md
├── README.md
│
├── frontend/          (React + Mantine, nginx proxy /api)
├── backend/           (Spring Boot, GraalVM native)
│   ├── Dockerfile         (image JVM, untuk pengembangan)
│   ├── Dockerfile.native  (image native, untuk produksi)
│   ├── native-config/     (reachability-metadata.json hasil tracing agent)
│   ├── generate-native-config.sh  (regenerate config native)
│   └── src/
│
├── deploy/            (nginx config untuk VPS)
├── build-native.sh    (build image native lokal)
├── deploy-native.sh   (kirim image native ke VPS)
│
└── backend-golang/  (implementasi lama, tidak digunakan)
```

## Runtime Backend: JVM vs Native

Produksi memakai **GraalVM Native Image** (bukan JVM). Lihat `backend/Dockerfile.native`.

| Metrik | JVM | Native |
|---|---|---|
| RSS | ~171 MiB | **~54 MiB** |
| Startup | ~2.3 s | **0.58 s** |
| mem_limit | 256m | 128m |

`docker-compose.yml` & `docker-compose.prod.yml` memakai image `expense-tracker-backend-native:latest`.

`backend/Dockerfile` (JVM) tetap dipertahankan sebagai fallback & untuk pengembangan.

---

# Prerequisites

Pastikan sudah menginstall:

* Docker
* Docker Compose

Tidak diperlukan instalasi Node.js, Java, maupun Maven apabila menjalankan project menggunakan Docker.

---

# Database Setup (MySQL)

Untuk **lokal**, `docker-compose.yml` sudah menyertakan service `mysql` sendiri (self-contained): skema & budget default dibuat otomatis oleh **Flyway** saat backend start. Tidak perlu setup manual untuk development lokal.

Untuk **produksi**, aplikasi memakai MySQL yang sudah ada di VPS (berbagi instance dengan WordPress). Perlu database dan user terpisah untuk expense tracker:

1. Buat database:

```sql
CREATE DATABASE expense_tracker;
```

2. Buat user khusus (terpisah dari WordPress) dan beri hak akses:

```sql
CREATE USER 'expense_tracker_user'@'%' IDENTIFIED BY 'password-kuat';
GRANT ALL PRIVILEGES ON expense_tracker.* TO 'expense_tracker_user'@'%';
FLUSH PRIVILEGES;
```

3. Skema tabel dibuat otomatis oleh backend saat start melalui **Flyway** (migration versioned di `backend/src/main/resources/db/migration`). Untuk DB yang sudah ada tanpa riwayat Flyway, `baselineOnMigrate` akan mem-baseline dan menerapkan migration selanjutnya.

4. Isi tabel `budgets` (nama + saldo) — bisa diimpor dari tab `Budget` Google Sheets yang lama:

```bash
python3 scripts/seed_budgets.py > seed.sql
mysql -u expense_tracker_user -p expense_tracker < seed.sql
```

5. Backend menghubungi MySQL melalui host port (mis. `33060`) yang sudah di-expose oleh compose WordPress:

```env
DB_URL=jdbc:mysql://host.docker.internal:33060/expense_tracker?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=expense_tracker_user
DB_PASSWORD=password-kuat
```

Mode normal tetap menggunakan:

```bash
docker compose up --build
```

---

# Environment Variables

## Backend

Buat file:

```text
backend/.env
```

Contoh:

```env
PORT=8080
DB_URL=jdbc:mysql://host.docker.internal:33060/expense_tracker?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=expense_tracker_user
DB_PASSWORD=change-me
UPLOAD_DIR=/app/uploads
```

---

## Frontend

Buat file:

```text
frontend/.env
```

Contoh:

```env
VITE_API_URL=/api
```

Jika kosong, frontend memakai `/api` (default). Nilai ini relatif dan diproxy oleh nginx ke backend, sehingga tidak perlu diubah untuk akses dari HP. Untuk development lokal dengan Vite, set `VITE_API_URL=http://localhost:8080`.

---

# Backup & Restore MySQL

Backup database `expense_tracker` (berbagi instance MySQL dengan WordPress) via script di VPS.

## Backup

```bash
cd /root/expense-tracker
./scripts/backup_mysql.sh
```

Membuat `backups/expense_tracker_<timestamp>.sql.gz` dan menyimpan 14 backup terakhir (variabel `KEEP`, `BACKUP_DIR`, `MYSQL_CONTAINER` bisa di-override).

## Jadwal otomatis (cron)

Cron harian pukul 20:00 UTC (03:00 WIB) sudah dipasang di VPS:

```
0 20 * * * /root/expense-tracker/scripts/backup_mysql.sh >> /var/log/expense-backup.log 2>&1
```

## Restore

```bash
cd /root/expense-tracker
./scripts/restore_mysql.sh backups/expense_tracker_20260808_124931.sql.gz
```

**Peringatan:** restore akan menimpa data `expense_tracker` saat ini.

---

# Running the Application

Jalankan:

```bash
docker compose up --build
```

Frontend:

```text
http://localhost:3000
```

Backend:

```text
http://localhost:8080
```

Untuk menghentikan aplikasi:

```bash
docker compose down
```

## Akses dari HP

Frontend sudah dikonfigurasi agar API dipanggil relatif (`/api`) dan diproxy oleh nginx ke backend, sehingga bisa diakses dari perangkat lain (termasuk HP) tanpa mengubah konfigurasi:

1. Cari IP komputer di jaringan lokal (misal `192.168.1.10`).
2. Dari HP, buka browser ke `http://<IP-KOMPUTER>:3000`.
3. Pastikan HP dan komputer berada di jaringan yang sama.

Untuk pengalaman seperti aplikasi native di HP, buka halaman tersebut lalu pilih **Add to Home Screen** (PWA).

---

# API

## POST /expenses

Request

```json
{
  "dateTime": "2026-08-06 14:30",
  "name": "Makan Siang",
  "budget": "Daily",
  "amount": 35000,
  "description": "Catatan"
}
```

Response

```json
{
  "success": true
}
```

---

## GET /options

Response

```json
{
  "success": true,
  "data": {
    "budgets": [
      { "name": "Daily", "balance": 500000 },
      { "name": "Weekly", "balance": -10000 }
    ]
  }
}
```

---

## GET /periods

Response

```json
{
  "success": true,
  "data": {
    "periods": ["2026-JUL-AUG", "2026-AUG-SEP"]
  }
}
```

---

## GET /expenses

Query param `period` (nama sheet periode, wajib).

Response

```json
{
  "success": true,
  "data": {
    "expenses": [
      {
        "id": "abc123",
        "dateTime": "2026-08-06 14:30",
        "name": "Makan Siang",
        "budget": "Daily",
        "amount": 35000,
        "description": "Catatan"
      }
    ]
  }
}
```

Baris yang dihapus (soft delete) tidak dikembalikan.

---

## GET /topups

Mengembalikan daftar top-up saldo.

```json
{
  "success": true,
  "data": {
    "topUps": [
      { "id": "abc123", "dateTime": "2026-08-07 10:00", "budget": "Daily", "amount": 50000, "description": "Gaji" }
    ]
  }
}
```

---

## GET /trend

Ringkasan beberapa periode terakhir. Query param `months` (default 3).

```json
{
  "success": true,
  "data": {
    "periods": [
      { "period": "2026-JUL-AUG", "total": 30000, "count": 2 }
    ]
  }
}
```

---

## POST /topups

Menambahkan saldo pada budget. Body: `{ "budget": "Daily", "amount": 50000, "description": "Gaji" }`. `dateTime` opsional (default waktu sekarang). Saldo budget bertambah dan riwayat dicatat di tab `TopUp`.

---

## PUT /expenses/{id}

Body sama seperti `POST /expenses`. Mengedit pengeluaran dan menyesuaikan saldo budget.

---

## DELETE /expenses/{id}

Menghapus (soft delete) pengeluaran dan mengembalikan saldo budget.

---

# Validation Rules

Waktu

* Required
* Format `yyyy-MM-dd HH:mm`

Name

* Required
* Maksimal 255 karakter

Budget

* Required

Nominal (amount)

* Required
* Harus lebih besar dari 0

Description

* Opsional
* Maksimal 255 karakter

---

# Development Workflow

1. Baca `PRD.md`.
2. Ikuti aturan pada `AGENTS.md`.
3. Kerjakan item pada `TASKS.md`.
4. Gunakan teknologi yang ditentukan pada `STACK.md`.
5. Jalankan aplikasi menggunakan Docker Compose.
6. Pastikan fitur bekerja sebelum menandai task sebagai selesai.

---

# Deployment ke VPS

> **Penting:** Produksi memakai **image native** yang di-build di lokal, lalu dikirim ke VPS via `deploy-native.sh`. VPS **tidak build native** (butuh RAM besar).

## Prasyarat

* Repo sudah di-clone di VPS (`/root/expense-tracker`).
* `backend/.env` berisi `GOOGLE_SHEET_ID` produksi.
* `backend/credentials.json` (Service Account) sudah ada di VPS. Kedua file tidak ikut di-track git (lihat `.gitignore`), jadi harus disiapkan manual di VPS.
* SSH key (`~/.ssh/id_ed25519`) terdaftar di `root@<VPS>` authorized_keys (tanpa password).
* DNS: `expense.frmnjn.my.id` → A record ke IP VPS.

## Alur deploy (native image)

Dari PC lokal:

```bash
./build-native.sh     # 1. Build image native: expense-tracker-backend-native:latest (~6 menit, RAM ~7GB)
./deploy-native.sh    # 2. export tar.gz -> scp ke VPS -> git pull -> docker load -> up -d
```

`deploy-native.sh` menangani:
1. `docker save <image> | gzip > /tmp/backend-native.tar.gz`
2. `scp` tar.gz ke VPS
3. VPS: `git pull` (update compose file)
4. VPS: `docker load`
5. VPS: `docker compose -f docker-compose.prod.yml up -d --build` (rebuild frontend, backend pakai image native)
6. Verifikasi `docker compose ps` + memory

## Reverse proxy + SSL

1. Install nginx & certbot di VPS:
   ```bash
   sudo apt update && sudo apt install -y nginx certbot python3-certbot-nginx
   ```
2. Copy konfigurasi nginx:
   ```bash
   sudo cp deploy/nginx-expense.conf /etc/nginx/sites-available/expense
   sudo ln -s /etc/nginx/sites-available/expense /etc/nginx/sites-enabled/expense
   ```
3. Terbitkan sertifikat SSL (Let's Encrypt):
   ```bash
   sudo certbot --nginx -d expense.frmnjn.my.id
   ```
4. Verifikasi:
   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   ```

nginx proxy ke `127.0.0.1:23824` (port frontend yang di-expose oleh `docker-compose.prod.yml`).

## Mengelola config native (reachability-metadata)

Backend Google Sheets butuh config refleksi untuk native image. File `backend/native-config/reachability-metadata.json` di-generate via tracing agent:

```bash
./backend/generate-native-config.sh   # build jar -> jalankan agent -> hit test sheet -> simpan config
```

Jalankan ulang bila:
* Upgrade versi library Google (`google-api-services-sheets`, `google-http-client`, dll)
* Menambah endpoint baru yang memanggil Google Sheets

> Aman: script memakai `GOOGLE_TEST_SHEET_ID`, bukan sheet produksi.

## Rollback ke image JVM

Jika native bermasalah, kembalikan ke image JVM:

1. VPS: `docker compose -f docker-compose.prod.yml down`
2. Ubah `docker-compose.prod.yml` backend dari `image:` kembali ke `build: ./backend`
3. `docker compose -f docker-compose.prod.yml up -d --build`

---

# Future Roadmap

Versi berikutnya dapat menambahkan:

* Kategori
* Grafik tren yang lebih detail
* Multi-user
* Authentication

---

# License

Project ini dibuat untuk pembelajaran dan penggunaan pribadi.
