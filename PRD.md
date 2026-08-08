# PRD.md

# Expense Tracker v1

## Overview

Expense Tracker adalah aplikasi web sederhana untuk mencatat pengeluaran harian.

Aplikasi menggunakan database **MySQL** (berbagi instance dengan WordPress di VPS, memakai database `expense_tracker` terpisah).

Target utama aplikasi adalah sederhana, mudah dijalankan secara lokal menggunakan Docker, dan mudah dikembangkan di masa depan.

---

# Objectives

Membangun aplikasi yang memungkinkan pengguna:

* Menambahkan data pengeluaran melalui web.
* Menyimpan data ke database MySQL.
* Menjalankan seluruh aplikasi menggunakan Docker.

---

# Tech Stack

## Frontend

* React
* Vite
* TypeScript
* React Router
* TanStack Query
* Axios

## UI

* Mantine UI

## Backend

* Java
* Spring Boot

## Storage

MySQL 8+

## Backend

* Java
* Spring Boot
* Spring JDBC (JdbcTemplate)

Menggunakan database MySQL (berbagi instance dengan WordPress, database `expense_tracker` terpisah).

## Container

Docker

Docker Compose

---

# Functional Requirements

## Create Expense

User dapat menambahkan satu pengeluaran.

Field yang harus diisi:

| Field       | Type                 | Required |
| ----------- | -------------------- | -------- |
| Waktu       | Datetime             | Yes      |
| Name        | String               | Yes      |
| Budget      | String (dropdown)    | Yes      |
| Nominal     | Number               | Yes      |
| Description | String               | No       |

Contoh:

Waktu

2026-08-06 14:30

Name

Makan Siang

Budget

Daily

Nominal

35000

Description

Catatan (opsional)

Waktu dapat diisi secara otomatis menggunakan waktu sistem saat ini atau diisi manual.

Budget diambil dari dropdown yang datanya bersumber dari Google Sheets.

---

## Validation

Waktu

* wajib diisi
* format `yyyy-MM-dd HH:mm`

Name

* wajib diisi
* maksimal 255 karakter

Budget

* wajib diisi

Nominal

* wajib diisi
* harus lebih besar dari 0

Description

* opsional
* maksimal 255 karakter

---

## Submit

Ketika tombol **Save** ditekan:

Frontend akan memanggil API Backend.

Backend akan melakukan validasi.

Jika valid:

Backend akan menyimpan data ke Google Sheets.

Response:

```json
{
    "success": true
}
```

Jika gagal:

```json
{
    "success": false,
    "message": "error message"
}
```

---

# Database (MySQL)

Aplikasi memakai database `expense_tracker` pada instance MySQL yang berbagi dengan WordPress. Skema dibuat otomatis oleh backend saat start melalui **Flyway** (migration versioned di `backend/src/main/resources/db/migration`). Untuk DB yang sudah ada tanpa riwayat Flyway, `baselineOnMigrate` akan mem-baseline dan menerapkan migration berikutnya.

Tabel:

| Tabel | Kolom |
| ----- | ----- |
| `budgets` | `id` PK, `name` UNIQUE, `balance`, `is_active` |
| `expenses` | `id` PK, `period`, `period_start`, `date_time`, `budget_id` FK→`budgets`, `name`, `amount`, `description`, `deleted` |
| `top_ups` | `id` PK, `date_time`, `budget_id` FK→`budgets`, `amount`, `description` |

Periode (format `YYYY-MON-MON`, contoh `2026-JAN-FEB`) dihitung dari `date_time` dengan aturan cut-off tanggal 25. `period` disimpan untuk filter cepat.

Penghapusan expense dilakukan dengan **soft delete** (kolom `deleted = TRUE`); baris tetap ada di DB dan disaring saat ditampilkan.

`budgets.balance` adalah saldo running dan boleh bernilai negatif.

Penambahan saldo (misalnya tiap gajian tanggal 25) dilakukan melalui aplikasi (`POST /topups`); setiap top-up menambah saldo budget dan dicatat pada tabel `top_ups`.

Setiap expense yang tersimpan otomatis mengurangi saldo budget terkait sebesar nominalnya (atomic dalam transaksi).

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

Mengembalikan daftar budget beserta saldonya untuk dropdown.

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

Mengembalikan daftar periode (nama sheet periode) untuk dropdown bulan pada halaman Riwayat.

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

Mengembalikan daftar expense aktif pada suatu periode.

Query param:

* `period` (wajib): nama sheet periode, contoh `2026-JUL-AUG`.

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

