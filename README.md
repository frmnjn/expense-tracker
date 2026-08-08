# Expense Tracker

Aplikasi web untuk mencatat pengeluaran harian, memantau saldo per budget, dan mengelola riwayat. Backend memakai **MySQL**.

---

## Fitur

* Catat pengeluaran (waktu, nama, budget, nominal, deskripsi) + **foto invoice opsional** (kamera/galeri)
* Saldo per budget (berkurang saat pengeluaran, bertambah via top-up)
* Dashboard: saldo per budget, pengeluaran terbesar, 3 bulan terakhir
* Riwayat per periode: search, filter, sort, edit, hapus, lihat foto
* Kelola budget (tambah/edit/hapus, soft delete)
* Dark mode (default gelap)

---

## Tech Stack

* **Frontend:** React 19 + Vite + TypeScript, React Router, TanStack Query, Axios, Mantine UI
* **Backend:** Java 25 + Spring Boot 4, Spring JDBC (JdbcTemplate), Flyway, GraalVM Native Image (produksi)
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
```

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
* `POST/GET /expenses/{id}/photo`
* `GET /summary?period=`, `/trend?months=`
* `GET/POST /topups`
* `POST /budgets`, `PUT/DELETE /budgets/{name}`

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
scripts/    # backup, restore, seed
docker-compose.yml       # lokal (dengan mysql service)
docker-compose.prod.yml  # produksi (MySQL VPS)
uploads/                 # foto invoice (bind mount)
```
