-- Report PDF per periode yang sudah dibuat & dikirim (satu baris per periode).
-- Primary key mencegah report terkirim lebih dari sekali untuk periode yang sama.
CREATE TABLE period_reports (
    period       VARCHAR(64) PRIMARY KEY,
    file_name    VARCHAR(255) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    generated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
