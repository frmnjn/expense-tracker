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

* [x] ExpenseRequest (dateTime, name, budget, amount, description)

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
* [x] Auto-reorder sheet (periode terbaru di kiri, Budget paling kanan)
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

---

# Phase 7 - JVM Tuning

Optimasi memory backend dengan membatasi resource JVM.

Hasil pengukuran (apples-to-apples, image sama):

| Metrik | Baseline (tanpa flag) | Dengan tuning | Penghematan |
|---|---|---|---|
| RSS | 197.6 MB | 170.8 MB | 27 MB |
| Pss_Anon | 170.8 MB | 144.9 MB | 26 MB |

Breakdown JVM tuned: heap 38MB + metaspace 45MB + codecache 10MB + native ~55MB.

## Konfigurasi JVM

* [x] Tambah flag JVM di ENTRYPOINT Dockerfile backend
* [x] Batasi heap (-Xms, -Xmx)
* [x] Batasi Metaspace (-XX:MaxMetaspaceSize)
* [x] Batasi CodeCache (-XX:ReservedCodeCacheSize)
* [x] Gunakan SerialGC (-XX:+UseSerialGC)
* [x] Nonaktifkan komponen yang tidak dipakai (headless, JMX)

## Docker

* [x] Tambah mem_limit backend di docker-compose.yml
* [x] Tambah mem_limit backend di docker-compose.prod.yml

## Verifikasi

* [x] Build ulang image backend
* [x] Bandingkan memory usage via docker stats
* [x] Health endpoint tetap berjalan
* [x] GET /options tetap berjalan
* [x] POST /expenses tetap berjalan

---

# Phase 8 - GraalVM Native Image

Mengubah backend menjadi native executable (~50MB total) menggantikan JVM runtime (~170MB).

## Konteks & Target

* Phase 7 (JVM tuning) hanya menghemat 27MB; bottleneck adalah class loading + metaspace yang tidak bisa dikurangi flag JVM.
* Target: RSS backend turun dari 170MB menjadi ~50MB, startup < 1 detik.
* Dukungan resmi: Spring Boot 4.1.0 + GraalVM 25 + Native Build Tools 1.1.1 (Java 25).
* Distribusi ke VPS: docker save -> scp/SFTP -> docker load (tanpa registry, sudah diputuskan).

## Hasil (terverifikasi)

| Metrik | JVM (Phase 7) | Native Image (Phase 8) | Penghematan |
|---|---|---|---|
| RSS (docker stats) | 171.6 MiB | 53.85 MiB | **117.8 MiB (68%)** |
| Startup | ~2.3 s | **0.58 s** | ~4x lebih cepat |
| Image size | ~300 MB+ | 117 MB | lebih kecil |

## Risiko & Mitigasi

