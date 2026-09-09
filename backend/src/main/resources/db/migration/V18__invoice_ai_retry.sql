-- Menambahkan kolom retry untuk analisa AI invoice.
-- retry_count: percobaan gagal yang sudah dilakukan; retry_max: batas percobaan.
-- Dipakai agar frontend bisa menampilkan progres retry (mis. "Retry 3/50").

ALTER TABLE invoices ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN retry_max INT NOT NULL DEFAULT 0;
