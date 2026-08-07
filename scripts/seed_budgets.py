#!/usr/bin/env python3
"""Seed budgets (name + balance) from the Budget tab of the Google Sheet into MySQL.

Usage (prints INSERT SQL to stdout; pipe into mysql):
    python3 scripts/seed_budgets.py > seed.sql
    mysql -u<user> -p expense_tracker < seed.sql

Requires env (or backend/.env):
    GOOGLE_SHEET_ID, GOOGLE_BUDGET_SHEET (default Budget), and
    backend/credentials.json (service account).
"""
import base64
import json
import time
import urllib.request
import urllib.parse

from cryptography.hazmat.primitives.serialization import load_pem_private_key
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes


def load_env(path="backend/.env"):
    env = {}
    try:
        for line in open(path):
            line = line.strip()
            if line and "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                env[k] = v
    except FileNotFoundError:
        pass
    return env


def token(cred):
    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT"}
    claims = {
        "iss": cred["client_email"],
        "scope": "https://www.googleapis.com/auth/spreadsheets",
        "aud": "https://oauth2.googleapis.com/token",
        "iat": now,
        "exp": now + 3600,
    }

    def b64(o):
        return base64.urlsafe_b64encode(json.dumps(o, separators=(",", ":")).encode()).rstrip(b"=").decode()

    signing_input = b64(header) + "." + b64(claims)
    pk = load_pem_private_key(cred["private_key"].encode(), password=None)
    sig = pk.sign(signing_input.encode(), padding.PKCS1v15(), hashes.SHA256())
    jwt = signing_input + "." + base64.urlsafe_b64encode(sig).rstrip(b"=").decode()
    body = urllib.parse.urlencode(
        {"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": jwt}).encode()
    req = urllib.request.Request("https://oauth2.googleapis.com/token", data=body)
    return json.load(urllib.request.urlopen(req))["access_token"]


def main():
    env = load_env()
    sheet_id = env.get("GOOGLE_SHEET_ID")
    budget_sheet = env.get("GOOGLE_BUDGET_SHEET", "Budget")
    cred = json.load(open("backend/credentials.json"))
    tok = token(cred)
    url = f"https://sheets.googleapis.com/v4/spreadsheets/{sheet_id}/values/{budget_sheet}!A:B"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {tok}"})
    rows = json.load(urllib.request.urlopen(req)).get("values", [])

    values = []
    for row in rows[1:]:
        if not row or not row[0].strip():
            continue
        name = row[0].strip().replace("'", "''")
        raw = row[1].strip() if len(row) > 1 else "0"
        try:
            bal = int(raw.replace(",", "").replace(".", "").replace("Rp", "").replace(" ", ""))
        except ValueError:
            bal = 0
        values.append(f"('{name}', {bal})")

    if not values:
        print("-- no budgets found")
        return
    print("INSERT INTO budgets (name, balance) VALUES")
    print(",\n".join(values) + ";")


if __name__ == "__main__":
    main()
