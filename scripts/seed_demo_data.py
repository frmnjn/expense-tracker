#!/usr/bin/env python3
"""Seed database demo dengan data generik (read-only public demo)."""

import argparse
import datetime
import os
import random
import subprocess
import sys
import uuid

MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']

BUDGETS = [
    # id, name, alert_threshold, description, categories
    (1, 'Household', 300_000, 'Kebutuhan rumah tangga', [
        (1, 'Utilities', 'Listrik, air, internet'),
        (2, 'Cleaning', 'Sabun, deterjen, kebersihan'),
        (3, 'Home Supplies', 'Peralatan dan kebutuhan rumah'),
    ]),
    (2, 'Groceries', 200_000, 'Belanja bahan makanan', [
        (4, 'Fresh Food', 'Sayur, buah, daging, telur'),
        (5, 'Snacks', 'Cemilan dan makanan ringan'),
        (6, 'Beverages', 'Kopi, teh, minuman'),
    ]),
    (3, 'Transport', 100_000, 'Transportasi harian', [
        (7, 'Fuel', 'Bensin dan energi kendaraan'),
        (8, 'Ride Hailing', 'Ojek dan taksi online'),
        (9, 'Parking', 'Parkir dan tol'),
    ]),
    (4, 'Entertainment', 100_000, 'Hiburan dan lifestyle', [
        (10, 'Dining Out', 'Makan di luar'),
        (11, 'Movies', 'Bioskop dan streaming'),
        (12, 'Hobbies', 'Hobi dan rekreasi'),
    ]),
    (5, 'Kids', 150_000, 'Kebutuhan anak', [
        (13, 'Diapers', 'Popok dan perlengkapan bayi'),
        (14, 'Milk', 'Susu dan makanan anak'),
        (15, 'Toys', 'Mainan dan buku anak'),
    ]),
    (6, 'Health', 100_000, 'Kesehatan dan vitamin', [
        (16, 'Pharmacy', 'Obat dan apotek'),
        (17, 'Vitamins', 'Vitamin dan suplemen'),
    ]),
]

ITEMS = {
    1: [(1, 'Token Listrik', 50_000, 250_000), (2, 'Sabun Cuci', 15_000, 60_000), (3, 'Tissue Rumah', 20_000, 70_000)],
    2: [(4, 'Sayur & Buah', 15_000, 70_000), (5, 'Cemilan', 10_000, 45_000), (6, 'Kopi Bubuk', 25_000, 90_000)],
    3: [(7, 'Bensin', 30_000, 120_000), (8, 'Ojek Online', 15_000, 60_000), (9, 'Parkir', 5_000, 25_000)],
    4: [(10, 'Makan di Luar', 35_000, 150_000), (11, 'Bioskop', 45_000, 100_000), (12, 'Buku & Hobi', 30_000, 120_000)],
    5: [(13, 'Popok', 40_000, 110_000), (14, 'Susu Anak', 55_000, 130_000), (15, 'Mainan Edukasi', 30_000, 120_000)],
    6: [(16, 'Obat Apotek', 20_000, 100_000), (17, 'Vitamin', 25_000, 90_000)],
}

TOPUP_DESCRIPTIONS = ['Alokasi bulanan', 'Top up budget', 'Tambahan saldo']


def period_of(d: datetime.date) -> str:
    y, m = d.year, d.month
    if d.day < 25:
        m -= 1
        if m == 0:
            m, y = 12, y - 1
    return f'{y}-{MONTHS[m - 1]}-{MONTHS[m % 12]}'


def period_start(d: datetime.date) -> str:
    y, m = d.year, d.month
    if d.day < 25:
        m -= 1
        if m == 0:
            m, y = 12, y - 1
    return f'{y}-{m:02d}-25'


def esc(value: str) -> str:
    return value.replace('\\', '\\\\').replace("'", "''")


