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

Go

Framework

Gin

Project Structure

```
cmd/

internal/

handler/

service/

google/

model/

config/

routes/

pkg/
```

Rules

* Handler hanya menerima HTTP Request.
* Business logic berada di Service.
* Google Sheets hanya boleh diakses melalui package google.
* Jangan akses Google Sheets langsung dari Handler.
* Jangan letakkan business logic di main.go.

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

ExpenseHandler

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
