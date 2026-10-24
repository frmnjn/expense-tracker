#!/usr/bin/env python3
"""Generate data demo statis (JSON) tanpa database.

Output default: frontend/public/demo/data.json
Data tanggal mengikuti tanggal script dijalankan (TZ Asia/Jakarta).
"""

import argparse
import datetime
import json
import random
import uuid
from pathlib import Path

MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT = PROJECT_ROOT / 'frontend' / 'public' / 'demo' / 'data.json'

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
    'inv-demo-review': ('demo-receipt-review.png', 'Demo Mart', 'TO_REVIEW'),
    'inv-demo-submitted': ('demo-receipt-submitted.png', 'Demo Fresh Market', 'SUBMITTED'),
    'inv-demo-not-invoice': ('demo-not-invoice.png', 'Demo Mart', 'NOT_INVOICE'),
}

VOICE_NOTES = [
    ('voice-demo-review', 'demo-voice-review.webm', 14417, '23:51:00', 'TO_REVIEW',
     'Tadi di kantor, gua jajan di FamilyMart beli bakpau sama siomay harganya totalnya dua puluh ribu sekitar jam setengah empat sore.',
     {'storeName': 'FamilyMart', 'dateTimeSuffix': '15:30',
      'items': [('Bakpau dan Siomay', 20000, 'Groceries', 'Snacks')]}, None),
    ('voice-demo-wartek', 'demo-voice-wartek.webm', 16459, '23:49:00', 'SUBMITTED',
     'tadi beli makan di wartek depan pake orek tempe ayam goreng sama perkedel satu harganya Rp18.000 jam ya 07.00 malam lah',
     {'storeName': 'Wartek', 'dateTimeSuffix': '19:00',
      'items': [('Makan di wartek', 18000, 'Entertainment', 'Dining Out')]}, None),
    ('voice-demo-household', 'demo-voice-household.webm', 5683, '21:44:00', 'SUBMITTED',
     'jam 5 sore 10.000 bayar baal',
     {'storeName': '', 'dateTimeSuffix': '17:00',
      'items': [('Baal', 10000, 'Household', 'Cleaning')]}, None),
    ('voice-demo-not-expense', 'demo-voice-not-expense.webm', 4159, '20:51:00', 'NOT_EXPENSE',
     None, None, 'Rekaman bukan catatan pengeluaran'),
]


def ym_add(year: int, month: int, delta: int) -> tuple[int, int]:
    index = year * 12 + (month - 1) + delta
    return index // 12, index % 12 + 1


def payday(year: int, month: int) -> datetime.date:
    year, month = ym_add(year, month, 0)
    day = datetime.date(year, month, 25)
    while day.weekday() >= 5:
        day -= datetime.timedelta(days=1)
    return day


def period_name(year: int, month: int) -> str:
    year, month = ym_add(year, month, 0)
    return f'{year}-{MONTHS[month - 1]}-{MONTHS[month % 12]}'


def parse_period(period: str) -> tuple[int, int]:
    year, month_name, _next = period.split('-')
    return int(year), MONTHS.index(month_name) + 1


def period_month(value: datetime.date) -> tuple[int, int]:
    if value >= payday(value.year, value.month):
        return value.year, value.month
    return ym_add(value.year, value.month, -1)


def period_of(value: datetime.date) -> str:
    return period_name(*period_month(value))


def period_start_of(period: str) -> datetime.date:
    return payday(*parse_period(period))


def period_end_of(period: str) -> datetime.date:
    year, month = parse_period(period)
    return payday(*ym_add(year, month, 1)) - datetime.timedelta(days=1)


def iso_date(value: datetime.date) -> str:
    return value.isoformat()


def iso_date_time(value: datetime.date, clock: str) -> str:
    return f'{value:%Y-%m-%d} {clock}'


