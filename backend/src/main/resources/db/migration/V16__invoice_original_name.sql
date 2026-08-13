-- Menyimpan nama file asli saat upload invoice (gambar/PDF) agar bisa
-- ditampilkan di UI. Kosong untuk invoice lama.

ALTER TABLE invoices ADD COLUMN original_name VARCHAR(255) NULL;
