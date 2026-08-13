# PRD.md

# Expense Tracker

## Overview

Aplikasi web untuk mencatat pengeluaran harian, memantau saldo per budget, dan mengelola riwayat.

Data disimpan di database **MySQL** (berbagi instance dengan WordPress di VPS, memakai database `expense_tracker` terpisah).

Target: sederhana, mudah dijalankan dengan Docker, mudah dikembangkan.

---

# Objectives

* Mencatat pengeluaran.
* Memantau saldo tiap budget (ditambah via top-up, berkurang saat pengeluaran).
* Mengelola budget (tambah, edit, hapus).
* Melihat daftar pengeluaran per periode beserta foto invoice.
* Menjalankan seluruh aplikasi dengan Docker Compose.

---

# Tech Stack

## Frontend

* React 19 + Vite + TypeScript
* React Router
* TanStack Query
* Axios
* Mantine UI

## Backend

* Java 25 + Spring Boot 4
* Spring JDBC (JdbcTemplate)
* Flyway (DB migration)
* GraalVM 25 Native Image (produksi)

## Storage

* MySQL 8+

## Infrastructure

* Docker Compose
* nginx (reverse proxy + SSL)

---

# Halaman

* `/` — halaman Catat (form pengeluaran + foto invoice).
* `/scan` — **Scan Struk dengan AI** (upload foto/PDF, analisis otomatis, review & assign budget, auto-create banyak expense).
* `/dashboard` — ringkasan saldo per budget, top-up, riwayat top-up, dan 3 bulan terakhir.
* `/riwayat` — daftar pengeluaran per periode, dengan filter/sort, pagination, edit, hapus, dan lihat foto (termasuk PDF).
* `/catat` — alias halaman Catat.

Saat aplikasi dibuka, user langsung melihat halaman Catat.

---

# Database (MySQL)

Database `expense_tracker` pada instance MySQL yang berbagi dengan WordPress. Skema dikelola oleh **Flyway** (migration versioned di `backend/src/main/resources/db/migration`).

Tabel:

| Tabel | Kolom |
| ----- | ----- |
| `budgets` | `id` PK, `name` UNIQUE, `balance`, `is_active`, `alert_threshold` (0 = nonaktif) |
| `expenses` | `id` PK, `period`, `period_start`, `date_time`, `budget_id` FK→`budgets`, `name`, `amount`, `description` (TEXT), `deleted`, `invoice_id` FK→`invoices` (nullable), `created_at` (DATETIME(6), untuk pengurutan deterministik) |
| `invoices` | `id` PK, `period`, `period_start`, `photo_path`, `deleted`, `status` (`ANALYZING`/`TO_REVIEW`/`SUBMITTED`/`ERROR`), `analysis_json`, `error_message`, `scan_flow` |
| `idempotency_keys` | `id_key` PK, `response_json`, `created_at` |
| `top_ups` | `id` PK, `date_time`, `budget_id` FK→`budgets`, `amount`, `description` (TEXT) |

Migration saat ini:

* `V1__init.sql` — skema awal.
* `V2__budget_is_active.sql` — kolom `is_active` pada budgets (idempotent).
* `V3__seed_default_budgets.sql` — seed budget default (`INSERT IGNORE`).
* `V4__expense_photo.sql` — kolom `photo_path` pada expenses (sudah digantikan `V5`).
* `V5__invoices.sql` — tabel `invoices`, kolom `expenses.invoice_id`; memindahkan foto lama dari `expenses.photo_path` ke `invoices.photo_path` lalu menghapus `photo_path`.
* `V6__idempotency.sql` — tabel `idempotency_keys` untuk idempotensi request POST.
* `V7__indexes.sql` — index `expenses.invoice_id`, komposit `expenses(period, deleted)` (menggantikan `idx_expenses_period`), dan `idempotency_keys.created_at`.
* `V8__budget_alert_threshold.sql` — kolom `budgets.alert_threshold` (0 = nonaktif) untuk notifikasi "budget menipis".
* `V9__invoices_created_at.sql` — kolom `invoices.created_at` (untuk urutan "foto periode ini").
* `V10__invoice_ai.sql` — kolom `invoices.status`, `analysis_json`, `error_message` (state machine analisis AI).
* `V11__invoice_scan_flow.sql` — kolom `invoices.scan_flow` (menandai invoice hasil alur `/scan`).
* `V12__expenses_created_at.sql` — kolom `expenses.created_at` (pengurutan deterministik).
* `V13__expenses_created_at_fractional.sql` — `created_at` jadi `DATETIME(6)` (mikrodetik, hindari tie dalam satu batch scan).
* `V14__wider_description.sql` — `description` expense & top_up jadi `TEXT` (deskripsi auto-generate scan bisa > 255).