def invoice_analysis(store: str, date_time: str, items: list[tuple[str, int, str, str]]) -> dict:
    return {
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


def build_data(days: int, seed: int) -> dict:
    rng = random.Random(seed)
    today = datetime.date.today()
    current = period_of(today)

    budget_names = {budget[0]: budget[1] for budget in BUDGETS}
    category_info = {
        category_id: (category_name, budget[0])
        for budget in BUDGETS
        for category_id, category_name, _description in budget[4]
    }

    raw_expenses = []
    raw_topups = []

    for budget_id, _name, _threshold, _description, _categories in BUDGETS:
        for offset in (days + 3, 18):
            value = today - datetime.timedelta(days=offset)
            raw_topups.append({
                'id': uuid.uuid4().hex,
                'date': value,
                'dateTime': f'{value:%Y-%m-%d} 08:00:00',
                'budget': budget_names[budget_id],
                'amount': rng.randint(1_000_000, 2_500_000),
                'description': rng.choice(TOPUP_DESCRIPTIONS),
            })

    for offset in range(days, 0, -1):
        value = today - datetime.timedelta(days=offset)
        for _ in range(rng.randint(1, 3)):
            budget = rng.choice(BUDGETS)
            category_id, name, low, high = rng.choice(ITEMS[budget[0]])
            amount = rng.randint(low, high)
            hour = rng.randint(7, 21)
            minute = rng.choice([0, 10, 15, 30, 45])
            date_time = f'{value:%Y-%m-%d} {hour:02d}:{minute:02d}:00'
            raw_expenses.append({
                'id': uuid.uuid4().hex,
                'period': period_of(value),
                'dateTime': date_time,
                'budget': budget_names[budget[0]],
                'category': category_info[category_id][0],
                'name': name,
                'amount': amount,
                'description': 'Demo data',
                'invoiceId': None,
                'voiceNoteId': None,
            })

    submitted_date = today - datetime.timedelta(days=1)
    fixed_expenses = [
        ('exp-demo-submitted-groceries', submitted_date, 2, 4, 'Demo Fresh Market', 90_000,
         'Sayur, buah, dan susu', 'inv-demo-submitted', None, '10:00:00'),
        ('exp-demo-submitted-household', submitted_date, 1, 2, 'Demo Fresh Market', 60_000,
         'Sabun dan tissue rumah', 'inv-demo-submitted', None, '10:00:00'),
        ('exp-demo-email-imported', today - datetime.timedelta(days=2), 1, 3, 'Demo Grocery', 125_000,
         'Transaksi kartu kredit', None, None, '14:30:00'),
        ('exp-demo-voice-wartek', today, 4, 10, 'Wartek', 18_000,
         'Makan di wartek Rp18.000', None, 'voice-demo-wartek', '19:00:00'),
        ('exp-demo-voice-household', today, 1, 2, 'Bayar Mbak Anik', 10_000,
         'Bayar Mbak Anik Rp10.000', None, 'voice-demo-household', '17:00:00'),
    ]
    for expense_id, value, budget_id, category_id, name, amount, description, invoice_id, voice_note_id, clock in fixed_expenses:
        raw_expenses.append({
            'id': expense_id,
            'period': period_of(value),
            'dateTime': iso_date_time(value, clock),
            'budget': budget_names[budget_id],
            'category': category_info[category_id][0],
            'name': name,
            'amount': amount,
            'description': description,
            'invoiceId': invoice_id,
            'voiceNoteId': voice_note_id,
        })

    expenses_by_period: dict[str, list[dict]] = {}
    for expense in raw_expenses:
        expenses_by_period.setdefault(expense['period'], []).append(to_expense(expense))
    for expenses in expenses_by_period.values():
        expenses.sort(key=lambda item: item['dateTime'], reverse=True)

    total_expense = {budget[0]: 0 for budget in BUDGETS}
    for expense in raw_expenses:
        for budget_id, name in budget_names.items():
            if expense['budget'] == name:
                total_expense[budget_id] += expense['amount']
                break

    topup_total = {budget[0]: 0 for budget in BUDGETS}
    for topup in raw_topups:
        for budget_id, name in budget_names.items():
            if topup['budget'] == name:
                topup_total[budget_id] += topup['amount']
                break

    options = {
        'budgets': [
            {
                'name': name,
                'balance': max(0, topup_total[budget_id] - total_expense[budget_id]),
                'alertThreshold': threshold,
                'description': description,
                'categories': [
                    {'id': category_id, 'name': category_name, 'description': category_description}
                    for category_id, category_name, category_description in categories
                ],
            }
            for budget_id, name, threshold, description, categories in BUDGETS
        ]
    }

    periods_with_expenses = sorted(expenses_by_period.keys(), key=period_start_of, reverse=True)
    periods = [current] + [period for period in periods_with_expenses if period != current]

    calendar_months = set()
    calendar_months.add(ym_add(today.year, today.month, -1))
    for index in range(0, 7):
        calendar_months.add(ym_add(today.year, today.month, index))
    for period in periods:
        year, month = parse_period(period)
        calendar_months.add((year, month))
        calendar_months.add(ym_add(year, month, -1))
    calendar = []
    for year, month in sorted(calendar_months):
        name = period_name(year, month)
        calendar.append({
            'period': name,
            'start': iso_date(payday(year, month)),
            'end': iso_date(payday(*ym_add(year, month, 1))),
            'overridden': False,
        })

    summary_by_period = {
        period: build_summary(period, expenses)
        for period, expenses in expenses_by_period.items()
    }

    topups = sorted(
        ({
            'id': topup['id'],
            'dateTime': topup['dateTime'],
            'budget': topup['budget'],
            'amount': topup['amount'],
            'description': topup['description'],
        } for topup in raw_topups),
        key=lambda item: item['dateTime'],
        reverse=True,
    )

    invoices, invoice_details = build_invoices(today, raw_expenses)
    voice_notes, voice_note_details = build_voice_notes(today, raw_expenses)
    email_imports = build_email_imports(today, submitted_date)
    report_inputs = build_report_inputs(current, raw_expenses, raw_topups, options)

    return {
        'generatedAt': datetime.datetime.now().astimezone().isoformat(timespec='seconds'),
        'currentPeriod': current,
        'periods': periods,
        'calendar': calendar,
        'options': options,
        'expensesByPeriod': expenses_by_period,
        'summaryByPeriod': summary_by_period,
        'topUps': topups,
        'invoices': invoices,
        'invoiceDetails': invoice_details,
        'voiceNotes': voice_notes,
        'voiceNoteDetails': voice_note_details,
        'emailImports': email_imports,
        'reportsInput': report_inputs,
    }


def to_expense(expense: dict) -> dict:
    result = {
        'id': expense['id'],
        'dateTime': expense['dateTime'],
        'name': expense['name'],
        'budget': expense['budget'],
        'category': expense['category'],
        'amount': expense['amount'],
        'description': expense['description'],
        'hasPhoto': expense['invoiceId'] is not None,
    }
    if expense['invoiceId']:
        result['photoType'] = 'image'
        result['photoName'] = 'demo-receipt-submitted.png'
    if expense['voiceNoteId']:
        result['voiceNoteId'] = expense['voiceNoteId']
    return result


def build_summary(period: str, expenses: list[dict]) -> dict:
    total = sum(expense['amount'] for expense in expenses)
    by_budget: dict[str, dict] = {}
    for expense in expenses:
        entry = by_budget.setdefault(expense['budget'], {'amount': 0, 'count': 0, 'categories': {}})
        entry['amount'] += expense['amount']
        entry['count'] += 1
        category = expense.get('category') or 'Uncategorized'
        category_entry = entry['categories'].setdefault(category, {'amount': 0, 'count': 0})
        category_entry['amount'] += expense['amount']
        category_entry['count'] += 1
    return {
        'period': period,
        'total': total,
        'count': len(expenses),
        'byBudget': [
            {
                'budget': name,
                'amount': entry['amount'],
                'count': entry['count'],
                'categories': [
                    {'category': category, 'amount': value['amount'], 'count': value['count']}
                    for category, value in sorted(entry['categories'].items(), key=lambda item: item[1]['amount'], reverse=True)
                ],
            }
            for name, entry in sorted(by_budget.items(), key=lambda item: item[1]['amount'], reverse=True)
        ],
    }


def build_invoices(today: datetime.date, raw_expenses: list[dict]) -> tuple[list[dict], dict]:
    review_date = f'{today:%Y-%m-%d} 09:15:00'
    submitted_date = today - datetime.timedelta(days=1)
    submitted_time = f'{submitted_date:%Y-%m-%d} 10:00:00'
    not_invoice_date = f'{today - datetime.timedelta(days=2):%Y-%m-%d} 16:20:00'

    review_analysis = invoice_analysis('Demo Mart', review_date, [
        ('Sayur & Buah', 25_000, 'Groceries', 'Fresh Food'),
        ('Susu', 30_000, 'Groceries', 'Beverages'),
        ('Tissue', 32_500, 'Household', 'Home Supplies'),
    ])
    submitted_analysis = invoice_analysis('Demo Fresh Market', submitted_time, [
        ('Sayur & Buah', 50_000, 'Groceries', 'Fresh Food'),
        ('Susu', 40_000, 'Groceries', 'Beverages'),
        ('Sabun & Tissue', 60_000, 'Household', 'Cleaning'),
    ])

    invoices = [
        {
            'id': 'inv-demo-review',
            'period': period_of(today),
            'createdAt': review_date,
            'status': 'TO_REVIEW',
            'type': 'image',
            'name': 'demo-receipt-review.png',
            'retryCount': 0,
            'retryMax': 0,
            'provider': 'GEMINI',
        },
        {
            'id': 'inv-demo-submitted',
            'period': period_of(submitted_date),
            'createdAt': submitted_time,
            'status': 'SUBMITTED',
            'type': 'image',
            'name': 'demo-receipt-submitted.png',
            'retryCount': 0,
            'retryMax': 0,
            'provider': 'GEMINI',
        },
        {
            'id': 'inv-demo-not-invoice',
            'period': period_of(today - datetime.timedelta(days=2)),
            'createdAt': not_invoice_date,
            'status': 'NOT_INVOICE',
            'type': 'image',
            'name': 'demo-not-invoice.png',
            'retryCount': 0,
            'retryMax': 0,
            'provider': None,
        },
    ]
    invoices.sort(key=lambda item: item['createdAt'], reverse=True)

    submitted_expenses = [
        to_expense(expense)
        for expense in raw_expenses
        if expense['invoiceId'] == 'inv-demo-submitted'
    ]

    invoice_details = {
        'inv-demo-review': {
            'id': 'inv-demo-review',
            'type': 'image',
            'status': 'TO_REVIEW',
            'name': 'demo-receipt-review.png',
            'analysis': review_analysis,
            'retryCount': 0,
            'retryMax': 0,
            'provider': 'GEMINI',
        },
        'inv-demo-submitted': {
            'id': 'inv-demo-submitted',
            'type': 'image',
            'status': 'SUBMITTED',
            'name': 'demo-receipt-submitted.png',
            'analysis': submitted_analysis,
            'retryCount': 0,
            'retryMax': 0,
            'provider': 'GEMINI',
            'expenses': submitted_expenses,
        },
        'inv-demo-not-invoice': {
            'id': 'inv-demo-not-invoice',
            'type': 'image',
            'status': 'NOT_INVOICE',
            'errorMessage': 'Bukan struk invoice',
            'name': 'demo-not-invoice.png',
            'retryCount': 0,
            'retryMax': 0,
        },
    }
    return invoices, invoice_details


def build_voice_notes(today: datetime.date, raw_expenses: list[dict]) -> tuple[list[dict], dict]:
    voice_notes = []
    details = {}
    for note_id, file_name, duration, clock, status, transcript, analysis, error in VOICE_NOTES:
        created_at = f'{today:%Y-%m-%d} {clock}'
        voice_notes.append({
            'id': note_id,
            'period': period_of(today),
            'createdAt': created_at,
            'status': status,
            'durationMs': duration,
            'retryCount': 0,
            'retryMax': 50,
            'provider': 'GEMINI',
        })
        detail = {
            'id': note_id,
            'status': status,
            'durationMs': duration,
            'retryCount': 0,
            'retryMax': 50,
            'provider': 'GEMINI',
        }
        if analysis:
            detail['analysis'] = {
                'transcript': transcript,
                'isExpense': True,
                'storeName': analysis['storeName'],
                'dateTime': f'{today:%Y-%m-%d} {analysis["dateTimeSuffix"]}',
                'items': [
                    {
                        'name': name,
                        'amount': amount,
                        'suggestedBudget': budget,
                        'suggestedCategory': category,
                    }
                    for name, amount, budget, category in analysis['items']
                ],
            }
        if error:
            detail['errorMessage'] = error
        if status == 'SUBMITTED':
            detail['expenses'] = [
                to_expense(expense)
                for expense in raw_expenses
                if expense['voiceNoteId'] == note_id
            ]
        details[note_id] = detail
    voice_notes.sort(key=lambda item: item['createdAt'], reverse=True)
    return voice_notes, details


def build_email_imports(today: datetime.date, submitted_date: datetime.date) -> list[dict]:
    rows = [
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
    imports = []
    for row in rows:
        (import_id, sender, subject_sender, subject, received_at, transaction_at, merchant, amount,
         description, suggested_budget, suggested_category, parse_method, status, error_message, expense_id) = row
        imports.append({
            'id': import_id,
            'sender': sender,
            'subject': subject,
            'receivedAt': received_at,
            'transactionAt': transaction_at,
            'merchant': merchant,
            'amount': amount,
            'description': description,
            'suggestedBudget': suggested_budget,
            'suggestedCategory': suggested_category,
            'parseMethod': parse_method,
            'status': status,
            'errorMessage': error_message,
            'expenseId': expense_id,
        })
    imports.sort(key=lambda item: item['receivedAt'], reverse=True)
    return imports


def build_report_inputs(current: str, raw_expenses: list[dict], raw_topups: list[dict], options: dict) -> list[dict]:
    completed = sorted(
        {expense['period'] for expense in raw_expenses if expense['period'] != current},
        key=period_start_of,
        reverse=True,
    )[:3]
    inputs = []
    for period in completed:
        start = period_start_of(period)
        end = period_end_of(period)
        expenses = [
            {
                'id': expense['id'],
                'period': expense['period'],
                'dateTime': expense['dateTime'],
                'name': expense['name'],
                'budgetName': expense['budget'],
                'amount': expense['amount'],
                'description': expense['description'],
                'deleted': False,
                'hasPhoto': expense['invoiceId'] is not None,
                'invoiceId': expense['invoiceId'],
                'photoType': 'image' if expense['invoiceId'] else None,
                'photoName': 'demo-receipt-submitted.png' if expense['invoiceId'] else None,
                'categoryName': expense['category'],
                'voiceNoteId': expense['voiceNoteId'],
            }
            for expense in sorted(raw_expenses, key=lambda item: item['dateTime'], reverse=True)
            if expense['period'] == period
        ]
        topups = [
            {
                'id': topup['id'],
                'dateTime': topup['dateTime'],
                'budget': topup['budget'],
                'amount': topup['amount'],
                'description': topup['description'],
            }
            for topup in raw_topups
            if start <= topup['date'] <= end
        ]
        inputs.append({
            'period': period,
            'startDate': iso_date(start),
            'endDate': iso_date(end),
            'expenses': expenses,
            'topUps': topups,
            'budgets': options['budgets'],
            'databaseExpenseTotal': sum(expense['amount'] for expense in expenses),
            'databaseTransactionCount': len(expenses),
        })
    return inputs


def main() -> None:
    parser = argparse.ArgumentParser(description='Generate data demo statis (JSON).')
    parser.add_argument('--output', default=str(DEFAULT_OUTPUT))
    parser.add_argument('--days', type=int, default=75)
    parser.add_argument('--seed', type=int, default=42)
    args = parser.parse_args()

    data = build_data(args.days, args.seed)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    print(f'Demo data generated: {output}')
    print(f'  periods={len(data["periods"])} expenses={sum(len(v) for v in data["expensesByPeriod"].values())} '
          f'reports={len(data["reportsInput"])}')


if __name__ == '__main__':
    main()
