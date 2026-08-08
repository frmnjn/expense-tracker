-- Menambahkan kolom is_active ke budgets (untuk DB yang sudah ada tanpa kolom ini).
-- Idempotent: hanya menambah bila kolom belum ada.

DROP PROCEDURE IF EXISTS add_budget_is_active;

DELIMITER //
CREATE PROCEDURE add_budget_is_active()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'budgets' AND COLUMN_NAME = 'is_active'
    ) THEN
        ALTER TABLE budgets ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;
END //
DELIMITER ;

CALL add_budget_is_active();
DROP PROCEDURE add_budget_is_active;
