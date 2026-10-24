-- Override tanggal gajian per periode (mis. akibat tanggal merah atau gajian maju).
-- Bila tidak ada baris untuk suatu periode, dipakai tanggal 25 yang digeser
-- mundur ke hari kerja sebelumnya bila jatuh pada Sabtu/Minggu.
CREATE TABLE payday_overrides (
    period       VARCHAR(64) PRIMARY KEY,
    payday_date  DATE        NOT NULL,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
