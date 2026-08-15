#!/usr/bin/env python3
"""
seed_dummy_data.py

Men-generate data dummy LANGSUNG ke database (tanpa REST API): membuat 5 budget
(Household, Alana, Papa, Playing, Mama) lalu mencatat expense acak beberapa hari.

Secara default script me-RESET tabel data (expenses, invoices, top_ups,
idempotency_keys, budgets) sebelum mengisi ulang — jadi hasilnya seperti database
baru berisi data dummy.

Mengeksekusi SQL lewat `docker exec <container> mysql`. Kredensial & container
bisa disesuaikan lewat argumen (untuk VPS pakai wordpress-db-1 + kredensial VPS).

Contoh:
  Lokal (default)        : python3 scripts/seed_dummy_data.py
  VPS                    : python3 scripts/seed_dummy_data.py --container wordpress-db-1 --db-user expense-tracker --db-password 'Passw3ird123'
  Hanya lihat SQL        : python3 scripts/seed_dummy_data.py --dry-run
  Tanpa reset (append)   : python3 scripts/seed_dummy_data.py --no-reset
"""

import argparse
import datetime
import random
import subprocess
import sys
import uuid

MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']

BUDGETS = [
    {"id": 1, "name": "Household", "balance": 3_000_000, "alertThreshold": 500_000, "items": [
        ("Sembako", 50_000, 350_000),
        ("Token Listrik", 100_000, 300_000),
        ("Gas LPG", 22_000, 25_000),
        ("PDAM", 40_000, 80_000),
        ("Internet", 300_000, 300_000),
        ("Sabun & Deterjen", 15_000, 60_000),
        ("Sayur & Buah", 10_000, 50_000),
        ("Daging Ayam", 25_000, 70_000),
        ("Bumbu Dapur", 5_000, 30_000),
    ]},
    {"id": 2, "name": "Alana", "balance": 2_000_000, "alertThreshold": 300_000, "items": [
        ("Popok", 40_000, 90_000),
        ("Susu Anak", 60_000, 120_000),
        ("Skincare Anak", 25_000, 80_000),
        ("Jajan Alana", 5_000, 25_000),
        ("Vitamin Anak", 20_000, 60_000),
        ("Mainan", 15_000, 80_000),
    ]},
    {"id": 3, "name": "Papa", "balance": 1_500_000, "alertThreshold": 200_000, "items": [
        ("Kopi", 10_000, 30_000),
        ("Jajan Papa", 5_000, 25_000),
        ("Bensin", 50_000, 100_000),
        ("Cemilan", 10_000, 40_000),
        ("Pulsa", 25_000, 50_000),
        ("Buku", 20_000, 90_000),
    ]},
    {"id": 4, "name": "Playing", "balance": 2_000_000, "alertThreshold": 300_000, "items": [
        ("Makan Enak", 50_000, 250_000),
        ("Jajan Mall", 30_000, 120_000),
        ("Jalan-jalan", 20_000, 150_000),
        ("Bioskop", 50_000, 100_000),
        ("Coffee Date", 40_000, 100_000),
        ("Parkir & Tol", 10_000, 50_000),
    ]},
    {"id": 5, "name": "Mama", "balance": 1_500_000, "alertThreshold": 250_000, "items": [
        ("Skincare Mama", 30_000, 150_000),
        ("Makeup", 40_000, 200_000),
        ("Jajan Mama", 10_000, 40_000),
        ("Baju", 50_000, 200_000),
        ("Vitamin", 20_000, 80_000),
        ("Kopi", 15_000, 40_000),
    ]},
]


def period_of(d: datetime.date) -> str:
    y, m = d.year, d.month
    if d.day < 25:
        m -= 1
        if m == 0:
            m, y = 12, y - 1
    return f"{y}-{MONTHS[m - 1]}-{MONTHS[m % 12]}"


def period_start(d: datetime.date) -> str:
    y, m = d.year, d.month
    if d.day < 25:
        m -= 1
        if m == 0:
            m, y = 12, y - 1
    return f"{y}-{m:02d}-25"