Index yang ada: `budgets` PK(id) + UNIQUE(name); `expenses` PK(id), `budget_id` FK, `deleted`, `invoice_id`, komposit `(period, deleted)`; `invoices` PK(id) + `period`; `top_ups` PK(id) + `budget_id`; `idempotency_keys` PK(id_key) + `created_at`.

Satu **invoice** adalah pemilik satu file foto/PDF dan dapat dirujuk oleh **banyak** expense (`invoice_id`). Karena itu satu foto invoice bisa dipakai untuk beberapa catatan pengeluaran (mis. satu struk untuk beberapa budget). `hasPhoto` pada respons expense berarti `invoice_id` terisi; `photoType` (`image`/`pdf`) diturunkan dari jenis file. Menghapus foto pada sebuah expense melepas referensi (`invoice_id = NULL`); invoice & file foto **baru dihapus jika tidak ada expense lain yang masih memakainya** — jika masih dipakai expense lain, invoice & file dipertahankan.

Invoice hasil alur `/scan` ditandai `scan_flow = TRUE` dan memiliki state machine analisis AI: `ANALYZING` (upload sukses, AI berjalan async) → `TO_REVIEW` (AI selesai, menunggu konfirmasi user) → `SUBMITTED` (expense dibuat) atau `ERROR` (analisis gagal, bisa retry). Foto biasa pada form Catat berstatus langsung `SUBMITTED` (bukan alur AI).

Periode (format `YYYY-MON-MON`, contoh `2026-JAN-FEB`) dihitung dari `date_time` dengan aturan cut-off tanggal 25. `period` disimpan untuk filter cepat.

Penghapusan expense dilakukan dengan **soft delete** (`deleted = TRUE`); baris tetap ada di DB dan disaring saat ditampilkan.

`budgets.balance` adalah saldo running dan boleh bernilai negatif.

---

# Fitur

## Catat Pengeluaran

Field:

| Field | Tipe | Required |
| ----- | ---- | -------- |
| Waktu | Datetime (`yyyy-MM-dd HH:mm`) | Ya |
| Name | String (maks 255) | Ya |
| Budget | Dropdown | Ya |
| Nominal | Number (> 0) | Ya |
| Description | String (maks 255) | Tidak |
| Foto invoice | Gambar (opsional) | Tidak |

* Waktu bisa otomatis (waktu sistem) atau manual.
* Budget diambil dari dropdown (budget aktif).
* Saat nominal diisi, muncul preview "Saldo nanti" (sisa saldo dikurangi nominal; hijau jika >= 0, merah jika negatif).
* Foto: tombol membuka pilihan **"Ambil Foto (Kamera)"**, **"Dari Galeri"**, atau **"Pakai Foto Periode Ini"** (memilih foto invoice yang sudah di-upload di periode yang sama, agar satu struk bisa dipakai beberapa catatan), dengan preview.
* Submit: create expense → jika ada foto, upload foto (atau pakai `invoiceId` saat memilih foto yang sudah ada) → reset form + notifikasi sukses.
* Idempotensi: setiap POST mengirim header `Idempotency-Key` (UUID). Backend menyimpan hasilnya selama 24 jam; request dengan key yang sama mengembalikan respons tersimpan tanpa membuat duplikat. Guard `submittingRef` di frontend juga mencegah double-submit.

