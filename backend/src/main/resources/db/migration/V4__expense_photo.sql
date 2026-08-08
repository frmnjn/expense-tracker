-- Menambahkan kolom photo_path ke expenses untuk foto invoice (opsional).

ALTER TABLE expenses ADD COLUMN photo_path VARCHAR(255) NULL;