| Risiko | Mitigasi |
|---|---|
| Refleksi Gson pada model & request Google Sheets (ValueRange, Sheets$Spreadsheets$Values$Get, dll) | Tracing agent generate `native-config/reachability-metadata.json`, di-pass via `-H:ConfigurationFileDirectories` |
| Query params hilang di native (issue #23642 google-api-services) | **Terbukti muncul di build awal** (URL `spreadsheets//values/` kosong) -> diperbaiki dengan config refleksi |
| google-api-services-sheets / google-api-client / google-oauth-client tidak punya native config bawaan | Tracing agent capture refleksi yang hilang (hanya google-http-client yang bundling reflect-config) |
| `add-reachability-metadata` goal menimpa `META-INF/native-image` | Letakkan config di `native-config/` terpisah, referensikan via ConfigurationFileDirectories |
| Build lambat (~6 menit) & Peak RSS 6.8GB | Build lokal; RAM cukup saat eksekusi |
| Native link glibc, butuh libz | Runtime pakai `distroless/cc-debian12` + copy `libz.so.1` dari debian bookworm-slim |

## Keputusan Build

* Build dua tahap:
  * Tahap 1: compile jar dengan `maven:3.9-eclipse-temurin-25`
  * Tahap 2: proses AOT + native-image dengan image Oracle GraalVM 25 (`container-registry.oracle.com/graalvm/native-image:25`) + install maven via microdnf
  * Runtime: base image `gcr.io/distroless/cc-debian12` + zlib (glibc; jangan Alpine/musl)
* Aktifkan profile `native` di Maven (native-maven-plugin) + executions `add-reachability-metadata` & `compile`.
* Tracing agent: `-agentpath:...libnative-image-agent.so=config-output-dir=<dir>` saat jalankan jar JVM di test sheet, hit /health /options POST /expenses.
* Hasil: image `expense-tracker-backend-native:latest`.

## Konfigurasi Backend

* [x] Tambah `native-maven-plugin` di pom.xml (profil native + executions)
* [x] Tambah buildArg `-H:ConfigurationFileDirectories=${project.basedir}/native-config`
* [x] Jalankan tracing agent untuk capture metadata refleksi Google -> `native-config/reachability-metadata.json`
* [x] Generate native executable via `mvn -Pnative package`
* [x] Buat `Dockerfile.native` multi-stage self-contained (build + runtime minimal)
* [x] Verifikasi executable native berjalan di local (health, options, POST ke test sheet)

## Docker

* [x] `docker build -f backend/Dockerfile.native -t expense-tracker-backend-native:latest .`
* [x] Ukur RSS native via docker stats (53.85 MiB, target tercapai)
* [x] Update `docker-compose.prod.yml`: backend pakai image native + mem_limit 128m
* [x] Update `docker-compose.yml` (dev) pakai image native + mem_limit 128m

## Verifikasi Fungsional (test sheet, bukan produksi)

* [x] `/health` OK
* [x] `/options` mengembalikan budget yang benar (uji refleksi Gson)
* [x] POST `/expenses` berhasil ke test sheet
* [x] Startup 0.58s

## Distribusi ke VPS (terverifikasi)

* [x] `docker save expense-tracker-backend-native:latest | gzip > backend-native.tar.gz`
* [x] `scp`/SFTP `backend-native.tar.gz` ke VPS
* [x] Di VPS: `docker load < backend-native.tar.gz`
* [x] Update `docker-compose.prod.yml` di VPS (image native)
* [x] `docker compose up -d` + verifikasi health di produksi
* [x] Bandingkan memory VPS (docker stats) vs sebelum native — **54.13 MiB** di produksi

## Otomatisasi Build & Deploy

* [x] `build-native.sh` — build image native lokal (docker build Dockerfile.native)
* [x] `deploy-native.sh` — export image, scp ke VPS, git pull, docker load, up -d
* [x] `backend/generate-native-config.sh` — regenerate reachability-metadata via tracing agent

## Rollback

* [x] Image JVM (`expense-tracker-backend:0.0.1-SNAPSHOT`) tetap tersimpan di VPS sebagai fallback
* [x] Dokumentasikan cara rollback: `docker compose -f docker-compose.prod.yml down && docker compose up -d` setelah mengembalikan konfigurasi image

---

# Phase 9 - Fitur Saldo Budget

Menambahkan saldo per budget. Saldo disimpan sebagai kolom B pada tab `Budget`, dikurangi otomatis saat expense tersimpan, dan diisi manual di Google Sheets (misal tiap gajian tanggal 25). Budget boleh bernilai negatif.

## Docs

* [ ] Update PRD.md (format sheet Budget, respons /options, UI dropdown dua kolom + preview saldo)
* [ ] Update TASKS.md (checklist ini)
* [ ] Buat docs/BALANCE.md

## Backend

* [ ] Model: `BudgetOption(name, balance)` + `OptionsResponse(List<BudgetOption>)`
* [ ] `GoogleSheetsClient.getOptions` membaca tab Budget `A:B` (parse saldo, blank -> 0)
* [ ] `GoogleSheetsClient.decrementBudget(budget, amount)` (baca, kurangi, tulis ulang)
* [ ] `ExpenseService.createExpense` memanggil decrementBudget setelah append
* [ ] Unit test parsing saldo & decrement
* [ ] Regenerasi native config (`./backend/generate-native-config.sh`)

## Frontend

* [ ] Type `OptionsResponse` -> `{ name, balance }[]`
* [ ] Utils format rupiah + warna tanda
* [ ] Dropdown dua kolom via renderOption
* [ ] Kartu preview "Saldo nanti" (disable jika budget/nominal kosong)

## Verifikasi

* [ ] Backend build & test lolos
* [ ] Frontend lint & build lolos
* [ ] Data tetap tersimpan ke sheet periode yang benar
* [ ] Saldo berkurang sesuai expense (test sheet)
* [ ] Saldo negatif dapat tampil

---

# Phase 10 - Daftar, Edit & Hapus Pengeluaran

Menampilkan daftar pengeluaran per periode, mengedit, dan menghapus (soft delete) beserta penyesuaian saldo budget.

## Docs

* [x] Update PRD.md (skema ID+Deleted, API period/expenses/put/delete, UI halaman Riwayat, out-of-scope)
* [x] Update TASKS.md (checklist ini)

## Backend

* [x] Model: `ExpenseResponse`, `PeriodsResponse`, `ExpensesResponse`
* [x] Kolom `ID` & `Deleted` di header sheet periode; appendExpense menulis ID unik
* [x] `GoogleSheetsClient.getExpenses` (baca A:G, filter baris Deleted)
* [x] `GoogleSheetsClient.getPeriodSheetTitles`
* [x] `GoogleSheetsClient.findExpense` (cari id lintas sheet -> ExpenseRef)
* [x] `GoogleSheetsClient.updateExpenseRow`
* [x] `GoogleSheetsClient.softDeleteExpense`
* [x] Refactor helper baca/tulis saldo (`adjustBudgetBalance`, `readBudgetBalance`)
* [x] `ExpenseService.getPeriods` / `getExpenses` / `updateExpense` / `deleteExpense` + kalkulasi delta saldo
* [x] Controller: `GET /periods`, `GET /expenses`, `PUT /expenses/{id}`, `DELETE /expenses/{id}`
* [x] Unit test delta saldo, find row, soft delete
* [x] Regenerasi native config

## Frontend

* [x] Types `Expense`, `PeriodsResponse`, `ExpensesResponse`
* [x] Service: getPeriods, getExpenses, updateExpense, deleteExpense
* [x] Hooks: usePeriods, useExpenses, useUpdateExpense, useDeleteExpense
* [x] Halaman `/riwayat` (dropdown bulan + tabel + modal edit + konfirmasi hapus)
* [x] Preview saldo saat edit & hapus (saldo nanti bertambah/dikurangi)
* [x] Link ke `/riwayat` dari halaman utama
* [x] Refetch daftar + invalidate options setelah edit/hapus

## Verifikasi

* [x] Backend build & test lolos
* [x] Frontend lint & build lolos
* [x] Test sheet: buat -> edit nominal/ganti budget -> hapus -> cek saldo konsisten
* [x] Soft delete: baris ditandai, tidak tampil di daftar, tidak dihapus fisik
* [x] Saldo dropdown ter-update setelah edit/hapus

---

# Phase 11 - Dark Mode

## Frontend

* [x] `MantineProvider` pakai `colorSchemeManager` (localStorage, persist) & `defaultColorScheme="dark"`
* [x] Komponen `ColorSchemeToggle` (toggle ☀️/🌙)
* [x] Pasang toggle di halaman form (`/` & `/catat`) dan halaman Riwayat
* [x] Alias rute `/catat` untuk halaman form

## Verifikasi

* [x] Build & lint lolos
* [x] Toggle menyala/mati dan persist setelah reload
* [x] Tabel/modal/kartu tetap terbaca di mode gelap

---

# Phase 12 - Dashboard

## Docs

* [x] Update PRD.md (endpoint /summary, UI Dashboard, rute `/` = dashboard)
* [x] Update TASKS.md (checklist ini)
* [x] Update README.md

## Backend

* [x] Model: `SummaryResponse`, `BudgetSummary`
* [x] `ExpenseService.getSummary(period)` (total, count, pengeluaran per budget)
* [x] Controller: `GET /summary?period=`
* [x] Unit test agregasi summary
* [x] Regenerasi native config (endpoint & model baru)

## Frontend

* [x] Types `SummaryResponse`, `BudgetSummary`; service `getSummary`; hook `useSummary`
* [x] Halaman `DashboardPage` (dropdown periode + kartu ringkasan + saldo per budget + pengeluaran terbesar)
* [x] Rute `/` = form; `/dashboard` = dashboard; `/riwayat` = riwayat; update nav link di semua halaman
* [x] Build & lint lolos
* [x] Cek di docker local (test sheet)

## Verifikasi

* [x] Backend build & test lolos
* [x] Frontend build & lint lolos
* [x] Dashboard menampilkan ringkasan sesuai data (test sheet)

---

# Phase 13 - Top-up Saldo

## Docs

* [x] Update PRD.md (endpoint /topups, UI tambah saldo, env GOOGLE_TOP_UP_SHEET)
* [x] Update TASKS.md (checklist ini)
* [x] Update README.md

## Backend

* [x] Config `google.top-up-sheet` (default `TopUp`)
* [x] Model: `TopUpResponse`, `TopUpsResponse`, `TopUpRequest`
* [x] Tab `TopUp` dibuat otomatis (header + format nominal) + reorder
* [x] `GoogleSheetsClient.appendTopUp` / `getTopUps`
* [x] `ExpenseService.createTopUp` (append + adjustBudgetBalance(+amount)) / `getTopUps`
* [x] Controller: `POST /topups`, `GET /topups`
* [x] Unit test createTopUp
* [x] Regenerasi native config (endpoint & model baru)

## Frontend

* [x] Types/service/hook: useTopUps, useCreateTopUp
* [x] `TopUpModal` (pre-filled budget dari kartu, tanpa dropdown budget, tampil saldo saat ini)
* [x] Ikon "+" di tiap kartu budget di Dashboard + riwayat top-up
* [x] Build & lint lolos
* [x] Cek di docker local

## Verifikasi

* [x] POST /topups menambah saldo & tercatat di tab TopUp (test sheet)
* [x] Build & lint lolos

---

# Phase 14 - Filter & Sort di Riwayat

## Docs

* [x] Update PRD.md (filter & sort di UI Riwayat)
* [x] Update TASKS.md (checklist ini)
* [x] Update README.md

## Frontend

* [x] Search nama (client-side)
* [x] Filter budget (dropdown dari /options)
* [x] Sort (waktu naik/turun, nominal besar/kecil)
* [x] Diterapkan di tabel desktop & list kartu mobile
* [x] State "tidak ada hasil" jika filter kosong
* [x] Build & lint lolos
* [x] Cek di docker local

---

# Phase 15 - Dashboard 3 Bulan Terakhir

## Docs

* [x] Update PRD.md (endpoint /trend, UI 3 bulan terakhir)
* [x] Update TASKS.md (checklist ini)
* [x] Update README.md

## Backend

* [x] Model: `TrendPoint`, `TrendResponse`
* [x] `ExpenseService.getTrend(months)` (periode terakhir, urut terlama ke terbaru)
* [x] Controller: `GET /trend?months=`
* [x] Unit test getTrend
* [x] Regenerasi native config (endpoint & model baru)

## Frontend

* [x] Types/service/hook: useTrend
* [x] Section "3 Bulan Terakhir" di Dashboard (list + bar proporsional)
* [x] Build & lint lolos
* [x] Cek di docker local

## Verifikasi

* [x] GET /trend mengembalikan total & count per periode (test sheet)
* [x] Build & lint lolos

---

# Phase 16 - Restrukturisasi Dashboard

## Backend

* [x] `BudgetSummary` tambah field `count`; `getSummary` menghitung jumlah transaksi per budget

## Frontend

* [x] Hapus kartu stat (total, transaksi, budget negatif) & section pengeluaran terbesar & riwayat top-up flat
* [x] Kartu budget: badge urutan terbesar (1–3), saldo, ikon `+` & `📋`, jumlah transaksi + pengeluaran periode
* [x] `TopUpHistoryModal` (ikon 📋 → riwayat top-up per budget, terbaru di atas)
* [x] Pindah "3 Bulan Terakhir" ke bawah "Saldo per Budget"
* [x] Build & lint lolos

## Verifikasi

* [x] `/summary` mengembalikan `count` per budget (test sheet)
* [x] Cek di docker local

---

# Phase 17 - Migrasi ke MySQL

Memindahkan penyimpanan dari Google Sheets ke database MySQL (berbagi instance dengan WordPress, database `expense_tracker` terpisah). API tidak berubah.

## Backend

* [x] Tambah `spring-boot-starter-jdbc` + `mysql-connector-j`; hapus dependency Google Sheets
* [x] Hapus `GoogleSheetsClient` & config Google; full cutover
* [x] Repository JdbcTemplate: `BudgetRepository`, `ExpenseRepository`, `TopUpRepository`
* [x] `ExpenseService` dipindah ke repository (API & controller sama)
* [x] Skema `schema.sql` (budgets, expenses, top_ups) dibuat via `spring.sql.init` *(digantikan Flyway di Phase 19)*
* [x] `PeriodSheetName.periodStart` + `FORMATTER` untuk menghitung periode dari date_time
* [x] Unit test diadaptasi (mock repository)
* [ ] Regenerasi native config (MySQL JDBC driver refleksi)

## Frontend

* [x] Tidak ada perubahan (API sama)

## Infra

* [x] docker-compose: backend `extra_hosts` host.docker.internal + env DB dari `.env` (produksi)
* [x] `scripts/seed_budgets.py` (impor budget + saldo dari tab Budget)
* [x] docker-compose lokal: service `mysql` self-contained + backend konek ke `mysql:3306` (skema & seed budget via Flyway)
* [ ] Setup database & user `expense_tracker` di MySQL VPS
* [ ] Seed tabel `budgets` (via script)

## Verifikasi

* [x] Backend build & test lolos
* [x] Endpoint (options/expenses/summary/trend/topups/update/delete) jalan terhadap MySQL lokal
* [x] `schema.sql` membuat tabel otomatis saat start *(digantikan Flyway di Phase 19)*
* [ ] Native image + deploy ke VPS

---

# Phase 18 - Tambah & Hapus Budget

## Backend

* [x] `budgets` tambah kolom `is_active` (soft delete); DB fresh via `CREATE TABLE`, DB lama migrasi manual (`ALTER TABLE budgets ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE`)
* [x] `BudgetRepository`: filter `is_active`, `create`, `softDelete` (rename `DELETED_<nama>_<id>` agar nama asli bisa dipakai ulang), `update` (nama + saldo)
* [x] Model `BudgetCreateRequest`, `BudgetUpdateRequest`; `ExpenseService.createBudget` / `deleteBudget` / `updateBudget`
* [x] Controller: `POST /budgets`, `PUT /budgets/{name}`, `DELETE /budgets/{name}`
* [x] Unit test create/duplicate/delete/update
* [ ] Regenerasi native config (endpoint & model baru)

## Frontend

* [x] Types/service/hook: useCreateBudget, useUpdateBudget, useDeleteBudget
* [x] `AddBudgetModal` (nama + saldo awal), `EditBudgetModal` (nama + saldo), `DeleteBudgetModal` (konfirmasi)
* [x] Tombol "+ Budget" & ikon ✎/🗑 di tiap kartu budget di Dashboard
* [x] Build & lint lolos
* [x] Cek di docker local

## Verifikasi

* [ ] Backend build & test lolos
* [ ] `/options` tidak menampilkan budget nonaktif

---

# Phase 19 - Migrasi DB dengan Flyway

Mengganti `spring.sql.init` (schema.sql) dengan Flyway versioned migrations agar penambahan kolom/ubah skema ke depan lebih mudah.

## Backend

* [x] Tambah `flyway-core` + `flyway-mysql`
* [x] `FlywayConfig` bean (Boot 4 tanpa auto-config) dengan `baselineOnMigrate(true)`
* [x] Hapus `schema.sql` & `spring.sql.init`
* [x] `V1__init.sql` (skema: budgets + expenses + top_ups)
* [x] `V2__budget_is_active.sql` (tambah kolom is_active idempotent via stored procedure)
* [x] `V3__seed_default_budgets.sql` (INSERT IGNORE)
* [x] `scripts/seed_budgets.py` diubah jadi upsert (ON DUPLICATE KEY UPDATE)

## Infra

* [x] Hapus `scripts/initdb` & mount-nya (skema & seed kini via Flyway)

## Verifikasi

* [x] Fresh DB: V1+V2+V3 jalan, schema & seed budget ada
* [x] Existing DB tanpa history & tanpa is_active: baseline V1 -> V2 tambah is_active -> V3, data dipertahankan
* [ ] Native config regen + build (saat deploy)

---

# Phase 20 - Backup & Restore MySQL

## Scripts

* [x] `scripts/backup_mysql.sh` (mysqldump + gzip + rotasi, baca kredensial dari `.env`)
* [x] `scripts/restore_mysql.sh`

## VPS

* [x] Backup manual teruji (dump berisi data produksi)
* [x] Restore teruji (menimpa DB dengan isi backup)
* [x] Cron harian terpasang (20:00 UTC / 03:00 WIB) + log `/var/log/expense-backup.log`

## Docs

* [x] README: section Backup & Restore MySQL

---

# Phase 21 - Upload & Lihat Foto Invoice

## Backend

* [x] Flyway `V4__expense_photo.sql`: `expenses.photo_path`
* [x] Env `UPLOAD_DIR` (default `/app/uploads`)
* [x] `POST /expenses` mengembalikan `data.id`
* [x] `POST /expenses/{id}/photo` (multipart, validasi jpg/png/webp/gif, max 10MB)
* [x] `GET /expenses/{id}/photo` (sajikan file; 404 jika tak ada)
* [x] `ExpenseResponse.hasPhoto`; multipart config

## Frontend

* [x] `/catat`: `PhotoInput` (modal pilihan Kamera / Galeri) + preview; upload setelah create
* [x] Fix Content-Type: apiClient tidak memaksa JSON (agar FormData upload foto terkirim multipart)
* [x] `/riwayat`: tombol 📷 jika `hasPhoto` -> modal tampil gambar

## Infra

* [x] docker-compose (local & prod): volume `uploads` -> `/app/uploads`

## Verifikasi

* [x] Upload + tampil foto (local, langsung & via proxy)
* [x] Non-image ditolak
* [x] Native config regen + build (saat deploy)

---

# Phase 22 - Shared Invoice, Reuse Foto, Idempotensi, & Edit Foto

## Backend

* [x] Flyway `V5__invoices.sql`: tabel `invoices`, kolom `expenses.invoice_id`; migrasi foto lama dari `expenses.photo_path` ke invoice; hapus `photo_path`
* [x] `InvoiceData` / `InvoiceRepository` / `InvoiceService` / `InvoiceController`
* [x] `GET /invoices?date=...` (daftar id invoice per periode) & `GET /invoices/{id}/photo`
* [x] `ExpenseRequest` + `invoiceId` opsional (create & update, divalidasi periode sama)
* [x] `attachPhoto` kini membuat invoice & menghubungkan `invoice_id`
* [x] `DELETE /expenses/{id}/photo` (melepas `invoice_id`; hapus baris invoice + file jika tidak dipakai expense lain, pertahankan jika masih dipakai)
* [x] `ExpenseResponse.hasPhoto` = `invoice_id` terisi
* [x] Flyway `V6__idempotency.sql`: tabel `idempotency_keys`
* [x] Flyway `V7__indexes.sql`: index `expenses.invoice_id`, komposit `expenses(period, deleted)`, `idempotency_keys.created_at`
* [x] `IdempotencyRepository` / `IdempotencyService` (Jackson 3); semua POST membaca header `Idempotency-Key`, simpan respons 24 jam + cleanup otomatis
* [x] `@RegisterReflectionForBinding` + `InvoiceResponse[]` untuk serialisasi native `/invoices`
* [x] Unit test: detachPhoto, updateExpense+invoice, InvoiceService, IdempotencyService

## Frontend

* [x] `PhotoInput`: opsi ke-3 "Pakai Foto Periode Ini" (grid thumbnail invoice via `useInvoices`)
* [x] `ExpenseForm`: kirim `invoiceId` saat reuse, guard `submittingRef` anti double-submit
* [x] `services`/`hooks`: `getInvoices`, `getInvoicePhotoUrl`, `deletePhoto`, `useDeletePhoto`, invalidasi `useUploadPhoto`
* [x] `EditExpenseModal`: ganti foto (upload baru / reuse periode ini) & hapus foto

## Infra / Verifikasi

* [x] Idempotensi teruji (request key sama 2x → id sama, tidak duplikat)
* [x] Delete photo → `hasPhoto` false; update dengan `invoiceId` → reuse foto
* [x] Native config regen + build + deploy (terverifikasi di VPS, termasuk fix refleksi array)

---

# Phase 23 - Notifikasi Email (microservice notifier)

## notifier/ (Go)

* [x] `main.go` + `go.mod` (std lib `net/smtp`, tanpa dependency)
* [x] `POST /send` `{to[], subject, body}` — dukungan **SMTP (Gmail)** & **Resend** (`MAIL_PROVIDER`)
* [x] Fallback otomatis: `RESEND_FALLBACK=true` → SMTP gagal, coba Resend
* [x] Resend: jika limit tercapai (`429`) → lewati kirim (tanpa retry)
* [x] `GET /health`
* [x] `Dockerfile` multi-stage (Go → alpine, ca-certificates + tzdata)
* [x] Produksi: notifier dijalankan di **STB Armbian** (`docker-compose.stb.yml`), diakses backend VPS via **WireGuard** `10.8.0.4:8081` (karena SMTP dari Linode diblokir)

## Backend

* [x] Flyway `V8__budget_alert_threshold.sql`: `budgets.alert_threshold` (0 = nonaktif)
* [x] `BudgetRepository.getAlertThreshold`
* [x] Threshold diset dari UI: input "Ambang notifikasi" di modal Tambah & Edit Budget; indikator `⚠️ Ambang` di kartu budget
* [x] `BudgetOption` + `alertThreshold`; `BudgetCreateRequest`/`BudgetUpdateRequest` + `alertThreshold`
* [x] `NotificationService` (JDK HttpClient → notifier, fire-and-log, JSON escape)
* [x] Notifikasi dikirim **async** (thread pool) agar tidak memperlambat request
* [x] Mode testing: `NOTIFY_TEST_MODE` → hanya kirim ke `NOTIFY_TEST_EMAIL`
* [x] Email HTML (inline CSS, rupiah via `NumberFormat(id)`), bahasa Inggris, sisa saldo di body, emoji subtil di subject
* [x] `NOTIFY_EMAILS` toleran spasi & tanda kutip di sekitar koma
* [x] Hook `ExpenseService`: konfirmasi expense, budget menipis (`balance < alert_threshold`), top-up, budget dibuat
* [x] Config `notify.notifier-url` / `notify.emails`
* [x] Unit test: alert terkirim saat < threshold, skip saat 0, notif top-up

## Docker

* [x] Service `notifier` di `docker-compose.yml` & `.docker-compose.prod.yml`
* [x] Backend `NOTIFIER_URL=http://notifier:8081`
* [x] `.env.example` di-update (NOTIFY_EMAILS, NOTIFIER_URL, SMTP_*)

## Verifikasi (lokal)

* [x] Backend test lolos (73)
* [x] notifier health OK; `POST /send` mencapai Gmail SMTP (502 saat tanpa kredensial → wiring benar)
* [x] Backend → notifier teruji (log `notification send failed: status=502` saat tanpa kredensial)
* [x] V8 migration applied
* [x] Latensi create expense turun (async) → ~0.1s; email terkirim sukses

## Deploy (perlu kredensial pengguna)

* [ ] User set App Password Gmail (2FA) + `NOTIFY_EMAILS` (2 alamat) + `alert_threshold` per budget
* [ ] Native config regen + build native + deploy VPS