## Scan Struk dengan AI (`/scan`)

Alur untuk belanja supermarket yang memakai banyak budget dalam satu struk, tanpa harus menghitung manual. AI (Google Gemini Flash) membaca struk gambar/PDF, lalu user tinggal review & assign budget sebelum expense dibuat otomatis.

Alur:

1. **Upload** struk (foto kamera/galeri, atau PDF) → invoice dibuat dengan status `ANALYZING`, AI menganalisis **async** di background.
2. **Menunggu AI** → status berubah otomatis (polling ~3 detik) menjadi `TO_REVIEW` saat selesai, atau `ERROR` (mis. bukan struk) dengan tombol "Coba lagi".
3. **Review** (modal): daftar item hasil AI (nama, nominal, **saran budget** dari daftar budget aktif). User mengoreksi: ganti nama/nominal, ganti/pilih budget tiap item, tambah/hapus item. Item diskon/promo dibaca sebagai nominal negatif (mengurangi total group).
4. **Group per budget** otomatis: item di-budget yang sama dijumlah → 1 expense per budget; nama default "Belanja {toko}", deskripsi berisi daftar item. Tampil warning bila jumlah item ≠ total struk.
5. **Buat N Pengeluaran** → `POST /expenses/batch` (satu transaksi) → semua expense dibuat, saldo tiap budget di-adjust, invoice ditandai `SUBMITTED`.

Detail teknis:

* List di `/scan` hanya menampilkan invoice `scan_flow = TRUE`, dengan **filter status** (Semua/Menunggu AI/Perlu Review/Selesai/Gagal), **sort** (terbaru/terlama), dan **pagination**.
* Satu struk dipakai sebagai `invoice_id` pada **semua** expense hasil split (satu foto untuk banyak catatan).
* Deskripsi auto-generate bisa melebihi 255 karakter; di-truncate di frontend, dan DB `description` = `TEXT`.
* Pengurutan riwayat pakai `date_time DESC, created_at ASC` (created_at mikrodetik) agar urutan batch scan stabil & urut sesuai urutan item.
* Konfigurasi AI: `GEMINI_API_KEY`, `AI_MODEL` (default `gemini-3.5-flash-lite`), `AI_TIMEOUT`.

## Dashboard

* Daftar saldo per budget — tiap kartu menampilkan:
  * nama budget, badge urutan (1–3) jika masuk pengeluaran terbesar periode
  * saldo saat ini (merah jika negatif)
  * ikon `+` untuk top-up (modal nominal + deskripsi, tampil saldo saat ini)
  * ikon `📋` untuk riwayat top-up budget tersebut
  * ikon `✎` untuk edit budget (nama & saldo)
  * ikon `🗑` untuk hapus budget (konfirmasi)
  * saat periode dipilih: jumlah transaksi + pengeluaran periode budget tersebut
* Tombol "+ Budget" (modal: nama + saldo awal opsional).
* Ringkasan "3 Bulan Terakhir" (total + transaksi per periode, bar visual).

## Riwayat

* Dropdown periode.
* Search nama, filter budget, dan sort (waktu/nominal) — muncul setelah periode dipilih.
* Tabel (desktop) / kartu (mobile) daftar expense aktif.
* Tombol lihat foto (📷) jika expense memiliki foto.
* Tombol edit (modal) dan hapus (konfirmasi, soft delete).
* Edit bisa **ganti foto** (upload baru / pakai foto periode ini) atau **hapus foto**.
* Preview perubahan saldo saat edit/hapus.

## Budget

