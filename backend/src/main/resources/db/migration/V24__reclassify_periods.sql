-- Hitung ulang periode semua data lama dengan aturan tanggal gajian baru:
-- tanggal 25, digeser mundur ke hari kerja sebelumnya bila Sabtu/Minggu.
-- Override tanggal merah belum ada saat migrasi ini; bila nanti ditambahkan,
-- aplikasi akan menghitung ulang data yang terdampak.

CREATE TEMPORARY TABLE tmp_period_dates (
    entity VARCHAR(16) NOT NULL,
    id     VARCHAR(64) NOT NULL,
    d      DATE        NOT NULL,
    INDEX idx_tmp_period_dates (entity, id)
);

INSERT INTO tmp_period_dates (entity, id, d)
SELECT 'expense', id, DATE(date_time) FROM expenses;

INSERT INTO tmp_period_dates (entity, id, d)
SELECT 'invoice', i.id, COALESCE(MAX(DATE(e.date_time)), DATE(i.created_at))
FROM invoices i
LEFT JOIN expenses e ON e.invoice_id = i.id
GROUP BY i.id, i.created_at;

INSERT INTO tmp_period_dates (entity, id, d)
SELECT 'voice', id, DATE(reference_date) FROM voice_notes;

CREATE TEMPORARY TABLE tmp_period_assignments (
    entity       VARCHAR(16) NOT NULL,
    id           VARCHAR(64) NOT NULL,
    period       VARCHAR(64) NOT NULL,
    period_start DATE        NOT NULL,
    INDEX idx_tmp_period_assignments (entity, id)
);

INSERT INTO tmp_period_assignments (entity, id, period, period_start)
SELECT entity, id,
       CONCAT(
           YEAR(pm), '-',
           ELT(MONTH(pm), 'JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN',
                          'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'), '-',
           ELT(MONTH(DATE_ADD(pm, INTERVAL 1 MONTH)),
                          'JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN',
                          'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC')),
       ps
FROM (
    SELECT entity, id,
           CASE WHEN d >= cur_payday THEN nom_cur ELSE nom_prev END AS pm,
           CASE WHEN d >= cur_payday THEN cur_payday ELSE prev_payday END AS ps
    FROM (
        SELECT entity, id, d, nom_cur, nom_prev,
               DATE_SUB(nom_cur, INTERVAL (CASE WEEKDAY(nom_cur)
                   WHEN 5 THEN 1 WHEN 6 THEN 2 ELSE 0 END) DAY) AS cur_payday,
               DATE_SUB(nom_prev, INTERVAL (CASE WEEKDAY(nom_prev)
                   WHEN 5 THEN 1 WHEN 6 THEN 2 ELSE 0 END) DAY) AS prev_payday
        FROM (
            SELECT entity, id, d,
                   DATE_ADD(DATE_SUB(d, INTERVAL (DAYOFMONTH(d) - 1) DAY), INTERVAL 24 DAY) AS nom_cur,
                   DATE_SUB(DATE_ADD(DATE_SUB(d, INTERVAL (DAYOFMONTH(d) - 1) DAY), INTERVAL 24 DAY),
                            INTERVAL 1 MONTH) AS nom_prev
            FROM tmp_period_dates
        ) nominal
    ) adjusted
) assignment;

UPDATE expenses e
JOIN tmp_period_assignments a ON a.entity = 'expense' AND a.id = e.id
SET e.period = a.period, e.period_start = a.period_start;

UPDATE invoices i
JOIN tmp_period_assignments a ON a.entity = 'invoice' AND a.id = i.id
SET i.period = a.period, i.period_start = a.period_start;

UPDATE voice_notes v
JOIN tmp_period_assignments a ON a.entity = 'voice' AND a.id = v.id
SET v.period = a.period, v.period_start = a.period_start;

DROP TEMPORARY TABLE tmp_period_assignments;
DROP TEMPORARY TABLE tmp_period_dates;
