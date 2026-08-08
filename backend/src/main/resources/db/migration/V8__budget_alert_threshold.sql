-- Ambang notifikasi "budget menipis" per budget. Nilai 0 = nonaktif (skip).
-- Di-set manual via DB, tanpa UI.

ALTER TABLE budgets ADD COLUMN alert_threshold BIGINT NOT NULL DEFAULT 0;
