# AGENTS.md

## AI Agent Instructions

Dokumen ini berisi aturan implementasi yang harus dipatuhi oleh seluruh AI Coding Agent yang bekerja pada project ini.

Selalu baca file ini sebelum melakukan perubahan kode.

---

## Primary Goal

Implementasikan fitur sesuai `PRD.md`.

Jangan menambahkan fitur yang tidak diminta.

Jika ada requirement yang ambigu, pilih implementasi paling sederhana.

---

## General Principles

* Keep It Simple.
* Jangan overengineering.
* Hindari dependency yang tidak diperlukan.
* Hindari abstraction yang belum dibutuhkan.
* Jangan membuat generic framework untuk kemungkinan kebutuhan masa depan.
* Tulis kode yang mudah dibaca daripada kode yang terlalu pintar.

---

## Architecture

Project terdiri dari tiga komponen:

```
frontend/
backend/
notifier/   # microservice Go (kirim email), berjalan di STB Armbian
```

Frontend dan backend harus independen.

Komunikasi frontend ↔ backend dilakukan menggunakan REST API.

Backend menyimpan data di database MySQL.

Notifier dipanggil backend via HTTP untuk mengirim email (SMTP/Resend); di produksi berjalan di STB (dijangkau VPS lewat WireGuard).

### Trace ID end-to-end

Setiap request membawa trace id (header `X-Trace-Id`) dari frontend → backend → notifier:

* **Frontend**: mengirim `X-Trace-Id` (UUID baru) pada setiap request (axios interceptor).
* **Backend**: `TraceIdFilter` membaca `X-Trace-Id` (atau generate), menyimpan ke MDC `trace.id` (muncul otomatis di log ECS), dan mengembalikan header yang sama.
* **Backend → notifier**: `NotificationService` meneruskan `X-Trace-Id` dari MDC saat memanggil notifier.
* **Notifier**: membaca `X-Trace-Id`, menulis `trace.id` di log ECS, dan mengembalikan header.

Jangan menambahkan mekanisme trace lain yang menumpuk; gunakan header `X-Trace-Id` yang sudah ada.

---

## Frontend Rules

Framework

* React 19
* Vite
* TypeScript
* React Router

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

## Backend Rules

Language

Java 25

Framework

Spring Boot 4

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

data/

model/

config/

src/main/resources/db/migration/   (Flyway migration)

