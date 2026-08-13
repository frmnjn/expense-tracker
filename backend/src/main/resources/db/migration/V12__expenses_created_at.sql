-- Menambahkan created_at untuk pengurutan deterministik.
-- Expense dari satu batch scan berbagi date_time yang sama, sehingga urutan
-- DATE_TIME saja tidak stabil. created_at memberi urutan sesuai urutan insert.

ALTER TABLE expenses ADD COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE expenses SET created_at = date_time WHERE created_at IS NULL;

CREATE INDEX idx_expenses_period_dt ON expenses (period, deleted, date_time, created_at);
