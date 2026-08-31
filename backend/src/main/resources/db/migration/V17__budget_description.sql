-- Menambahkan deskripsi budget untuk menjelaskan kegunaan tiap budget
-- (dipakai juga oleh AI saat menyarankan budget pada analisis struk).

ALTER TABLE budgets ADD COLUMN description VARCHAR(500) NULL;
