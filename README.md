# Expense Tracker

Aplikasi web sederhana untuk mencatat pengeluaran harian.

Data tidak disimpan di database, melainkan langsung di Google Sheets menggunakan Google Sheets API.

---

# Features

* Menambah pengeluaran
* Validasi input
* Menyimpan data ke Google Sheets
* REST API menggunakan Go
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

* Go
* Gin

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
└── backend/
```

---

# Prerequisites

Pastikan sudah menginstall:

* Docker
* Docker Compose

Tidak diperlukan instalasi Node.js maupun Go apabila menjalankan project menggunakan Docker.

---

# Google Sheets Setup

1. Buat Google Spreadsheet.

2. Buat sebuah sheet bernama:

```text
Expenses
```

3. Buat header:

| Date | Description | Amount |
| ---- | ----------- | ------ |

4. Buat Google Cloud Service Account.

5. Aktifkan Google Sheets API.

6. Download credential JSON.

7. Share spreadsheet ke email Service Account dengan hak akses **Editor**.

8. Simpan file credential sebagai:

```text
backend/credentials.json
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
GOOGLE_APPLICATION_CREDENTIALS=/app/credentials.json
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
  "date": "2026-08-06",
  "description": "Makan Siang",
  "amount": 35000
}
```

Response

```json
{
  "success": true
}
```

---

# Validation Rules

Description

* Required
* Maksimal 255 karakter

Amount

* Required
* Harus lebih besar dari 0

Date

* Required

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