Baris dengan `Deleted=TRUE` tidak dikembalikan.

---

## GET /summary

Mengembalikan ringkasan pengeluaran pada suatu periode.

Query param:

* `period` (wajib): nama sheet periode, contoh `2026-JUL-AUG`.

Response

```json
{
    "success": true,
    "data": {
        "period": "2026-JUL-AUG",
        "total": 80000,
        "count": 3,
        "byBudget": [
            { "budget": "Weekly", "amount": 50000, "count": 1 },
            { "budget": "Daily", "amount": 30000, "count": 2 }
        ]
    }
}
```

`byBudget` diurutkan dari pengeluaran terbesar.

---

## GET /trend

Mengembalikan ringkasan pengeluaran untuk beberapa periode terakhir.

Query param:

* `months` (opsional, default `3`): jumlah periode terakhir.

Response

```json
{
    "success": true,
    "data": {
        "periods": [
            { "period": "2026-JUL-AUG", "total": 30000, "count": 2 },
            { "period": "2026-AUG-SEP", "total": 50000, "count": 1 }
        ]
    }
}
```

`periods` diurutkan dari periode terlama ke terbaru.

---

## POST /budgets

Menambahkan budget baru.

Request

```json
{
    "name": "Gadget",
    "balance": 100000
}
```

`balance` opsional (default `0`).

Response

```json
{
    "success": true
}
```

Jika nama sudah ada (termasuk budget yang dihapus), mengembalikan error `Budget already exists`.

---

## PUT /budgets/{name}

Mengedit budget: mengubah nama dan/atau saldo.

Request

```json
{
    "name": "Gadget",
    "balance": 150000
}
```

`balance` opsional; jika kosong/null, saldo tidak diubah.

Response

```json
{
    "success": true
}
```

Jika nama tujuan sudah dipakai budget lain, mengembalikan error `Budget already exists`. Jika budget tidak ditemukan, `Budget not found`.

---

## DELETE /budgets/{name}

Menghapus budget (soft delete — baris dipertahankan dan namanya diubah menjadi `DELETED_<nama>_<id>`, ditandai tidak aktif, dan tidak muncul lagi). Pengubahan nama ini membebaskan nama asli sehingga budget dengan nama yang sama bisa dibuat ulang.

Response

```json
{
    "success": true
}
```

Jika budget tidak ditemukan, mengembalikan error `Budget not found`.

---

## GET /topups

Mengembalikan daftar top-up saldo.

Response

```json
{
    "success": true,
    "data": {
        "topUps": [
            {
                "id": "abc123",
                "dateTime": "2026-08-07 10:00",
                "budget": "Daily",
                "amount": 50000,
                "description": "Gaji"
            }
        ]
    }
}
```

---

## POST /topups

Menambahkan saldo pada suatu budget.

Request

```json
{
    "budget": "Daily",
    "amount": 50000,
    "description": "Gaji"
}
```

`dateTime` opsional; jika kosong, menggunakan waktu sistem saat ini.

Response

```json
{
    "success": true
}
```

Saldo budget bertambah sebesar nominal dan riwayat dicatat pada tab `TopUp`.

---

## PUT /expenses/{id}

Mengedit satu expense pada periode yang sesuai.

Request body sama seperti `POST /expenses`.

Response

```json
{
    "success": true
}
```

Validasi sama seperti `POST /expenses`. Saldo budget disesuaikan ulang berdasarkan perubahan.

---

## DELETE /expenses/{id}

Menghapus (soft delete) satu expense.

Response

```json
{
    "success": true
}
```

Baris ditandai `Deleted=TRUE` dan saldo budget dikembalikan sebesar nominal.

---

# Error Response

```json
{
    "success": false,
    "message": "DateTime is required"
}
```

---

# UI

Terdapat tiga halaman:

* Halaman form pencatatan pengeluaran (`/`).
* Halaman `Dashboard` (`/dashboard`) — ringkasan pengeluaran per periode.
* Halaman `Riwayat` (`/riwayat`) — daftar, edit, dan hapus pengeluaran.

Saat aplikasi dibuka, user langsung melihat form pencatatan pengeluaran.

### Dashboard

Komponen:

* Dropdown periode
* Daftar saldo per budget — tiap kartu menampilkan:
  * nama budget, dengan badge urutan (1–3) jika masuk pengeluaran terbesar periode
  * saldo saat ini (merah jika negatif)
  * ikon `+` untuk menambah saldo (modal: nominal + deskripsi)
  * ikon `📋` untuk membuka riwayat top-up budget tersebut
  * ikon `✎` untuk mengedit budget (nama & saldo)
  * ikon `🗑` untuk menghapus budget (soft delete, dengan konfirmasi)
  * saat periode dipilih: jumlah transaksi + pengeluaran periode budget tersebut