def build_sql(days: int, seed: int) -> str:
    rng = random.Random(seed)
    today = datetime.date.today()
    topups = []
    expenses = []
    total_expense = {b[0]: 0 for b in BUDGETS}

    for budget_id, _name, _threshold, _desc, _categories in BUDGETS:
        for offset in (days + 3, 18):
            d = today - datetime.timedelta(days=offset)
            topups.append((uuid.uuid4().hex, f'{d:%Y-%m-%d} 08:00:00', budget_id,
                           rng.randint(1_000_000, 2_500_000), rng.choice(TOPUP_DESCRIPTIONS)))

    for offset in range(days, 0, -1):
        d = today - datetime.timedelta(days=offset)
        for _ in range(rng.randint(1, 3)):
            budget = rng.choice(BUDGETS)
            category_id, name, lo, hi = rng.choice(ITEMS[budget[0]])
            amount = rng.randint(lo, hi)
            hour = rng.randint(7, 21)
            minute = rng.choice([0, 10, 15, 30, 45])
            date_time = f'{d:%Y-%m-%d} {hour:02d}:{minute:02d}:00'
            expenses.append((uuid.uuid4().hex, period_of(d), period_start(d), date_time,
                             budget[0], category_id, name, amount, date_time))
            total_expense[budget[0]] += amount

    topup_total = {b[0]: 0 for b in BUDGETS}
    for topup in topups:
        topup_total[topup[2]] += topup[3]

    parts = [
        'SET FOREIGN_KEY_CHECKS=0;',
        'TRUNCATE TABLE voice_notes;',
        'TRUNCATE TABLE email_imports;',
        'TRUNCATE TABLE expenses;',
        'TRUNCATE TABLE invoices;',
        'TRUNCATE TABLE top_ups;',
        'TRUNCATE TABLE idempotency_keys;',
        'TRUNCATE TABLE categories;',
        'TRUNCATE TABLE budgets;',
        'SET FOREIGN_KEY_CHECKS=1;',
    ]

    budget_rows = []
    for budget_id, name, threshold, description, _categories in BUDGETS:
        balance = max(0, topup_total[budget_id] - total_expense[budget_id])
        budget_rows.append(
            f"({budget_id}, '{esc(name)}', {balance}, {threshold}, '{esc(description)}', 1)")
    parts.append('INSERT INTO budgets (id, name, balance, alert_threshold, description, is_active) VALUES '
                 + ', '.join(budget_rows) + ';')

    category_rows = []
    for _budget_id, _name, _threshold, _desc, categories in BUDGETS:
        for category_id, name, description in categories:
            category_rows.append(f"({category_id}, {_budget_id}, '{esc(name)}', '{esc(description)}', 1)")
    parts.append('INSERT INTO categories (id, budget_id, name, description, is_active) VALUES '
                 + ', '.join(category_rows) + ';')

    if topups:
        parts.append('INSERT INTO top_ups (id, date_time, budget_id, amount, description) VALUES '
                     + ', '.join(f"('{t[0]}', '{t[1]}', {t[2]}, {t[3]}, '{esc(t[4])}')" for t in topups) + ';')

    parts.append('INSERT INTO expenses (id, period, period_start, date_time, budget_id, category_id, name, amount, description, deleted, created_at) VALUES '
                 + ',\n'.join(f"('{e[0]}', '{e[1]}', '{e[2]}', '{e[3]}', {e[4]}, {e[5]}, '{esc(e[6])}', {e[7]}, 'Demo data', 0, '{e[8]}')" for e in expenses) + ';')
    return '\n'.join(parts)


def run_sql(container: str, db_user: str, db_password: str, db: str, sql: str) -> None:
    cmd = ['docker', 'exec', '-i', container, 'mysql', f'-u{db_user}', f'-p{db_password}', db]
    proc = subprocess.run(cmd, input=sql.encode(), capture_output=True)
    if proc.returncode != 0:
        print('Gagal menjalankan SQL:', file=sys.stderr)
        print(proc.stderr.decode(errors='ignore'), file=sys.stderr)
        sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description='Seed data demo generik ke database demo.')
    parser.add_argument('--container', default='wordpress-db-1')
    parser.add_argument('--db-user', default='expense_tracker_demo_user')
    parser.add_argument('--db-password', default=os.environ.get('DEMO_DB_PASSWORD') or os.environ.get('DB_PASSWORD'))
    parser.add_argument('--db', default='expense_tracker_demo')
    parser.add_argument('--days', type=int, default=75)
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--allow-non-demo', action='store_true')
    args = parser.parse_args()

    if not args.db_password:
        print('ERROR: DB password kosong (env DEMO_DB_PASSWORD/DB_PASSWORD atau --db-password).', file=sys.stderr)
        sys.exit(1)

    if not args.db.endswith('_demo') and not args.allow_non_demo:
        print('ERROR: script hanya untuk database berakhiran _demo.', file=sys.stderr)
        sys.exit(1)

    sql = build_sql(args.days, args.seed)
    if args.dry_run:
        print(sql)
        return
    run_sql(args.container, args.db_user, args.db_password, args.db, sql)
    print(f'==> Demo data seeded: {len(BUDGETS)} budgets, expenses {args.days} hari.')


if __name__ == '__main__':
    main()
