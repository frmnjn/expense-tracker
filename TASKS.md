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

* [x] Inisialisasi Go Module
* [x] Install Gin
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

* [ ] Setup Google Service Account
* [ ] Load credentials dari environment
* [ ] Buat Google Sheets client
* [ ] Test koneksi ke Google Sheets

---

## Repository Layer

* [ ] Implement append row
* [ ] Handle error Google API

---

# Phase 3 - Expense API

## Model

* [ ] ExpenseRequest
* [ ] ExpenseResponse

---

## Validation

* [ ] Date wajib diisi
* [ ] Description wajib diisi
* [ ] Description maksimal 255 karakter
* [ ] Amount wajib diisi
* [ ] Amount > 0

---

## Service

* [ ] Validasi request
* [ ] Mapping request
* [ ] Simpan ke Google Sheets

---

## Handler

* [ ] POST /expenses

---

## Response

* [ ] Success response
* [ ] Error response

---

# Phase 4 - Frontend

## Layout

* [ ] Halaman utama
* [ ] Responsive layout

---

## Form

* [ ] Date Picker
* [ ] Description Input
* [ ] Amount Input
* [ ] Save Button

---

## API

* [ ] Axios client
* [ ] POST /expenses

---

## UX

* [ ] Disable tombol saat submit
* [ ] Loading indicator
* [ ] Success notification
* [ ] Error notification
* [ ] Reset form setelah berhasil

---

# Phase 5 - Testing

## Backend

* [ ] Unit Test Service
* [ ] Unit Test Validation

---

## Manual Testing

* [ ] Submit data valid
* [ ] Description kosong
* [ ] Amount = 0
* [ ] Amount negatif
* [ ] Google Sheets berhasil menerima data

---

# Phase 6 - Docker Verification

* [ ] docker compose up --build berhasil
* [ ] Frontend dapat diakses
* [ ] Backend dapat diakses
* [ ] Frontend dapat memanggil Backend
* [ ] Backend dapat menulis ke Google Sheets

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
