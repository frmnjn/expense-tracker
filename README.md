# Expense Tracker

Aplikasi web untuk mencatat pengeluaran harian, memantau saldo per budget, dan mengelola riwayat. Backend memakai **MySQL**.

---

## Fitur

* Catat pengeluaran (waktu, nama, budget, nominal, deskripsi) + **foto invoice opsional** (kamera/galeri/pakai foto yang sudah ada di periode ini)
* Saldo per budget (berkurang saat pengeluaran, bertambah via top-up)
* Dashboard: saldo per budget, pengeluaran terbesar, 3 bulan terakhir
* Riwayat per periode: search, filter, sort, edit (termasuk ganti/hapus foto), hapus, lihat foto
* Kelola budget (tambah/edit/hapus, soft delete) — termasuk ambang notifikasi "budget menipis" per budget
* Idempotensi POST (header `Idempotency-Key`) untuk mencegah duplikat
* Notifikasi email (Gmail SMTP via microservice `notifier`): konfirmasi expense/top-up/budget + peringatan budget menipis (threshold per budget)
* Dark mode (default gelap)

---

## Tech Stack

* **Frontend:** React 19 + Vite + TypeScript, React Router, TanStack Query, Axios, Mantine UI
* **Backend:** Java 25 + Spring Boot 4, Spring JDBC (JdbcTemplate), Flyway, GraalVM Native Image (produksi)
* **Notifier:** Go (std lib `net/smtp`), kirim email via Gmail SMTP
* **Storage:** MySQL 8+

---

## Menjalankan (lokal)

```bash
docker compose up --build
```

* Frontend: http://localhost:3000
* Backend: http://localhost:8080

`docker-compose.yml` menyertakan service `mysql` sendiri (self-contained). Skema & seed budget dibuat otomatis oleh **Flyway**. Foto invoice tersimpan di direktori `./uploads` (bind mount, tetap ada meski container/project dihapus).

---

## Menjalankan (produksi)

`docker-compose.prod.yml` memakai MySQL yang sudah ada di VPS (berbagi dengan WordPress) dan image backend **native**.

Setup database (sekali):

```sql
CREATE DATABASE expense_tracker;
CREATE USER 'expense_tracker_user'@'%' IDENTIFIED BY 'password-kuat';
GRANT ALL PRIVILEGES ON expense_tracker.* TO 'expense_tracker_user'@'%';
FLUSH PRIVILEGES;
```

Skema dibuat otomatis oleh Flyway saat backend start (`baselineOnMigrate` menangani DB yang sudah ada).

Import budget (opsional, dari Google Sheets lama):

```bash
python3 scripts/seed_budgets.py > seed.sql
mysql -u expense_tracker_user -p expense_tracker < seed.sql
```

---

## Environment Variables

### Backend (`backend/.env`)

```text
PORT=8080
DB_URL=jdbc:mysql://host.docker.internal:33060/expense_tracker?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=expense_tracker_user
DB_PASSWORD=password-kuat
UPLOAD_DIR=/app/uploads
NOTIFIER_URL=http://notifier:8081
NOTIFY_EMAILS=email1@example.com,email2@example.com
NOTIFY_TEST_MODE=false
NOTIFY_TEST_EMAIL=your-email@gmail.com
```

### Notifier (`notifier`)

Provider diatur via `MAIL_PROVIDER` (`smtp` atau `resend`).

**SMTP (Gmail):**

```text
MAIL_PROVIDER=smtp
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_APP_PASSWORD=your-gmail-app-password
SMTP_FROM=your-email@gmail.com
```

Gmail wajib memakai **App Password** (aktifkan 2FA dulu). Catatan: SMTP keluar bisa **diblokir** di sebagian provider VPS (mis. Linode memblokir port SMTP); pakai Resend jika begitu.

**Resend:**

```text
MAIL_PROVIDER=resend
RESEND_API_KEY=re_xxxxxxxx
RESEND_FROM=Expense Tracker <onboarding@resend.dev>
```

Jika limit Resend tercapai (respons `429`), notifier melewatkan pengiriman (tidak dikirim, tidak retry).

### Frontend

```text
VITE_API_URL=/api
```

---

## Backup & Restore MySQL

Script di VPS (membaca kredensial dari `backend/.env`).

```bash
# backup (mysqldump + gzip + simpan 14 terakhir)
cd /root/expense-tracker
./scripts/backup_mysql.sh

# restore (menimpa DB saat ini!)
./scripts/restore_mysql.sh backups/expense_tracker_<timestamp>.sql.gz
```

Cron harian sudah terpasang di VPS (03:00 WIB), log di `/var/log/expense-backup.log`.

---

## API

Dokumentasi lengkap ada di `PRD.md`. Ringkasan endpoint (`/api`):

* `GET /health`, `/options`, `/periods`
* `GET/POST /expenses`, `PUT/DELETE /expenses/{id}`
* `POST/GET/DELETE /expenses/{id}/photo`
* `GET /invoices?date=...`, `GET /invoices/{id}/photo`
* `GET /summary?period=`, `/trend?months=`
* `GET/POST /topups`
* `POST /budgets`, `PUT/DELETE /budgets/{name}`

Semua endpoint `POST` menerima header `Idempotency-Key` opsional (uuid) untuk mencegah duplikat.

---

## Build & Deploy Native (produksi)

Build & deploy hanya dari PC lokal (butuh RAM ~7GB untuk build native).

```bash
./build-native.sh       # build image native lokal (~6 menit)
./deploy-native.sh      # export -> scp -> VPS git pull -> docker load -> up -d -> prune dangling images
```

* Regenerasi native config saat menambah endpoint/model: `./backend/generate-native-config.sh`
* `deploy-native.sh` mengasumsikan SSH key `root@expense.frmnjn.my.id` sudah terdaftar.

---

## Project Structure

```
frontend/   # React (Vite)
backend/    # Spring Boot
  src/main/resources/db/migration/   # Flyway migration
notifier/   # Go microservice notifikasi email (SMTP)
scripts/    # backup, restore, seed
docker-compose.yml       # lokal (dengan mysql service)
docker-compose.prod.yml  # produksi (MySQL VPS)
uploads/                 # foto invoice (bind mount)
```
