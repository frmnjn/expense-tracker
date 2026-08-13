-- Normalisasi casing status invoice ke UPPERCASE. Status alur scan memakai enum
-- (ANALYZING/TO_REVIEW/SUBMITTED/ERROR), sedangkan default V10 adalah 'submitted'
-- (lowercase) untuk invoice foto biasa — membuat status tidak konsisten.

UPDATE invoices SET status = UPPER(status);

ALTER TABLE invoices MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED';
