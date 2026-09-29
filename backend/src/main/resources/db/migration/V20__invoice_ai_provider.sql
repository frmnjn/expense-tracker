-- Menyimpan provider AI yang dipakai untuk analisa invoice (GEMINI / DEEPSEEK).
-- Dipakai agar frontend bisa menampilkan provider pada progres retry.
-- Counter retry di-reset saat berpindah provider (lihat ai_provider).

ALTER TABLE invoices ADD COLUMN ai_provider VARCHAR(20) NULL;
