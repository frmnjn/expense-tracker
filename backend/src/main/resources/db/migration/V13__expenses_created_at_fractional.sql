-- Naikkan presisi created_at ke mikrodetik agar expense dalam satu batch scan
-- (date_time identik, diinsert cepat berurutan) tetap dapat urutan deterministik
-- via created_at DESC, tanpa bertabrakan pada detik yang sama.

ALTER TABLE expenses MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
