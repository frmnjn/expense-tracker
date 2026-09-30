-- Category per budget (induk). Saldo tetap di budgets.
-- expenses.category_id NULL berarti "Uncategorized".

CREATE TABLE categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id   BIGINT NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(500) NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_categories_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT uk_categories_budget_name UNIQUE (budget_id, name)
);

CREATE INDEX idx_categories_budget ON categories (budget_id, is_active);

ALTER TABLE expenses ADD COLUMN category_id BIGINT NULL;
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_category FOREIGN KEY (category_id) REFERENCES categories(id);
CREATE INDEX idx_expenses_category ON expenses (category_id);

-- Saran category dari AI untuk transaksi hasil import email.
ALTER TABLE email_imports ADD COLUMN suggested_category VARCHAR(255) NULL;
