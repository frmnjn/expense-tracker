# PRD.md

# Expense Tracker

## Overview

Aplikasi web untuk mencatat pengeluaran harian, memantau saldo per budget, dan mengelola riwayat.

Data disimpan di database **MySQL** (berbagi instance dengan WordPress di VPS, memakai database `expense_tracker` terpisah).

Target: sederhana, mudah dijalankan dengan Docker, mudah dikembangkan.

---

# Objectives

* Mencatat pengeluaran.
* Memantau saldo tiap budget (ditambah via top-up, berkurang saat pengeluaran).
* Mengelola budget (tambah, edit, hapus).
* Melihat daftar pengeluaran per periode beserta foto invoice.
* Menjalankan seluruh aplikasi dengan Docker Compose.

---

# Tech Stack

## Frontend

* React 19 + Vite + TypeScript
* React Router
* TanStack Query
* Axios
* Mantine UI

## Backend

* Java 25 + Spring Boot 4
* Spring JDBC (JdbcTemplate)
* Flyway (DB migration)
* GraalVM 25 Native Image (produksi)

## Storage

* MySQL 8+

## Infrastructure

* Docker Compose
* nginx (reverse proxy + SSL)

---

# Halaman

* `/` — halaman Catat (form pengeluaran + foto invoice).
* `/dashboard` — ringkasan saldo per budget, top-up, riwayat top-up, dan 3 bulan terakhir.
* `/riwayat` — daftar pengeluaran per periode, dengan filter/sort, edit, hapus, dan lihat foto.
* `/catat` — alias halaman Catat.

Saat aplikasi dibuka, user langsung melihat halaman Catat.

---

# Database (MySQL)

Database `expense_tracker` pada instance MySQL yang berbagi dengan WordPress. Skema dikelola oleh **Flyway** (migration versioned di `backend/src/main/resources/db/migration`).

Tabel:

| Tabel | Kolom |
| ----- | ----- |
| `budgets` | `id` PK, `name` UNIQUE, `balance`, `is_active` |
| `expenses` | `id` PK, `period`, `period_start`, `date_time`, `budget_id` FK→`budgets`, `name`, `amount`, `description`, `deleted`, `photo_path` |
| `top_ups` | `id` PK, `date_time`, `budget_id` FK→`budgets`, `amount`, `description` |

Migration saat ini:

* `V1__init.sql` — skema awal.
* `V2__budget_is_active.sql` — kolom `is_active` pada budgets (idempotent).
* `V3__seed_default_budgets.sql` — seed budget default (`INSERT IGNORE`).
* `V4__expense_photo.sql` — kolom `photo_path` pada expenses.

Periode (format `YYYY-MON-MON`, contoh `2026-JAN-FEB`) dihitung dari `date_time` dengan aturan cut-off tanggal 25. `period` disimpan untuk filter cepat.

Penghapusan expense dilakukan dengan **soft delete** (`deleted = TRUE`); baris tetap ada di DB dan disaring saat ditampilkan.

`budgets.balance` adalah saldo running dan boleh bernilai negatif.

---

# Fitur

## Catat Pengeluaran

Field:

| Field | Tipe | Required |
| ----- | ---- | -------- |
| Waktu | Datetime (`yyyy-MM-dd HH:mm`) | Ya |
| Name | String (maks 255) | Ya |
| Budget | Dropdown | Ya |
| Nominal | Number (> 0) | Ya |
| Description | String (maks 255) | Tidak |
| Foto invoice | Gambar (opsional) | Tidak |

* Waktu bisa otomatis (waktu sistem) atau manual.
* Budget diambil dari dropdown (budget aktif).
* Saat nominal diisi, muncul preview "Saldo nanti" (sisa saldo dikurangi nominal; hijau jika >= 0, merah jika negatif).
* Foto: tombol membuka pilihan **"Ambil Foto (Kamera)"** atau **"Dari Galeri"**, dengan preview.
* Submit: create expense → jika ada foto, upload foto → reset form + notifikasi sukses.

## Dashboard

* Daftar saldo per budget — tiap kartu menampilkan:
  * nama budget, badge urutan (1–3) jika masuk pengeluaran terbesar periode
  * saldo saat ini (merah jika negatif)
  * ikon `+` untuk top-up (modal nominal + deskripsi, tampil saldo saat ini)
  * ikon `📋` untuk riwayat top-up budget tersebut
  * ikon `✎` untuk edit budget (nama & saldo)
  * ikon `🗑` untuk hapus budget (konfirmasi)
  * saat periode dipilih: jumlah transaksi + pengeluaran periode budget tersebut
* Tombol "+ Budget" (modal: nama + saldo awal opsional).
* Ringkasan "3 Bulan Terakhir" (total + transaksi per periode, bar visual).

## Riwayat

* Dropdown periode.
* Search nama, filter budget, dan sort (waktu/nominal) — muncul setelah periode dipilih.
* Tabel (desktop) / kartu (mobile) daftar expense aktif.
* Tombol lihat foto (📷) jika expense memiliki foto.
* Tombol edit (modal) dan hapus (konfirmasi, soft delete).
* Preview perubahan saldo saat edit/hapus.

