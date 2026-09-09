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

## Runtime Backend (PENTING: JVM default, Native opsional)

Produksi **memakai image JVM** (`expense-tracker-backend-jvm:latest`, dibuild dari `backend/Dockerfile`). Dua Dockerfile backend:

| File | Pemakaian |
|---|---|
| `backend/Dockerfile` | Image JVM — **default** produksi & dev (`expense-tracker-backend-jvm:latest`) |
| `backend/Dockerfile.native` | GraalVM Native — **opsional/legacy**, RAM build besar (~7GB) |

`docker-compose.yml` (dev) memakai `image: ${BACKEND_IMAGE:-expense-tracker-backend-jvm:latest}`; `docker-compose.prod.yml` memakai `image: expense-tracker-backend-jvm:latest`.

**Mengapa JVM default?** Kompilasi native perlu ~7GB RAM dan tidak aman dilakukan di VPS yang RAM-nya terbatas (~3.7GB, dipakai banyak service lain). Kompilasi JVM jauh lebih ringan dan sudah terbukti aman tanpa OOM — bahkan di STB ber-RAM 1.7GB (available ~400MB) sekalipun. Karena itu image backend JVM dibuild **langsung di VPS** (avail ~2.1GB, jauh lebih lega dari STB).

### Konsekuensi untuk perubahan kode

* Image **JVM** adalah default → tidak butuh config refleksi/native.
* **Native (opsional)** memakai analisis statis. Bila kembali memakai native, semua refleksi/resource runtime harus terdaftar di `backend/native-config/reachability-metadata.json`; model JSON (request/response) harus via `@RegisterReflectionForBinding` di `ExpenseTrackerApplication`; dan regenerasi native config via `./backend/generate-native-config.sh`.
* **Flyway**: pada image native, migration tidak bisa dipindai dari `classpath:` (dibaca dari `filesystem:/app/db/migration`). Pada image JVM normal tidak ada kendala ini — jangan mengubah lokasi copy migration kecuali perlu.

---

## Alur Build & Deploy

Image backend (JVM) dibuild **langsung di VPS** (terbukti aman tanpa OOM, lihat **Runtime Backend**):

```bash
./deploy-vps.sh   # git pull -> docker build backend (di VPS) -> compose up -d --build -> prune -> verifikasi
```

Skrip lain:

```bash
./deploy-local.sh        # build image JVM + jalankan stack lokal (dev/test)
./deploy-stb.sh          # deploy notifier ke STB (Armbian via WireGuard)
./deploy-native.sh       # OPSIONAL: transfer image native ke VPS (hanya bila memakai native)
./build-native.sh        # OPSIONAL: build image native lokal
```

Semua deploy ke VPS berasumsi SSH key `root@frmnjn.my.id` tanpa password sudah terdaftar.

Backup MySQL otomatis (cron di VPS) dan manual via `scripts/backup_mysql.sh` / `scripts/restore_mysql.sh`.

---

## Git

Buat perubahan sekecil mungkin.

Jangan mengubah file yang tidak berhubungan.

Jangan melakukan refactor besar ketika sedang mengerjakan fitur kecil.

> **Jangan commit/push tanpa perintah eksplisit dari user.**

## Toolchain Lokal (STB Armbian — mesin dev ini)

> **Khusus mesin dev ini (STB Armbian).** Path toolchain di bawah hanya berlaku
> di device ini. Di mesin lain (mis. VPS, PC) lokasinya bisa berbeda — cek
> dahulu apakah Maven/JDK/npm tersedia sebelum memakai path `/data/dev`.

Untuk menjalankan build/test di mesin ini (tanpa Docker), gunakan toolchain yang
tersimpan di `/data/dev`. Ekspor sebelum menjalankan Maven/Java (shell tool yang
non-interaktif tidak memuat `.bashrc`):

```bash
export JAVA_HOME=/data/dev/jdk
export PATH="/data/dev/apache-maven-3.9.11/bin:$JAVA_HOME/bin:$PATH"
```

* JDK: `/data/dev/jdk` (Temurin 25 — default image backend JVM).
* Maven: `/data/dev/apache-maven-3.9.11`.
* Node/npm: via nvm di `/data/dev/nvm` (sudah terpasang).

Contoh menjalankan test backend dari repo ini:

```bash
cd backend && mvn -q -Dtest=InvoiceServiceTest test
```

Build/test backend lokal memakai Maven JDK dari `/data/dev`. Frontend memakai
`npm`/`node` dari nvm (lihat `frontend/package.json`).