* Tambah (nama + saldo awal opsional + ambang notifikasi opsional).
* Edit (nama + saldo + ambang notifikasi).
* Kartu budget di Dashboard menampilkan indikator `⚠️ Ambang Rp…` jika `alert_threshold > 0`.
* Hapus (soft delete): nama diubah jadi `DELETED_<nama>_<id>` sehingga nama asli bisa dipakai ulang; expense di bawah budget ikut soft-delete.

## Top-up

* Tambah saldo budget (nominal + deskripsi), tercatat sebagai riwayat.
* Saldo budget bertambah sebesar nominal.

## Dark Mode

Mode terang/gelap, toggle tersimpan di perangkat (localStorage), default gelap.

## Notifikasi (email)

Notifikasi dikirim via **microservice `notifier`** (Go) yang memakai **Gmail SMTP** (App Password). Backend memanggil notifier via HTTP (JDK HttpClient), **fire-and-log**: kalau gagal kirim hanya dicatat di log, tidak menggagalkan operasi utama. Penerima diambil dari env `NOTIFY_EMAILS` (comma-separated, mis. 2 alamat).

Pemicu (realtime, tanpa scheduler):

* **Expense tercatat** → konfirmasi pengeluaran.
* **Budget menipis** → setelah saldo berubah, jika `budgets.alert_threshold > 0` dan `balance < alert_threshold`. Threshold diset per budget **dari UI** (modal Tambah/Edit Budget, input "Ambang notifikasi"); nilai `0` = nonaktif.
* **Top-up** dibuat → konfirmasi.
* **Budget** dibuat → konfirmasi.

Konfigurasi (env): di sisi backend `NOTIFIER_URL`, `NOTIFY_EMAILS`; di sisi notifier `MAIL_PROVIDER` (`smtp`/`resend`) + kredensial provider. Notifier mendukung dua jalur pengiriman:
* **SMTP (Gmail)** — `SMTP_HOST/PORT/USER/APP_PASSWORD/FROM`. Di produksi, notifier berjalan di **STB Armbian** (menyala 24/7) dan diakses backend via **WireGuard** (`10.8.0.4:8081`), karena SMTP keluar dari VPS (Linode) diblokir.
* **Resend** — `RESEND_API_KEY`/`RESEND_FROM`, dipakai sebagai **fallback** (`RESEND_FALLBACK=true`) bila SMTP gagal. Jika limit Resend tercapai (respons `429`), email **dilewati** (tidak dikirim, tidak retry).

Email dikirim sebagai **HTML** (inline-style, aksen `#863bff`), dengan nominal terformat Rupiah (mis. `Rp35.000`), body menampilkan sisa saldo budget, dan bahasa **Inggris**. Subjek singkat dengan emoji subtil per jenis notifikasi. Pengiriman dijalankan **async** (thread pool) sehingga tidak memperlambat request.

Mode testing: jika `NOTIFY_TEST_MODE=true`, email hanya dikirim ke `NOTIFY_TEST_EMAIL` (mengabaikan `NOTIFY_EMAILS`). Default `false`.

> Catatan Gmail: butuh **App Password** (aktifkan 2FA dulu), bukan password biasa. Batas ±500 email/hari — jauh di atas kebutuhan 2 penerima realtime.

---

# API

Semua respons:

```json
{ "success": true, "message": "...", "data": { } }
```

Error menggunakan HTTP status yang sesuai dan `{ "success": false, "message": "..." }`.

## GET /health

Pengecekan status.

## GET /options

Daftar budget aktif beserta saldonya.

```json
{
  "success": true,
  "data": {
    "budgets": [
      { "name": "Daily", "balance": 500000 }
    ]
  }
}
```

## GET /periods

Daftar periode (hanya yang masih punya expense aktif), diurutkan terbaru.

## GET /expenses?period=YYYY-MON-MON

