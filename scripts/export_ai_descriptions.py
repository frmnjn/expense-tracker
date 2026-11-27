#!/usr/bin/env python3
"""Export deskripsi budget & kategori aktif yang dipakai prompt AI ke CSV.

Sumber data: MySQL expense_tracker (container default wordpress-db-1).
Kredensial dibaca dari backend/.env (DB_USER, DB_PASSWORD).

Contoh:
    python3 scripts/export_ai_descriptions.py
    python3 scripts/export_ai_descriptions.py --output /tmp/ai_desc.csv
"""

import argparse
import csv
import json
import os
import subprocess
import sys
from datetime import datetime
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_DIR = PROJECT_ROOT / 'exports'


def load_env(path: Path) -> dict:
    env = {}
    try:
        for line in path.read_text(encoding='utf-8').splitlines():
            line = line.strip()
            if line and '=' in line and not line.startswith('#'):
                key, value = line.split('=', 1)
                env[key] = value
    except FileNotFoundError:
        pass
    return env


def fetch_rows(container: str, db_name: str, db_user: str, db_password: str) -> list[dict]:
    query = (
        "SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT("
        "'type', t.type, 'name', t.name, 'parent', t.parent, 'description', t.description"
        ")), JSON_ARRAY()) FROM ("
        "SELECT 'budget' AS type, name, CAST(NULL AS CHAR) AS parent, description "
        "FROM budgets WHERE is_active = TRUE "
        "UNION ALL "
        "SELECT 'category', c.name, b.name, c.description "
        "FROM categories c JOIN budgets b ON b.id = c.budget_id "
        "WHERE c.is_active = TRUE AND b.is_active = TRUE"
        ") t"
    )
    result = subprocess.run(
        [
            'docker', 'exec', container,
            'mysql', '-N', '-B', f'-u{db_user}', f'-p{db_password}', db_name,
            '-e', query,
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        message = result.stderr.strip() or result.stdout.strip() or 'mysql gagal dijalankan'
        raise RuntimeError(message)

    raw = result.stdout.strip()
    if not raw:
        return []
    data = json.loads(raw)
    if data is None:
        return []
    return data


def sorted_rows(rows: list[dict]) -> list[tuple[str, str, str, str]]:
    budgets = sorted(
        (row for row in rows if row.get('type') == 'budget'),
        key=lambda row: (row.get('name') or '').lower(),
    )
    categories_by_parent: dict[str, list[dict]] = {}
    for row in rows:
        if row.get('type') != 'category':
            continue
        parent = row.get('parent') or ''
        categories_by_parent.setdefault(parent, []).append(row)

    result = []
    for budget in budgets:
        name = budget.get('name') or ''
        result.append(('budget', name, '', budget.get('description') or ''))
        categories = sorted(
            categories_by_parent.pop(name, []),
            key=lambda row: (row.get('name') or '').lower(),
        )
        for category in categories:
            result.append(('category', category.get('name') or '', name, category.get('description') or ''))

    for parent, categories in sorted(categories_by_parent.items()):
        for category in sorted(categories, key=lambda row: (row.get('name') or '').lower()):
            result.append(('category', category.get('name') or '', parent, category.get('description') or ''))

    return result


def main() -> int:
    env = load_env(PROJECT_ROOT / 'backend' / '.env')
    parser = argparse.ArgumentParser(description='Export deskripsi budget & kategori AI ke CSV.')
    parser.add_argument(
        '--output',
        default=str(DEFAULT_OUTPUT_DIR / f'ai_descriptions_{datetime.now():%Y%m%d_%H%M%S}.csv'),
        help='Path file CSV output (default: exports/ai_descriptions_<timestamp>.csv)',
    )
    parser.add_argument(
        '--container',
        default=os.environ.get('MYSQL_CONTAINER') or 'wordpress-db-1',
        help='Nama container MySQL (default: wordpress-db-1)',
    )
    parser.add_argument('--db-name', default='expense_tracker', help='Nama database (default: expense_tracker)')
    args = parser.parse_args()

    db_user = env.get('DB_USER', '')
    db_password = env.get('DB_PASSWORD', '')
    if not db_user or not db_password:
        print('ERROR: DB_USER/DB_PASSWORD tidak ditemukan di backend/.env', file=sys.stderr)
        return 1

    try:
        rows = fetch_rows(args.container, args.db_name, db_user, db_password)
    except Exception as error:
        print(f'ERROR: {error}', file=sys.stderr)
        return 1

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('w', newline='', encoding='utf-8') as handle:
        writer = csv.writer(handle)
        writer.writerow(['type', 'name', 'parent_budget', 'description'])
        writer.writerows(sorted_rows(rows))

    budget_count = sum(1 for row in rows if row.get('type') == 'budget')
    category_count = sum(1 for row in rows if row.get('type') == 'category')
    described = sum(1 for row in rows if (row.get('description') or '').strip())
    print(f'CSV: {output}')
    print(f'  budget={budget_count} kategori={category_count} punya deskripsi={described}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
