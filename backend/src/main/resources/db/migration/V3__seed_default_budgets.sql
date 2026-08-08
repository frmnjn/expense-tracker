-- Default budgets untuk development lokal (saldo 0; isi lewat top-up atau scripts/seed_budgets.py).
-- INSERT IGNORE: aman untuk produksi (nama yang sudah ada dilewati).

INSERT IGNORE INTO budgets (name, balance, is_active) VALUES
    ('Household', 0, TRUE),
    ('Alana', 0, TRUE),
    ('Playing', 0, TRUE),
    ('Maintenance Kendaraan', 0, TRUE),
    ('Pajak', 0, TRUE),
    ('IPL', 0, TRUE),
    ('Jalan-Jalan', 0, TRUE);