Daftar expense aktif pada periode.

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
        "description": "Catatan",
        "hasPhoto": false
      }
    ]
  }
}
```

## POST /expenses

Membuat expense.

```json
{
  "dateTime": "2026-08-06 14:30",
  "name": "Makan Siang",
  "budget": "Daily",
  "amount": 35000,
  "description": "Catatan",
  "invoiceId": "inv-abc123"
}
```

`invoiceId` opsional — mengaitkan expense ke invoice yang sudah ada (untuk memakai ulang foto). Invoice harus berada di periode yang sama dengan `dateTime`, jika tidak → `Invoice is not in the same period as the expense`.

Response:

```json
{
  "success": true,
  "data": { "id": "abc123" }
}
```

`data.id` dipakai untuk mengunggah foto.

## PUT /expenses/{id}

Mengedit expense (body sama seperti `POST /expenses`, `invoiceId` opsional). Saldo disesuaikan ulang. Mengirim `invoiceId` mengganti referensi foto expense ke invoice tersebut (harus periode yang sama).

## DELETE /expenses/{id}

Menghapus (soft delete) expense dan mengembalikan saldo budget.

## POST /expenses/{id}/photo

Mengunggah/mengganti foto invoice (multipart `file`). Membuat invoice baru (atau mengganti referensi jika expense sudah punya foto). Opsional.

* Hanya gambar: jpeg, png, webp, gif.
* Maksimal 10MB.

## DELETE /expenses/{id}/photo

Menghapus foto expense (melepas `invoice_id`). Jika invoice tersebut tidak lagi dipakai expense mana pun, baris invoice **dan file foto** di disk ikut dihapus; jika masih dipakai expense lain, invoice & file dipertahankan.

## GET /expenses/{id}/photo

Mengembalikan file foto invoice. `404` jika tidak ada.

## GET /invoices?date=yyyy-MM-dd HH:mm

Daftar id invoice yang punya foto pada periode dari `date` (untuk fitur "Pakai Foto Periode Ini").

```json
{
  "success": true,
  "data": { "invoices": [ { "id": "inv-abc123" } ] }
}
```

## GET /invoices/{id}/photo

Mengembalikan file foto sebuah invoice. `404` jika tidak ada. Mendukung `application/pdf`.

## POST /invoices

Membuat invoice **alur Scan AI** dari file upload (multipart `file` + `date` `yyyy-MM-dd HH:mm`), menandai `scan_flow`, mengatur status `ANALYZING`, lalu memicu analisis AI **async**. Mengembalikan `{ "invoiceId": "..." }`.

* Hanya gambar (jpg/png/webp/gif) atau **PDF**, maksimal 10MB.

## GET /invoices/{id}

Detail sebuah invoice: `{ id, type, status, errorMessage?, analysis? }`. Saat status `TO_REVIEW`, `analysis` berisi hasil AI `{ storeName, total, items: [{ name, amount, suggestedBudget }] }`.

## POST /invoices/{id}/retry

Mengulang analisis AI untuk invoice berstatus `ERROR` (reset ke `ANALYZING` + jalankan ulang).

## POST /expenses/batch

Membuat banyak expense hasil split struk dalam **satu transaksi** (alur `/scan`).

```json
{
  "dateTime": "2026-08-06 14:30",
  "invoiceId": "inv-abc123",
  "groups": [
    { "name": "Belanja Superindo", "budget": "Alana", "amount": 65000, "description": "Popok; Kopi" }
  ]
}
```

Semua group memakai `dateTime` yang sama; saldo di-adjust **agregat per budget**; invoice ditandai `SUBMITTED`; mengirim **1 email ringkasan** (bukan per expense). Respons `{ "count": N }`. Idempotensi via `Idempotency-Key`.

## GET /invoices?date=...&scan=...

Daftar id invoice per periode (untuk "Pakai Foto Periode Ini"). Dengan `scan=true`, hanya invoice alur `/scan` yang dikembalikan. Setiap item: `{ id, createdAt, status, type }`.

## Idempotensi

Semua endpoint `POST` (`/expenses`, `/expenses/batch`, `/expenses/{id}/photo`, `/topups`, `/budgets`, `/invoices`) menerima header `Idempotency-Key` opsional. Jika key diberikan, backend menyimpan respons selama **24 jam** dan mengembalikan respons tersimpan untuk key yang sama (mencegah duplikat saat retry). Baris lama dibersihkan otomatis saat penyimpanan baru.

## GET /summary?period=YYYY-MON-MON

Ringkasan periode: total, jumlah transaksi, dan pengeluaran per budget (urut terbesar).

```json
{
  "success": true,
  "data": {
    "period": "2026-JUL-AUG",
    "total": 80000,
    "count": 3,
    "byBudget": [
      { "budget": "Weekly", "amount": 50000, "count": 1 }
    ]
  }
}
```

## GET /trend?months=3

Ringkasan beberapa periode terakhir (total + count), urut terlama ke terbaru.

## GET /topups

Daftar riwayat top-up.

## POST /topups

Menambah saldo budget.

```json
{ "budget": "Daily", "amount": 50000, "description": "Gaji" }
```

`dateTime` opsional (default waktu sekarang).

## POST /budgets

Menambah budget.

```json
{ "name": "Gadget", "balance": 100000, "alertThreshold": 50000 }
```

`balance` opsional (default 0). `alertThreshold` opsional (default 0 = nonaktif, memicu notifikasi "budget menipis" saat `balance < alertThreshold`). Duplikat nama → `Budget already exists`.

## PUT /budgets/{name}

Mengedit budget (nama, saldo, dan/atau `alertThreshold`). `balance` dan `alertThreshold` opsional (jika null, tidak diubah).

## DELETE /budgets/{name}

Menghapus budget (soft delete, rename `DELETED_<nama>_<id>`, expense-nya ikut soft-delete).

---

# Environment Variables

## Backend (`backend/.env`)

```text
PORT=8080
DB_URL=jdbc:mysql://host.docker.internal:33060/expense_tracker?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=expense_tracker_user
DB_PASSWORD=password-kuat
UPLOAD_DIR=/app/uploads
GEMINI_API_KEY=AIza...            # untuk Scan Struk dengan AI
AI_MODEL=gemini-3.5-flash-lite    # opsional, default
AI_TIMEOUT=60                     # opsional, detik
```

## Frontend

```text
VITE_API_URL=/api
```

---

# Docker

```bash
docker compose up --build
```

Frontend: http://localhost:3000 · Backend: http://localhost:8080

Untuk **lokal**, `docker-compose.yml` menyertakan service `mysql` sendiri (self-contained); skema & seed budget dibuat otomatis oleh Flyway. Foto tersimpan di direktori `./uploads` (bind mount).

Untuk **produksi**, `docker-compose.prod.yml` memakai MySQL VPS dan image native.

---

# Acceptance Criteria

* User dapat membuka aplikasi dari browser.
* User dapat mencatat pengeluaran (dengan/ tanpa foto invoice).
* User dapat melihat saldo per budget dan menambah saldo (top-up).
* User dapat mengelola budget (tambah/edit/hapus).
* User dapat melihat riwayat per periode, filter/sort, edit, hapus, dan lihat foto.
* Data tersimpan di MySQL.
* Aplikasi dapat dijalankan dengan Docker Compose.

---

# Out of Scope

* Login / authentication
* Multi-user
* Kategori/tag
* Grafik detail
* Export PDF/Excel
* Notifikasi via WhatsApp (email sudah ada; WhatsApp bisa ditambahkan via Twilio/Meta Cloud API bila diperlukan)

---

# Future Improvements

* Authentication & multi-user
* Grafik tren yang lebih detail
* Export CSV/Excel
* Ringkasan notifikasi harian/bulanan terjadwal
* Notifikasi via WhatsApp (Twilio / Meta Cloud API)
