-- Voice note: rekaman suara yang dianalisis AI menjadi satu expense.
-- Status: ANALYZING (menunggu AI), TO_REVIEW (AI selesai, perlu review),
--         SUBMITTED (expense dibuat), ERROR (analisis gagal, bisa retry).
-- reference_date adalah waktu lokal user saat rekam, dipakai AI untuk
-- menafsirkan tanggal/jam relatif ("hari ini", "kemarin", "jam 3 sore").

CREATE TABLE voice_notes (
    id             VARCHAR(64)  PRIMARY KEY,
    period         VARCHAR(64)  NOT NULL,
    period_start   DATE         NOT NULL,
    reference_date DATETIME     NOT NULL,
    file_path      VARCHAR(255) NOT NULL,
    mime_type      VARCHAR(100) NOT NULL,
    duration_ms    INT          NULL,
    transcript     TEXT         NULL,
    status         VARCHAR(32)  NOT NULL DEFAULT 'ANALYZING',
    analysis_json  LONGTEXT     NULL,
    error_message  VARCHAR(255) NULL,
    retry_count    INT          NOT NULL DEFAULT 0,
    retry_max      INT          NOT NULL DEFAULT 0,
    ai_provider    VARCHAR(20)  NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_voice_notes_period (period),
    INDEX idx_voice_notes_status (status)
);

-- Satu rekaman menghasilkan satu expense. Rekaman tetap disimpan agar bisa
-- diputar lagi dari halaman Riwayat.
ALTER TABLE expenses
    ADD COLUMN voice_note_id VARCHAR(64) NULL,
    ADD INDEX idx_expenses_voice_note (voice_note_id),
    ADD CONSTRAINT fk_expenses_voice_note
        FOREIGN KEY (voice_note_id) REFERENCES voice_notes(id);
