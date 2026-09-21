-- Antrian transaksi hasil import email bank (BCA / D-Bank) yang dibaca via IMAP.
-- Dedup memakai message_id (Message-ID email) supaya email yang sama tidak
-- diimport dua kali meski di-poll berulang. status:
-- PENDING_REVIEW (menunggu konfirmasi user), IMPORTED, DISCARDED, FAILED.

CREATE TABLE email_imports (
    id               VARCHAR(64)  PRIMARY KEY,
    message_id       VARCHAR(512) NOT NULL,
    sender           VARCHAR(320) NOT NULL,
    subject          VARCHAR(512) NOT NULL,
    received_at      DATETIME(3)  NOT NULL,
    transaction_at   DATETIME(3)  NULL,
    merchant         VARCHAR(255) NULL,
    amount           BIGINT       NULL,
    description      TEXT         NULL,
    suggested_budget VARCHAR(255) NULL,
    parse_method     VARCHAR(16)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    error_message    VARCHAR(512) NULL,
    expense_id       VARCHAR(64)  NULL,
    created_at       DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_email_imports_message_id (message_id),
    INDEX idx_email_imports_status (status, created_at)
);