def esc(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def build_sql(days: int, seed: int, reset: bool) -> str:
    rng = random.Random(seed)
    today = datetime.date.today()
    parts = []
    if reset:
        parts += [
            "SET FOREIGN_KEY_CHECKS=0;",
            "TRUNCATE TABLE expenses;",
            "TRUNCATE TABLE invoices;",
            "TRUNCATE TABLE top_ups;",
            "TRUNCATE TABLE idempotency_keys;",
            "TRUNCATE TABLE budgets;",
            "SET FOREIGN_KEY_CHECKS=1;",
        ]

    budget_rows = ", ".join(
        f"({b['id']}, '{esc(b['name'])}', {b['balance']}, {b['alertThreshold']}, 1)" for b in BUDGETS
    )
    parts.append(
        "INSERT INTO budgets (id, name, balance, alert_threshold, is_active) VALUES "
        + budget_rows
        + ";"
    )

    items_by_budget = {b["name"]: b["items"] for b in BUDGETS}
    id_by_budget = {b["name"]: b["id"] for b in BUDGETS}
    expense_rows = []
    for offset in range(days, 0, -1):
        d = today - datetime.timedelta(days=offset)
        for _ in range(rng.randint(0, 3)):
            budget = rng.choice(list(items_by_budget.keys()))
            name, lo, hi = rng.choice(items_by_budget[budget])
            amount = rng.randint(lo, hi)
            hour = rng.randint(7, 21)
            minute = rng.choice([0, 15, 30, 45])
            date_time = f"{d:%Y-%m-%d} {hour:02d}:{minute:02d}:00"
            expense_rows.append(
                f"('{uuid.uuid4().hex}', '{period_of(d)}', '{period_start(d)}', "
                f"'{date_time}', {id_by_budget[budget]}, '{esc(name)}', {amount}, '', 0, '{date_time}')"
            )

    parts.append(
        "INSERT INTO expenses (id, period, period_start, date_time, budget_id, name, amount, description, deleted, created_at) VALUES "
        + ",\n".join(expense_rows)
        + ";"
    )

    return "\n".join(parts)


def run_sql(container: str, db_user: str, db_password: str, db: str, sql: str) -> None:
    cmd = ["docker", "exec", "-i", container, "mysql", f"-u{db_user}", f"-p{db_password}", db]
    proc = subprocess.run(cmd, input=sql.encode(), capture_output=True)
    if proc.returncode != 0:
        print("Gagal menjalankan SQL:", file=sys.stderr)
        print(proc.stderr.decode(), file=sys.stderr)
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="Seed dummy data langsung ke database.")
    parser.add_argument("--container", default="expensetracker-mysql-1", help="Nama container MySQL (VPS: wordpress-db-1)")
    parser.add_argument("--db-user", default="expense", help="User MySQL (VPS: expense-tracker)")
    parser.add_argument("--db-password", default="expense-pass", help="Password MySQL (VPS: sesuai .env)")
    parser.add_argument("--db", default="expense_tracker", help="Nama database")
    parser.add_argument("--days", type=int, default=75, help="Berapa hari ke belakang expense dibuat (default 75 ≈ 3 periode)")
    parser.add_argument("--seed", type=int, default=42, help="Seed random agar reproducible (default 42)")
    parser.add_argument("--dry-run", action="store_true", help="Hanya cetak SQL, tidak eksekusi")
    parser.add_argument("--no-reset", action="store_true", help="Jangan reset tabel sebelum mengisi")
    args = parser.parse_args()

    print(f"==> Target DB: {args.db} (container {args.container})")

    sql = build_sql(args.days, args.seed, reset=not args.no_reset)

    if args.dry_run:
        print(sql)
        return

    run_sql(args.container, args.db_user, args.db_password, args.db, sql)
    print(f"==> Selesai. Budget: {len(BUDGETS)}, expense dummy dimasukkan.")


if __name__ == "__main__":
    main()
