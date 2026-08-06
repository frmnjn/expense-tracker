# TASKS.md

# Expense Tracker v1

Checklist implementasi berdasarkan PRD.

---

# Phase 1 - Project Setup

## Repository

* [x] Inisialisasi project
* [x] Buat struktur folder
* [x] Tambahkan README.md
* [x] Tambahkan .gitignore

---

## Frontend

* [x] Inisialisasi React + Vite + TypeScript
* [x] Install Mantine UI
* [x] Install Axios
* [x] Install TanStack Query
* [x] Setup React Query Provider
* [x] Setup folder structure
* [x] Setup environment variables

---

## Backend

* [x] Inisialisasi project Java Spring Boot (Maven)
* [x] Setup Spring Boot Web
* [x] Setup project structure
* [x] Setup routing
* [x] Setup configuration loader
* [x] Setup environment variables

---

## Docker

* [x] Dockerfile Frontend
* [x] Dockerfile Backend
* [x] docker-compose.yml
* [x] Verifikasi seluruh service berjalan menggunakan Docker Compose

---

# Phase 2 - Google Sheets Integration

## Authentication

* [x] Setup Google Service Account
* [x] Load credentials dari environment
* [x] Buat Google Sheets client
* [x] Test koneksi ke Google Sheets

---

## Repository Layer

* [x] Implement append row
* [x] Handle error Google API

---

# Phase 3 - Expense API

## Model

* [x] ExpenseRequest (dateTime, name, budget, bank, amount, description)

---

## Validation

* [x] Waktu wajib diisi
* [x] Waktu format yyyy-MM-dd HH:mm
* [x] Name wajib diisi
* [x] Name maksimal 255 karakter
* [x] Budget wajib diisi
* [x] Bank wajib diisi
* [x] Amount wajib diisi
* [x] Amount > 0
* [x] Description opsional, maksimal 255 karakter

---

## Service

* [x] Validasi request
* [x] Hitung nama sheet berdasarkan periode (cut off tanggal 25)
* [x] Auto-reorder sheet (periode terbaru di kiri, Budget/Bank paling kanan)
* [x] Mapping request
* [x] Simpan ke sheet periode di Google Sheets

---

## Controller

* [x] POST /expenses
* [x] GET /options

---

## Response

* [x] Success response
* [x] Error response

---

# Phase 4 - Frontend

## Layout

* [x] Halaman utama
* [x] Responsive layout

---

## Form

* [x] Input Waktu (otomatis / manual)
* [x] Name Input
* [x] Budget Dropdown
* [x] Bank Dropdown
* [x] Nominal Input
* [x] Description Input (opsional)
* [x] Save Button

---

## API

* [x] Axios client
* [x] GET /options
* [x] POST /expenses

---

## UX

* [x] Disable tombol saat submit
* [x] Loading indicator
* [x] Success notification
* [x] Error notification
* [x] Reset form setelah berhasil

---

# Phase 5 - Testing

## Backend

* [x] Unit Test Service (validasi)
* [x] Unit Test penamaan sheet per periode
* [x] Unit Test parse tanggal awal periode
* [x] Integration Test reorder sheet

---

## Manual Testing

* [x] Submit data valid
* [x] Waktu kosong
* [x] Budget kosong
* [x] Bank kosong
* [x] Amount = 0
* [x] Amount negatif
* [x] Data masuk ke sheet periode yang benar
* [x] Google Sheets berhasil menerima data

---

# Phase 6 - Docker Verification

* [x] docker compose up --build berhasil
* [x] Frontend dapat diakses
* [x] Backend dapat diakses
* [x] Frontend dapat memanggil Backend
* [x] Backend dapat menulis ke Google Sheets

---

# Definition of Done

Project dianggap selesai apabila:

* [ ] Seluruh checklist selesai
* [ ] Build berhasil
* [ ] Docker Compose berjalan tanpa error
* [ ] Frontend dapat digunakan
* [ ] Backend berjalan normal
* [ ] Data berhasil tersimpan ke Google Sheets
* [ ] Tidak ada fitur di luar PRD
