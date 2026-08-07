# AGENTS.md

# AI Agent Instructions

Dokumen ini berisi aturan implementasi yang harus dipatuhi oleh seluruh AI Coding Agent yang bekerja pada project ini.

Selalu baca file ini sebelum melakukan perubahan kode.

---

# Primary Goal

Implementasikan fitur sesuai `PRD.md`.

Jangan menambahkan fitur yang tidak diminta.

Jika ada requirement yang ambigu, pilih implementasi paling sederhana.

---

# General Principles

* Keep It Simple.
* Jangan overengineering.
* Hindari dependency yang tidak diperlukan.
* Hindari abstraction yang belum dibutuhkan.
* Jangan membuat generic framework untuk kemungkinan kebutuhan masa depan.
* Tulis kode yang mudah dibaca daripada kode yang terlalu pintar.

---

# Architecture

Project terdiri dari dua aplikasi.

```
frontend/
backend/
```

Frontend dan backend harus independen.

Komunikasi dilakukan menggunakan REST API.

Tidak boleh ada komunikasi langsung dari frontend ke Google Sheets.

---

# Frontend Rules

Framework

* React
* Vite
* TypeScript

UI

* Mantine UI

HTTP Client

* Axios

Data Fetching

* TanStack Query

Rules

* Gunakan Functional Component.
* Gunakan Hooks.
* Jangan gunakan Class Component.
* Jangan gunakan Redux.
* Local state menggunakan React Hooks.
* Pisahkan UI dan API Client.

Folder yang disarankan:

```
src/

components/

pages/

services/

hooks/

types/

utils/
```

---

# Backend Rules

Language

Java

Framework

Spring Boot

Build Tool

Maven

Project Structure

```
backend/

pom.xml

src/main/java/com/expensetracker/

ExpenseTrackerApplication.java

controller/

service/

google/

model/

config/

src/main/resources/

src/test/java/
```

Rules

* Controller hanya menerima HTTP Request.
* Business logic berada di Service.
* Google Sheets hanya boleh diakses melalui package google.
* Jangan akses Google Sheets langsung dari Controller.
* Jangan letakkan business logic di main application class.

Catatan: `backend-golang/` berisi implementasi backend lama menggunakan Go dan tidak digunakan.

---

# Error Handling

Semua error harus dikembalikan dalam format:

```json
{
    "success": false,
    "message": "..."
}
```

Jangan mengembalikan stack trace kepada client.

---

# Logging

Gunakan structured logging.

Log:

* request masuk
* response error
* error Google Sheets

Jangan log credential.

---

# Configuration

Semua konfigurasi harus berasal dari Environment Variable.

Jangan hardcode:

* Sheet ID
* API Key
* Credential Path
* Port

---

# Docker

Seluruh aplikasi harus dapat dijalankan menggunakan:

```bash
docker compose up --build
```

Tidak boleh ada langkah manual selain menyiapkan file credential Google.

---

# Dependency Rules

Gunakan library seminimal mungkin.

Sebelum menambahkan dependency baru, pastikan benar-benar diperlukan.

Lebih baik menggunakan library resmi dibanding library pihak ketiga.

---

# Code Style

Prioritas:

1. Readability
2. Simplicity
3. Maintainability
4. Performance

Jangan membuat kode menjadi kompleks demi optimasi yang belum dibutuhkan.

---

# Naming

Gunakan nama yang jelas.

Contoh:

ExpenseService

ExpenseController

GoogleSheetsClient

ExpenseRequest

ExpenseResponse

Hindari nama seperti:

DataManager

Helper

Utils

Common

Misc

---

# Comments

Jangan menambahkan komentar yang menjelaskan hal yang sudah jelas.

Komentar hanya digunakan untuk:

* menjelaskan business rule
* workaround
* alasan pengambilan keputusan

---

# Testing

Untuk setiap business logic baru:

* buat Unit Test

Untuk endpoint baru:

* buat Integration Test jika diperlukan.

---

# Git

Buat perubahan sekecil mungkin.

Jangan mengubah file yang tidak berhubungan.

Jangan melakukan refactor besar ketika sedang mengerjakan fitur kecil.

---

# UI Guidelines

Gunakan tampilan sederhana.

Prioritas:

* mudah digunakan
* responsive
* loading state
* error state
* success notification

Tidak perlu animasi.

---

# Future Features

Jika menemukan kebutuhan fitur baru selama implementasi:

Jangan langsung mengimplementasikan.

Tambahkan sebagai TODO atau usulkan pada PRD.

---

# Definition of Done

Sebuah task dianggap selesai jika:

* Build berhasil.
* Docker berhasil dijalankan.
* Tidak ada compile error.
* Tidak ada lint error.
* Requirement pada PRD terpenuhi.
* Kode mengikuti struktur project.
* Tidak menambahkan fitur di luar scope.

---

# Runtime Backend (PENTING: JVM vs Native)

Produksi memakai **GraalVM Native Image**, bukan JVM. Dua Dockerfile backend:

| File | Pemakaian |
|---|---|
| `backend/Dockerfile` | Image JVM (fallback, pengembangan) |
| `backend/Dockerfile.native` | Image native (produksi, `expense-tracker-backend-native:latest`) |

Kedua `docker-compose.yml` & `docker-compose.prod.yml` memakai `image: expense-tracker-backend-native:latest`.

## Konsekuensi untuk perubahan kode

* **Native memakai analisis statis.** Semua refleksi/resource yang dipakai runtime harus terdaftar di `backend/native-config/reachability-metadata.json`.
* Google Sheets client memakai refleksi berat (Gson + request `@Key`). `google-http-client` sudah bundling config, tapi **`google-api-services-sheets`/`google-api-client`/`google-oauth-client` tidak** → wajib lewat config tracing.
* Jika menambah **endpoint/kelas yang memakai refleksi** atau **upgrade library Google**, jalankan ulang:
  ```bash
  ./backend/generate-native-config.sh   # regenerasi reachability-metadata (ke test sheet, aman)
  ./build-native.sh                     # rebuild image native
  ```
* Verifikasi selalu dengan test sheet (`GOOGLE_TEST_SHEET_ID`), jangan menulis ke produksi.

---

# Alur Build & Deploy Native

Build & deploy **hanya dari PC lokal** (bukan di VPS, karena butuh RAM ~7GB).

```bash
./build-native.sh     # build image native lokal (docker build Dockerfile.native)
./deploy-native.sh    # export -> scp -> VPS git pull -> docker load -> up -d
```

`deploy-native.sh` berasumsi SSH key `root@expense.frmnjn.my.id` tanpa password sudah terdaftar.

---

# Git

Buat perubahan sekecil mungkin.

Jangan mengubah file yang tidak berhubungan.

Jangan melakukan refactor besar ketika sedang mengerjakan fitur kecil.

> **Jangan commit/push tanpa perintah eksplisit dari user.**