* Tombol "Tambah Budget" (modal: nama + saldo awal opsional)
* Ringkasan 3 bulan terakhir (total + transaksi per periode, dengan bar visual)

Komponen halaman form:

* Input Waktu (opsi: waktu sistem saat ini atau manual)
* Name Text Field
* Budget Dropdown
* Nominal Number Field
* Description Text Field (opsional)
* Save Button

### Budget Dropdown & Preview Saldo

Dropdown Budget ditampilkan dua kolom:

* nama budget di kiri
* saldo di kanan (rata kanan, font mono, merah jika negatif)

Setelah Nominal diisi, muncul kartu preview:

* Sisa saldo saat ini
* Nominal
* Saldo nanti (sisa saldo dikurangi nominal), hijau jika >= 0 dan merah jika negatif

Preview dinonaktifkan selama budget belum dipilih atau nominal kosong / <= 0.

Budget boleh bernilai negatif.

### Dark Mode

Aplikasi mendukung mode terang dan gelap. Terdapat toggle di halaman form dan halaman Riwayat. Preferensi tersimpan di perangkat (localStorage) dan default adalah mode gelap.

### Halaman Riwayat

Diakses melalui link dari halaman utama.

Komponen:

* Dropdown bulan (periode, format `YYYY-MON-MON`)
* Search nama pengeluaran
* Filter budget
* Sort (waktu naik/turun, nominal besar/kecil)
* Tabel daftar expense aktif pada periode terpilih
* Tombol Edit (membuka modal berisi form yang sudah terisi)
* Tombol Hapus (dengan konfirmasi)

Search, filter, dan sort muncul setelah periode dipilih.

Preview saldo pada edit & hapus:

* Hapus: menampilkan saldo budget saat ini dan saldo nanti (bertambah sebesar nominal).
* Edit: menampilkan perubahan saldo budget (budget sama atau berganti), diperbarui otomatis saat nominal/budget diubah.

Baris tanpa ID (dibuat sebelum fitur ini) tidak dapat diedit atau dihapus.

Setelah edit atau hapus:

* daftar di-refresh pada periode yang sama
* saldo budget ikut ter-update

Waktu menggunakan format:

```text
yyyy-MM-dd HH:mm
```

Setelah berhasil menyimpan:

* tampilkan notifikasi sukses
* kosongkan seluruh form

Jika gagal:

* tampilkan notifikasi error

---

# Non Functional Requirements

Response API kurang dari 2 detik pada koneksi normal.

Kode backend harus dipisahkan menjadi:

* handler
* service
* google sheet client

Tidak boleh ada business logic di handler.

---

# Docker

Project harus dapat dijalankan menggunakan Docker Compose.

Service:

* frontend
* backend

Command:

```bash
docker compose up --build
```

Frontend:

http://localhost:3000

Backend:

http://localhost:8080

---

# Environment Variables

Backend:

```text
DB_URL=jdbc:mysql://host.docker.internal:33060/expense_tracker?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=expense_tracker_user
DB_PASSWORD=password-kuat
PORT=8080
```

Frontend:

```text
VITE_API_URL=http://localhost:8080
```

---

# Project Structure

```
expense-tracker/

frontend/

backend/

docker-compose.yml

README.md
```

---

# Acceptance Criteria

* User dapat membuka aplikasi dari browser.
* User dapat mengisi form pengeluaran.
* User dapat memilih budget dari dropdown.
* User dapat menekan tombol Save.
* Data berhasil tersimpan ke database MySQL.
* Jika validasi gagal, tampilkan pesan error.
* User dapat melihat daftar pengeluaran per periode.
* User dapat mengedit pengeluaran.
* User dapat menghapus pengeluaran (soft delete).
* Saldo budget tetap konsisten setelah edit/hapus.
* Aplikasi dapat dijalankan hanya dengan Docker Compose.
* Menggunakan database (MySQL).

---

# Out of Scope

Versi pertama belum mendukung:

* Login
* Authentication
* Category
* Upload bukti pembayaran
* Grafik tren yang detail
* Multi user

---

# Future Improvements

* Kategori
* Bulanan
* Grafik tren yang lebih detail
* Export PDF
* Export Excel
* Authentication
* Multi-user
* PWA
