-- Local dev bootstrap: creates schema + seeds default budgets.
-- Runs once on first MySQL init (empty data dir). Backend schema.sql is idempotent (no-op after this).

CREATE TABLE IF NOT EXISTS budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS expenses (
    id VARCHAR(64) PRIMARY KEY,
    period VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    date_time DATETIME NOT NULL,
    budget_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount BIGINT NOT NULL,
    description VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_expenses_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    INDEX idx_expenses_period (period),
    INDEX idx_expenses_deleted (deleted)
);

CREATE TABLE IF NOT EXISTS top_ups (
    id VARCHAR(64) PRIMARY KEY,
    date_time DATETIME NOT NULL,
    budget_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    description VARCHAR(255),
    CONSTRAINT fk_top_ups_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    INDEX idx_top_ups_budget (budget_id)
);

-- Default budgets (saldo 0; isi lewat top-up atau jalankan scripts/seed_budgets.py untuk nilai asli)
INSERT IGNORE INTO budgets (name, balance) VALUES
    ('Household', 0),
    ('Alana', 0),
    ('Playing', 0),
    ('Maintenance Kendaraan', 0),
    ('Pajak', 0),
    ('IPL', 0),
    ('Jalan-Jalan', 0);
