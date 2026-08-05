# PRD.md

# Expense Tracker v1

## Overview

Expense Tracker adalah aplikasi web sederhana untuk mencatat pengeluaran harian.

Aplikasi tidak menggunakan database. Semua data akan langsung disimpan ke Google Sheets menggunakan Google Sheets API.

Target utama aplikasi adalah sederhana, mudah dijalankan secara lokal menggunakan Docker, dan mudah dikembangkan di masa depan.

---

# Objectives

Membangun aplikasi yang memungkinkan pengguna:

* Menambahkan data pengeluaran melalui web.
* Menyimpan data ke Google Sheets.
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

* Go
* Gin Framework

## Storage

Google Sheets

Tidak menggunakan database lokal.

## Container

Docker

Docker Compose

---

# Functional Requirements

## Create Expense

User dapat menambahkan satu pengeluaran.

Field yang harus diisi:

| Field       | Type   | Required |
| ----------- | ------ | -------- |
| Date        | Date   | Yes      |
| Description | String | Yes      |
| Amount      | Number | Yes      |

Contoh:

Date

2026-08-06

Description

Makan siang

Amount

35000

---

## Validation

Description

* wajib diisi
* maksimal 255 karakter

Amount

* wajib diisi
* harus lebih besar dari 0

Date

* wajib diisi

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

# Google Sheets Format

Spreadsheet memiliki satu sheet bernama:

Expenses

Kolom:

| Date | Description | Amount |
| ---- | ----------- | ------ |

Contoh:

| Date       | Description | Amount |
| ---------- | ----------- | ------ |
| 2026-08-06 | Makan Siang | 35000  |
| 2026-08-06 | Parkir      | 5000   |

Backend akan selalu melakukan append row.

Tidak boleh menghapus data yang sudah ada.

---

# API

## POST /expenses

Request

```json
{
    "date":"2026-08-06",
    "description":"Makan Siang",
    "amount":35000
}
```

Response

```json
{
    "success":true
}
```

---

# Error Response

```json
{
    "success":false,
    "message":"Description is required"
}
```

---

# UI

Halaman hanya terdiri dari satu halaman.

Komponen:

* Date Picker
* Description Text Field
* Amount Number Field
* Save Button

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
GOOGLE_APPLICATION_CREDENTIALS=/app/credentials.json
GOOGLE_SHEET_ID=xxxxxxxxxxxxxxxx
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
* User dapat menekan tombol Save.
* Data berhasil tersimpan ke Google Sheets.
* Jika validasi gagal, tampilkan pesan error.
* Aplikasi dapat dijalankan hanya dengan Docker Compose.
* Tidak menggunakan database.

---

# Out of Scope

Versi pertama belum mendukung:

* Login
* Authentication
* Edit data
* Delete data
* List pengeluaran
* Filter
* Search
* Category
* Upload bukti pembayaran
* Dashboard
* Grafik
* Multi user

---

# Future Improvements

* Daftar pengeluaran
* Edit pengeluaran
* Hapus pengeluaran
* Kategori
* Bulanan
* Dashboard
* Export PDF
* Export Excel
* Authentication
* Multi-user
* Dark Mode
* PWA
