-- Menambahkan kolom status analisis AI pada invoices.
-- Status: ANALYZING (menunggu AI), TO_REVIEW (AI selesai, perlu review),
--         SUBMITTED (expense dibuat), ERROR (analisis gagal, bisa retry).
-- Default 'submitted' agar invoice lama / foto biasa (bukan alur AI) tidak tampil
-- sebagai "menunggu AI".

ALTER TABLE invoices ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'submitted';
ALTER TABLE invoices ADD COLUMN analysis_json LONGTEXT NULL;
ALTER TABLE invoices ADD COLUMN error_message VARCHAR(255) NULL;

CREATE INDEX idx_invoices_status ON invoices (status);
