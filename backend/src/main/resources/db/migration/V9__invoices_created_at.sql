-- Menambah created_at pada invoices agar foto periode ini bisa diurutkan
-- terbaru (DESC) di frontend. Backfill untuk baris lama memakai waktu
-- expense terakhir yang memakai invoice tersebut (terbaik yang tersedia).

ALTER TABLE invoices ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE invoices i
SET created_at = (
    SELECT COALESCE(MAX(e.date_time), i.period_start)
    FROM expenses e
    WHERE e.invoice_id = i.id
);

CREATE INDEX idx_invoices_created_at ON invoices (created_at);
