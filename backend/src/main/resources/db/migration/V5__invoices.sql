-- Memperkenalkan tabel invoices sebagai pemilik file foto yang bisa dirujuk
-- oleh banyak expense. Foto lama dipindah dari expenses.photo_path ke invoice.
-- File di disk tidak diubah namanya (photo_path lama tetap valid).

CREATE TABLE invoices (
    id           VARCHAR(64) PRIMARY KEY,
    period       VARCHAR(64) NOT NULL,
    period_start DATE        NOT NULL,
    photo_path   VARCHAR(255) NOT NULL,
    deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    INDEX idx_invoices_period (period)
);

ALTER TABLE expenses ADD COLUMN invoice_id VARCHAR(64) NULL;

INSERT INTO invoices (id, period, period_start, photo_path, deleted)
SELECT CONCAT('inv-', id), period, period_start, photo_path, FALSE
FROM expenses
WHERE photo_path IS NOT NULL AND photo_path <> '';

UPDATE expenses e
JOIN invoices i ON i.id = CONCAT('inv-', e.id)
SET e.invoice_id = i.id;

ALTER TABLE expenses DROP COLUMN photo_path;
