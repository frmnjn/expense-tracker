# Expense Tracker

Aplikasi web sederhana untuk mencatat pengeluaran harian.

Data tidak disimpan di database, melainkan langsung di Google Sheets menggunakan Google Sheets API.

---

# Features

* Menambah pengeluaran
* Input waktu otomatis atau manual
* Dropdown budget & bank dari Google Sheets
* Sheet pengeluaran terpisah per periode
* Urutan sheet otomatis (periode terbaru di kiri)
* Validasi input
* Menyimpan data ke Google Sheets
* REST API menggunakan Java Spring Boot
* React Frontend
* Docker Compose
* Tanpa database

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
* Spring Boot
* Maven

## Storage

* Google Sheets API

## Infrastructure

* Docker
* Docker Compose

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
├── frontend/
│
├── backend/
│
└── backend-golang/  (implementasi lama, tidak digunakan)
```

---

# Prerequisites

Pastikan sudah menginstall:

* Docker
* Docker Compose

Tidak diperlukan instalasi Node.js, Java, maupun Maven apabila menjalankan project menggunakan Docker.

---

# Google Sheets Setup

1. Buat Google Spreadsheet.

2. Buat dua sheet untuk data dropdown dengan header di baris 1:

   * Sheet `Budget`, kolom A berisi daftar nama budget.
   * Sheet `Bank`, kolom A berisi daftar nama bank.

3. Buat Google Cloud Service Account.

4. Aktifkan Google Sheets API.

5. Download credential JSON.

6. Share spreadsheet ke email Service Account dengan hak akses **Editor**.

7. Simpan file credential sebagai:

```text
backend/credentials.json
```

Sheet pengeluaran per periode (format nama `YYYY-MON-MON`, contoh `2026-JAN-FEB`) dibuat otomatis oleh backend beserta header.

Urutan sheet otomatis diatur oleh backend: periode terbaru paling kiri, lalu periode lama ke kanan, dan tab `Budget`/`Bank` selalu paling kanan. Reorder dilakukan saat backend start dan saat sheet periode baru dibuat.

## Testing Spreadsheet (opsional)

Untuk mencegah test menulis ke spreadsheet produksi, buat spreadsheet terpisah untuk testing:

1. Buat Google Spreadsheet terpisah.
2. Buat tab `Budget` dan `Bank` (kolom A, header baris 1) — agar test `getOptions` berjalan.
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
GOOGLE_BANK_SHEET=Bank
```

---

## Frontend

Buat file:

```text
frontend/.env
```

Contoh:

```env
VITE_API_URL=http://localhost:8080
```

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

---

# API

## POST /expenses

Request

```json
{
  "dateTime": "2026-08-06 14:30",
  "name": "Makan Siang",
  "budget": "Daily",
  "bank": "BCA",
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
    "budgets": ["Daily", "Weekly"],
    "banks": ["BCA", "Mandiri"]
  }
}
```

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

Bank

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

# Future Roadmap

Versi berikutnya dapat menambahkan:

* Daftar pengeluaran
* Edit pengeluaran
* Hapus pengeluaran
* Kategori
* Filter
* Dashboard
* Grafik
* Multi-user
* Authentication

---

# License

Project ini dibuat untuk pembelajaran dan penggunaan pribadi.
