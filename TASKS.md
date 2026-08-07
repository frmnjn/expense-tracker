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
* [ ] Regenerasi native config (endpoint & model baru)

## Frontend

* [x] Types `SummaryResponse`, `BudgetSummary`; service `getSummary`; hook `useSummary`
* [x] Halaman `DashboardPage` (dropdown periode + kartu ringkasan + saldo per budget + pengeluaran terbesar)
* [x] Rute `/` = form; `/dashboard` = dashboard; `/riwayat` = riwayat; update nav link di semua halaman
* [ ] Build & lint lolos
* [ ] Cek di docker local (test sheet)

## Verifikasi

* [ ] Backend build & test lolos
* [ ] Frontend build & lint lolos
* [ ] Dashboard menampilkan ringkasan sesuai data (test sheet)
