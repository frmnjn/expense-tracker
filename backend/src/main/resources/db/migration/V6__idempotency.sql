-- Menyimpan hasil POST untuk idempotency via header Idempotency-Key.
-- Baris dibersihkan otomatis oleh IdempotencyService saat save (older than TTL).

CREATE TABLE idempotency_keys (
    id_key        VARCHAR(64) PRIMARY KEY,
    response_json TEXT        NOT NULL,
    created_at    DATETIME    NOT NULL
);