## Budget

* Tambah (nama + saldo awal opsional).
* Edit (nama + saldo).
* Hapus (soft delete): nama diubah jadi `DELETED_<nama>_<id>` sehingga nama asli bisa dipakai ulang; expense di bawah budget ikut soft-delete.

## Top-up

* Tambah saldo budget (nominal + deskripsi), tercatat sebagai riwayat.
* Saldo budget bertambah sebesar nominal.

## Dark Mode

Mode terang/gelap, toggle tersimpan di perangkat (localStorage), default gelap.

---

# API

Semua respons:

```json
{ "success": true, "message": "...", "data": { } }
```

Error menggunakan HTTP status yang sesuai dan `{ "success": false, "message": "..." }`.

## GET /health

Pengecekan status.

## GET /options

Daftar budget aktif beserta saldonya.

```json
{
  "success": true,
  "data": {
    "budgets": [
      { "name": "Daily", "balance": 500000 }
    ]
  }
}
```

## GET /periods

Daftar periode (hanya yang masih punya expense aktif), diurutkan terbaru.

## GET /expenses?period=YYYY-MON-MON

Daftar expense aktif pada periode.

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
        "description": "Catatan",
        "hasPhoto": false
      }
    ]
  }
}
```

## POST /expenses

Membuat expense.

```json
{
  "dateTime": "2026-08-06 14:30",
  "name": "Makan Siang",
  "budget": "Daily",
  "amount": 35000,
  "description": "Catatan"
}
```

Response:

```json
{
  "success": true,
  "data": { "id": "abc123" }
}
```

`data.id` dipakai untuk mengunggah foto.

## PUT /expenses/{id}

Mengedit expense (body sama seperti `POST /expenses`). Saldo disesuaikan ulang.

## DELETE /expenses/{id}

Menghapus (soft delete) expense dan mengembalikan saldo budget.

## POST /expenses/{id}/photo

Mengunggah foto invoice (multipart `file`). Opsional.

* Hanya gambar: jpeg, png, webp, gif.
* Maksimal 10MB.

## GET /expenses/{id}/photo

Mengembalikan file foto invoice. `404` jika tidak ada.

## GET /summary?period=YYYY-MON-MON

Ringkasan periode: total, jumlah transaksi, dan pengeluaran per budget (urut terbesar).

```json
{
  "success": true,
  "data": {
    "period": "2026-JUL-AUG",
    "total": 80000,
    "count": 3,
    "byBudget": [
      { "budget": "Weekly", "amount": 50000, "count": 1 }
    ]
  }
}
```

## GET /trend?months=3

Ringkasan beberapa periode terakhir (total + count), urut terlama ke terbaru.

## GET /topups

Daftar riwayat top-up.

## POST /topups

Menambah saldo budget.

```json
{ "budget": "Daily", "amount": 50000, "description": "Gaji" }
```

`dateTime` opsional (default waktu sekarang).

## POST /budgets

Menambah budget.

```json
{ "name": "Gadget", "balance": 100000 }
```

`balance` opsional (default 0). Duplikat nama → `Budget already exists`.

## PUT /budgets/{name}

Mengedit budget (nama dan/atau saldo). `balance` opsional (jika null, saldo tidak diubah).

## DELETE /budgets/{name}

Menghapus budget (soft delete, rename `DELETED_<nama>_<id>`, expense-nya ikut soft-delete).

---

# Environment Variables

## Backend (`backend/.env`)

```text
PORT=8080
DB_URL=jdbc:mysql://host.docker.internal:33060/expense_tracker?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=expense_tracker_user
DB_PASSWORD=password-kuat
UPLOAD_DIR=/app/uploads
```

## Frontend

```text
VITE_API_URL=/api
```

---

# Docker

```bash
docker compose up --build
```

Frontend: http://localhost:3000 · Backend: http://localhost:8080

Untuk **lokal**, `docker-compose.yml` menyertakan service `mysql` sendiri (self-contained); skema & seed budget dibuat otomatis oleh Flyway. Foto tersimpan di direktori `./uploads` (bind mount).

Untuk **produksi**, `docker-compose.prod.yml` memakai MySQL VPS dan image native.

---

# Acceptance Criteria

* User dapat membuka aplikasi dari browser.
* User dapat mencatat pengeluaran (dengan/ tanpa foto invoice).
* User dapat melihat saldo per budget dan menambah saldo (top-up).
* User dapat mengelola budget (tambah/edit/hapus).
* User dapat melihat riwayat per periode, filter/sort, edit, hapus, dan lihat foto.
* Data tersimpan di MySQL.
* Aplikasi dapat dijalankan dengan Docker Compose.

---

# Out of Scope

* Login / authentication
* Multi-user
* Kategori/tag
* Grafik detail
* Export PDF/Excel
* Notifikasi

---

# Future Improvements

* Authentication & multi-user
* Grafik tren yang lebih detail
* Export CSV/Excel
* Ganti/hapus foto saat edit expense
* Notifikasi/pengingat & peringatan budget menipis
