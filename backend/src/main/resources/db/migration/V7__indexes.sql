-- Menambahkan index yang belum ada.
-- expenses.invoice_id dipakai di InvoiceService.deleteIfUnused (COUNT by invoice_id).
-- Komposit (period, deleted) menggantikan idx_expenses_period untuk query daftar per periode.
-- idempotency_keys.created_at dipakai cleanup DELETE ... WHERE created_at < cutoff.

ALTER TABLE expenses
    DROP INDEX idx_expenses_period,
    ADD INDEX idx_expenses_period_deleted (period, deleted),
    ADD INDEX idx_expenses_invoice (invoice_id);

ALTER TABLE idempotency_keys
    ADD INDEX idx_idempotency_created_at (created_at);
