-- Menandai invoice yang dibuat melalui alur Scan AI (bukan foto biasa pada
-- form expense). Dipakai untuk memfilter daftar di halaman /scan.

ALTER TABLE invoices ADD COLUMN scan_flow BOOLEAN NOT NULL DEFAULT FALSE;
