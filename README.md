# Expense Tracker

Aplikasi web sederhana untuk mencatat pengeluaran harian.

Data tidak disimpan di database, melainkan langsung di Google Sheets menggunakan Google Sheets API.

---

# Features

* Menambah pengeluaran
* Input waktu otomatis atau manual
* Dropdown budget dari Google Sheets
* Saldo per budget (otomatis berkurang saat pengeluaran, ditambah manual di sheet tiap gajian)
* Preview "Saldo nanti" saat mengisi nominal
* Daftar pengeluaran per periode (halaman Riwayat)
* Edit pengeluaran
* Hapus pengeluaran (soft delete, saldo dikembalikan)
* Preview perubahan saldo saat edit/hapus
* Dashboard ringkasan per periode (total, saldo per budget, pengeluaran terbesar)
* Top-up saldo via aplikasi (tambah saldo budget + riwayat)
* Sheet pengeluaran terpisah per periode
* Kolom nominal & saldo diformat currency IDR
* Urutan sheet otomatis (periode terbaru di kiri)
* Validasi input
* Menyimpan data ke Google Sheets
* REST API menggunakan Java Spring Boot
* React Frontend
* Docker Compose
* Tanpa database
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
* **GraalVM 25 Native Image** (produksi memakai native executable, bukan JVM)
* Google Sheets API

## Storage

* Google Sheets API

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

# Google Sheets Setup

1. Buat Google Spreadsheet.

2. Buat satu sheet untuk data dropdown dengan header di baris 1:

   * Sheet `Budget`, kolom A berisi daftar nama budget.

3. Buat Google Cloud Service Account.

4. Aktifkan Google Sheets API.

5. Download credential JSON.

6. Share spreadsheet ke email Service Account dengan hak akses **Editor**.

7. Simpan file credential sebagai:

```text
backend/credentials.json
```

Sheet pengeluaran per periode (format nama `YYYY-MON-MON`, contoh `2026-JAN-FEB`) dibuat otomatis oleh backend beserta header.

Urutan sheet otomatis diatur oleh backend: periode terbaru paling kiri, lalu periode lama ke kanan, dan tab `Budget` selalu paling kanan. Reorder dilakukan saat backend start dan saat sheet periode baru dibuat.

## Testing Spreadsheet (opsional)

Untuk mencegah test menulis ke spreadsheet produksi, buat spreadsheet terpisah untuk testing:

1. Buat Google Spreadsheet terpisah.
2. Buat tab `Budget` (kolom A, header baris 1) — agar test `getOptions` berjalan.
3. Share spreadsheet ke email Service Account yang sama dengan hak akses **Editor**.
4. Isi ID spreadsheet testing pada `GOOGLE_TEST_SHEET_ID` di `backend/.env`.

Jika `GOOGLE_TEST_SHEET_ID` kosong, integration test akan di-skip.

## Mode Testing (smoke test dengan Docker)

Untuk mencegah smoke test menulis ke spreadsheet produksi, jalankan backend dengan override compose yang memaksa memakai test spreadsheet:

```bash
docker compose --env-file backend/.env -f docker-compose.yml -f docker-compose.test.yml up --build
```

Backend akan memakai `GOOGLE_TEST_SHEET_ID` sebagai `GOOGLE_SHEET_ID`. Jika `GOOGLE_TEST_SHEET_ID` kosong, compose akan menolak berjalan.

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
GOOGLE_SHEET_ID=YOUR_GOOGLE_SHEET_ID
GOOGLE_TEST_SHEET_ID=YOUR_TEST_GOOGLE_SHEET_ID
GOOGLE_APPLICATION_CREDENTIALS=/app/credentials.json
GOOGLE_BUDGET_SHEET=Budget
GOOGLE_TOP_UP_SHEET=TopUp
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
* Filter
* Dashboard
* Grafik
* Multi-user
* Authentication

---

# License

Project ini dibuat untuk pembelajaran dan penggunaan pribadi.
