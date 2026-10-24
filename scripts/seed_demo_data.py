#!/usr/bin/env python3
"""Seed database demo dengan data generik (read-only public demo)."""

import argparse
import datetime
import json
import os
import random
import shutil
import subprocess
import sys
import uuid
from pathlib import Path

MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']
PROJECT_ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = Path(__file__).resolve().parent / 'demo-assets'
UPLOAD_DIR = PROJECT_ROOT / 'uploads-demo'

BUDGETS = [
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
INVOICE_ASSETS = {
    'inv-demo-review': ('demo-receipt-review.png', 'demo_receipt_review.png'),
    'inv-demo-submitted': ('demo-receipt-submitted.png', 'demo_receipt_submitted.png'),
    'inv-demo-not-invoice': ('demo-not-invoice.png', 'demo_not_invoice.png'),
}
VOICE_ASSETS = [
    'demo-voice-review.webm',
    'demo-voice-wartek.webm',
    'demo-voice-household.webm',
    'demo-voice-not-expense.webm',
]


def payday(year: int, month: int) -> datetime.date:
    day = datetime.date(year, month, 25)
    while day.weekday() >= 5:
        day -= datetime.timedelta(days=1)
    return day


def period_month(d: datetime.date) -> tuple[int, int]:
    if d >= payday(d.year, d.month):
        return d.year, d.month
    if d.month == 1:
        return d.year - 1, 12
    return d.year, d.month - 1


def period_of(d: datetime.date) -> str:
    y, m = period_month(d)
    return f'{y}-{MONTHS[m - 1]}-{MONTHS[m % 12]}'


def period_start(d: datetime.date) -> str:
    y, m = period_month(d)
    return payday(y, m).isoformat()


def esc(value: str) -> str:
    return value.replace('\\', '\\\\').replace("'", "''")


def copy_demo_assets() -> None:
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    for source_name, _target_name in INVOICE_ASSETS.values():
        source = ASSETS_DIR / source_name
        if not source.exists():
            raise SystemExit(f'Asset demo tidak ditemukan: {source}')
        shutil.copyfile(source, UPLOAD_DIR / source_name)
    for voice_name in VOICE_ASSETS:
        source = ASSETS_DIR / voice_name
        if not source.exists():
            raise SystemExit(f'Asset demo tidak ditemukan: {source}')
        shutil.copyfile(source, UPLOAD_DIR / voice_name)


def invoice_analysis(store: str, date_time: str, items: list[tuple[str, int, str, str]]) -> str:
    payload = {
        'storeName': store,
        'total': sum(item[1] for item in items),
        'dateTime': date_time,
        'currency': 'IDR',
        'originalTotal': None,
        'items': [
            {
                'name': name,
                'amount': amount,
                'suggestedBudget': budget,
                'suggestedCategory': category,
            }
            for name, amount, budget, category in items
        ],
    }
    return json.dumps(payload, ensure_ascii=False)


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
                             budget[0], category_id, name, amount, date_time, None, None))
            total_expense[budget[0]] += amount

    submitted_date = today - datetime.timedelta(days=1)
    submitted_time = f'{submitted_date:%Y-%m-%d} 10:00:00'
    fixed_expenses = [
        ('exp-demo-submitted-groceries', submitted_date, 2, 4, 'Demo Fresh Market', 90_000,
         'Sayur, buah, dan susu', 'inv-demo-submitted', None),
        ('exp-demo-submitted-household', submitted_date, 1, 2, 'Demo Fresh Market', 60_000,
         'Sabun dan tissue rumah', 'inv-demo-submitted', None),
        ('exp-demo-email-imported', today - datetime.timedelta(days=2), 1, 3, 'Demo Grocery', 125_000,
         'Transaksi kartu kredit', None, None),
        ('exp-demo-voice-wartek', today, 4, 10, 'Wartek', 18_000,
         'Makan di wartek Rp18.000', None, 'voice-demo-wartek'),
        ('exp-demo-voice-household', today, 1, 2, 'Bayar Mbak Anik', 10_000,
         'Bayar Mbak Anik Rp10.000', None, 'voice-demo-household'),
    ]
    for expense_id, date_value, budget_id, category_id, name, amount, description, invoice_id, voice_note_id in fixed_expenses:
        if invoice_id:
            date_time = f'{date_value:%Y-%m-%d} 10:00:00'
        elif voice_note_id == 'voice-demo-wartek':
            date_time = f'{date_value:%Y-%m-%d} 19:00:00'
        elif voice_note_id == 'voice-demo-household':
            date_time = f'{date_value:%Y-%m-%d} 17:00:00'
        else:
            date_time = f'{date_value:%Y-%m-%d} 14:30:00'
        expenses.append((expense_id, period_of(date_value), period_start(date_value), date_time,
                         budget_id, category_id, name, amount, date_time, invoice_id, voice_note_id))
        total_expense[budget_id] += amount

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
    for budget_id, _name, _threshold, _desc, categories in BUDGETS:
        for category_id, name, description in categories:
            category_rows.append(f"({category_id}, {budget_id}, '{esc(name)}', '{esc(description)}', 1)")
    parts.append('INSERT INTO categories (id, budget_id, name, description, is_active) VALUES '
                 + ', '.join(category_rows) + ';')

    if topups:
        parts.append('INSERT INTO top_ups (id, date_time, budget_id, amount, description) VALUES '
                     + ', '.join(f"('{t[0]}', '{t[1]}', {t[2]}, {t[3]}, '{esc(t[4])}')" for t in topups) + ';')

    review_date = f'{today:%Y-%m-%d} 09:15:00'
    submitted_invoice_date = f'{submitted_date:%Y-%m-%d} 10:00:00'
    not_invoice_date = f'{today - datetime.timedelta(days=2):%Y-%m-%d} 16:20:00'
    review_analysis = invoice_analysis('Demo Mart', review_date, [
        ('Sayur & Buah', 25_000, 'Groceries', 'Fresh Food'),
        ('Susu', 30_000, 'Groceries', 'Beverages'),
        ('Tissue', 32_500, 'Household', 'Home Supplies'),
    ])
    submitted_analysis = invoice_analysis('Demo Fresh Market', submitted_invoice_date, [
        ('Sayur & Buah', 50_000, 'Groceries', 'Fresh Food'),
        ('Susu', 40_000, 'Groceries', 'Beverages'),
        ('Sabun & Tissue', 60_000, 'Household', 'Cleaning'),
    ])
    invoice_rows = [
        f"('inv-demo-review', '{period_of(today)}', '{period_start(today)}', 'demo-receipt-review.png', 0, "
        f"'TO_REVIEW', '{esc(review_analysis)}', NULL, 1, '{review_date}', 'demo-receipt-review.png', 0, 0, 'GEMINI')",
        f"('inv-demo-submitted', '{period_of(submitted_date)}', '{period_start(submitted_date)}', "
        f"'demo-receipt-submitted.png', 0, 'SUBMITTED', '{esc(submitted_analysis)}', NULL, 1, "
        f"'{submitted_invoice_date}', 'demo-receipt-submitted.png', 0, 0, 'GEMINI')",
        f"('inv-demo-not-invoice', '{period_of(today - datetime.timedelta(days=2))}', "
        f"'{period_start(today - datetime.timedelta(days=2))}', 'demo-not-invoice.png', 0, "
        f"'NOT_INVOICE', NULL, 'Bukan struk invoice', 1, '{not_invoice_date}', 'demo-not-invoice.png', 0, 0, NULL)",
    ]
    parts.append('INSERT INTO invoices (id, period, period_start, photo_path, deleted, status, analysis_json, '
                 'error_message, scan_flow, created_at, original_name, retry_count, retry_max, ai_provider) VALUES '
                 + ',\n'.join(invoice_rows) + ';')

    voice_analyses = {
        'voice-demo-review': json.dumps({
            'transcript': 'Tadi di kantor, gua jajan di FamilyMart beli bakpau sama siomay harganya totalnya dua puluh ribu sekitar jam setengah empat sore.',
            'isExpense': True,
            'storeName': 'FamilyMart',
            'dateTime': f'{today:%Y-%m-%d} 15:30',
            'items': [{'name': 'Bakpau dan Siomay', 'amount': 20000,
                       'suggestedBudget': 'Groceries', 'suggestedCategory': 'Snacks'}],
        }, ensure_ascii=False),
        'voice-demo-wartek': json.dumps({
            'transcript': 'tadi beli makan di wartek depan pake orek tempe ayam goreng sama perkedel satu harganya Rp18.000 jam ya 07.00 malam lah',
            'isExpense': True,
            'storeName': 'Wartek',
            'dateTime': f'{today:%Y-%m-%d} 19:00',
            'items': [{'name': 'Makan di wartek', 'amount': 18000,
                       'suggestedBudget': 'Entertainment', 'suggestedCategory': 'Dining Out'}],
        }, ensure_ascii=False),
        'voice-demo-household': json.dumps({
            'transcript': 'jam 5 sore 10.000 bayar baal',
            'isExpense': True,
            'storeName': '',
            'dateTime': f'{today:%Y-%m-%d} 17:00',
            'items': [{'name': 'Baal', 'amount': 10000,
                       'suggestedBudget': 'Household', 'suggestedCategory': 'Cleaning'}],
        }, ensure_ascii=False),
    }
    voice_rows = [
        ('voice-demo-review', 'demo-voice-review.webm', '14417', '23:51:00',
         'Tadi di kantor, gua jajan di FamilyMart beli bakpau sama siomay harganya totalnya dua puluh ribu sekitar jam setengah empat sore.',
         'TO_REVIEW', voice_analyses['voice-demo-review'], None),
        ('voice-demo-wartek', 'demo-voice-wartek.webm', '16459', '23:49:00',
         'tadi beli makan di wartek depan pake orek tempe ayam goreng sama perkedel satu harganya Rp18.000 jam ya 07.00 malam lah',
         'SUBMITTED', voice_analyses['voice-demo-wartek'], None),
        ('voice-demo-household', 'demo-voice-household.webm', '5683', '21:44:00',
         'jam 5 sore 10.000 bayar baal',
         'SUBMITTED', voice_analyses['voice-demo-household'], None),
        ('voice-demo-not-expense', 'demo-voice-not-expense.webm', '4159', '20:51:00',
         None, 'NOT_EXPENSE', None, 'Rekaman bukan catatan pengeluaran'),
    ]
    voice_values = []
    for note_id, file_name, duration, clock, transcript, status, analysis, error in voice_rows:
        def qv(value):
            return 'NULL' if value is None else "'" + esc(str(value)) + "'"
        voice_values.append(
            f"('{note_id}', '{period_of(today)}', '{period_start(today)}', "
            f"'{today:%Y-%m-%d} {clock}', '{file_name}', 'audio/webm', {duration}, "
            f"{qv(transcript)}, '{status}', {qv(analysis)}, {qv(error)}, 0, 50, 'GEMINI', "
            f"'{today:%Y-%m-%d} {clock}')")
    parts.append('INSERT INTO voice_notes (id, period, period_start, reference_date, file_path, mime_type, '
                 'duration_ms, transcript, status, analysis_json, error_message, retry_count, retry_max, '
                 'ai_provider, created_at) VALUES ' + ',\n'.join(voice_values) + ';')

    parts.append('INSERT INTO expenses (id, period, period_start, date_time, budget_id, category_id, name, amount, description, deleted, created_at, invoice_id, voice_note_id) VALUES '
                 + ',\n'.join(f"('{e[0]}', '{e[1]}', '{e[2]}', '{e[3]}', {e[4]}, {e[5]}, '{esc(e[6])}', {e[7]}, 'Demo data', 0, '{e[8]}', {('NULL' if e[9] is None else repr(e[9]))}, {('NULL' if e[10] is None else repr(e[10]))})" for e in expenses) + ';')

    email_rows = [
        ('email-demo-qris', '<demo-qris@demo-bank.example>', 'notifikasi@demo-bank.example',
         'Pembayaran QRIS Berhasil', f'{today:%Y-%m-%d} 08:10:00', f'{today:%Y-%m-%d} 08:00:00',
         'Demo Coffee', 35_000, 'QRIS - DEMO COFFEE', 'Entertainment', 'Dining Out', 'REGEX',
         'PENDING_REVIEW', None, None),
        ('email-demo-cc', '<demo-cc@demo-bank.example>', 'notifikasi@demo-bank.example',
         'Transaksi Kartu Kredit', f'{today - datetime.timedelta(days=2):%Y-%m-%d} 14:31:00',
         f'{today - datetime.timedelta(days=2):%Y-%m-%d} 14:30:00', 'Demo Grocery', 125_000,
         'Pembayaran kartu kredit', 'Household', 'Home Supplies', 'REGEX', 'IMPORTED', None,
         'exp-demo-email-imported'),
        ('email-demo-promo', '<demo-promo@demo-bank.example>', 'promo@demo-bank.example',
         'Promo spesial bulan ini', f'{today - datetime.timedelta(days=3):%Y-%m-%d} 09:00:00', None,
         'Demo Bank', None, 'Promo cashback dan diskon', None, None, 'REGEX', 'DISCARDED',
         'Bukan transaksi pengeluaran', None),
        ('email-demo-failed', '<demo-failed@demo-bank.example>', 'notifikasi@demo-bank.example',
         'Notifikasi transaksi tidak dikenali', f'{today - datetime.timedelta(days=4):%Y-%m-%d} 11:30:00',
         None, None, None, 'Email tidak dapat dibaca', None, None, 'AI', 'FAILED',
         'Format email tidak dikenali', None),
        ('email-demo-ereceipt', '<demo-ereceipt@demo-mart.example>', 'do-not-reply@demo-mart.example',
         'E-Receipt Demo Mart', f'{submitted_date:%Y-%m-%d} 11:00:00', f'{submitted_date:%Y-%m-%d} 10:30:00',
         'Demo Mart', 87_500, 'Struk digital dikirim ke Scan', 'Groceries', 'Fresh Food', 'SCAN',
         'SCANNED', None, None),
    ]
    values = []
    for row in email_rows:
        (email_id, message_id, sender, subject, received_at, transaction_at, merchant, amount,
         description, suggested_budget, suggested_category, parse_method, status, error_message, expense_id) = row
        def q(value):
            return 'NULL' if value is None else "'" + esc(str(value)) + "'"
        values.append(
            f"('{email_id}', '{message_id}', '{sender}', '{esc(subject)}', '{received_at}', "
            f"{q(transaction_at)}, {q(merchant)}, {('NULL' if amount is None else amount)}, {q(description)}, "
            f"{q(suggested_budget)}, {q(suggested_category)}, '{parse_method}', '{status}', {q(error_message)}, "
            f"{q(expense_id)}, '{received_at}')")
    parts.append('INSERT INTO email_imports (id, message_id, sender, subject, received_at, transaction_at, '
                 'merchant, amount, description, suggested_budget, suggested_category, parse_method, status, '
                 'error_message, expense_id, created_at) VALUES ' + ',\n'.join(values) + ';')
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
    copy_demo_assets()
    run_sql(args.container, args.db_user, args.db_password, args.db, sql)
    print(f'==> Demo data seeded: {len(BUDGETS)} budgets, {len(INVOICE_ASSETS)} sample invoices, {len(VOICE_ASSETS)} sample voice notes, 5 sample emails.')


if __name__ == '__main__':
    main()