src/test/java/
```

Rules

* Controller hanya menerima HTTP Request.
* Business logic berada di Service.
* Akses database melalui layer `data/` (JdbcTemplate repository).
* Jangan letakkan business logic di main application class.
* Skema dikelola Flyway (tambah migration baru `V<n>__desc.sql` bila mengubah skema; jangan ubah migration lama).

---

## Error Handling

Semua error harus dikembalikan dalam format:

```json
{
    "success": false,
    "message": "..."
}
```

Jangan mengembalikan stack trace kepada client.

---

## Logging

Gunakan structured logging.

Log:

* request masuk
* response error
* error database

Jangan log credential.

Setiap request memakai trace id dari MDC (`trace.id`, header `X-Trace-Id`) agar log satu request bisa ditelusuri end-to-end (lihat **Trace ID end-to-end**).

---

## Configuration

Semua konfigurasi harus berasal dari Environment Variable.

Jangan hardcode:

* DB URL / user / password
* Port
* UPLOAD_DIR
* Kredensial apa pun

---

## Docker

Seluruh aplikasi harus dapat dijalankan menggunakan:

```bash
docker compose up --build
```

Foto invoice tersimpan di direktori `./uploads` (bind mount) — jangan ubah menjadi named volume.

---

## Dependency Rules

Gunakan library seminimal mungkin.

Sebelum menambahkan dependency baru, pastikan benar-benar diperlukan.

Lebih baik menggunakan library resmi dibanding library pihak ketiga.

---

## Code Style

Prioritas:

1. Readability
2. Simplicity
3. Maintainability
4. Performance

Jangan membuat kode menjadi kompleks demi optimasi yang belum dibutuhkan.

---

## Naming

Gunakan nama yang jelas.

Contoh:

ExpenseService

ExpenseController

ExpenseRepository

ExpenseRequest

ExpenseResponse

Hindari nama seperti:

DataManager

Helper

Utils

Common

Misc

---

## Comments

Jangan menambahkan komentar yang menjelaskan hal yang sudah jelas.

Komentar hanya digunakan untuk:

* menjelaskan business rule
* workaround
* alasan pengambilan keputusan

---

## Testing

Untuk setiap business logic baru:

* buat Unit Test

Untuk endpoint baru:

* buat Integration Test jika diperlukan.

---

## UI Guidelines

Gunakan tampilan sederhana.

Prioritas:

* mudah digunakan
* responsive
* loading state
* error state
* success notification

Tidak perlu animasi.

### Responsive Mobile

* Di layar kecil, **ganti layout** daripada memaksa kolom dikecilkan.
* Contoh: untuk daftar baris padat (tabel), gunakan `useMediaQuery('(max-width: 48em)')` dan render **list kartu** (`Stack` + `Paper`) di mobile, bukan tabel yang kolomnya diperkecil/di-ellipsis.
* Pastikan tombol aksi (edit/hapus) selalu terlihat di layar HP, tidak pernah off-screen / butuh scroll horizontal.

---

## Future Features

Jika menemukan kebutuhan fitur baru selama implementasi:

Jangan langsung mengimplementasikan.

Tambahkan sebagai TODO atau usulkan pada PRD.

---

## Definition of Done

Sebuah task dianggap selesai jika:

* Build berhasil.
* Docker berhasil dijalankan.
* Tidak ada compile error.
* Tidak ada lint error.
* Requirement pada PRD terpenuhi.
* Kode mengikuti struktur project.
* Tidak menambahkan fitur di luar scope.

---

## Runtime Backend (PENTING: JVM vs Native)

Produksi memakai **GraalVM Native Image**, bukan JVM. Dua Dockerfile backend:

| File | Pemakaian |
|---|---|
| `backend/Dockerfile` | Image JVM (fallback, pengembangan) |
| `backend/Dockerfile.native` | Image native (produksi, `expense-tracker-backend-native:latest`) |

Kedua `docker-compose.yml` & `docker-compose.prod.yml` memakai `image: expense-tracker-backend-native:latest`.

Untuk memakai **JVM** (mis. di VPS), ada override `docker-compose.jvm.yml` (backend memakai `expense-tracker-backend-jvm:latest`) dan `docker-compose.local.yml` (build backend dari `backend/Dockerfile`).

### Konsekuensi untuk perubahan kode

* **Native memakai analisis statis.** Semua refleksi/resource yang dipakai runtime harus terdaftar di `backend/native-config/reachability-metadata.json`.
* Model yang di-bind JSON (request/response) harus didaftarkan via `@RegisterReflectionForBinding` di `ExpenseTrackerApplication`.
* **Flyway** tidak bisa memindai `classpath:` di native — migration dibaca dari `filesystem:/app/db/migration` (file di-copy ke image). Jangan mengubah lokasi ini kecuali perlu.
* Jika menambah **endpoint/kelas yang memakai refleksi** atau **model baru**, regenerasi native config:
  ```bash
  ./backend/generate-native-config.sh   # regenerasi reachability-metadata terhadap MySQL lokal
  ./build-native.sh                     # rebuild image native
  ```

---

## Alur Build & Deploy Native

Build & deploy **hanya dari PC lokal** (bukan di VPS, karena butuh RAM ~7GB).

```bash
./build-native.sh     # build image native lokal (docker build Dockerfile.native)
./deploy-native.sh    # export -> scp -> VPS git pull -> docker load -> up -d -> prune dangling images
```

`deploy-native.sh` berasumsi SSH key `root@expense.frmnjn.my.id` tanpa password sudah terdaftar.

Backup MySQL otomatis (cron di VPS) dan manual via `scripts/backup_mysql.sh` / `scripts/restore_mysql.sh`.

---

## Git

Buat perubahan sekecil mungkin.

Jangan mengubah file yang tidak berhubungan.

Jangan melakukan refactor besar ketika sedang mengerjakan fitur kecil.

> **Jangan commit/push tanpa perintah eksplisit dari user.**
