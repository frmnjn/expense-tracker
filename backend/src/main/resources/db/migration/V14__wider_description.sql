-- Besarkan deskripsi expense/top_up. Deskripsi auto-generate dari hasil scan
-- (daftar item per budget) bisa lebih panjang dari 255.

ALTER TABLE expenses MODIFY COLUMN description TEXT NULL;
ALTER TABLE top_ups MODIFY COLUMN description TEXT NULL;
