# AI Model Log

Riwayat model yang dipakai untuk **analisa struk** pada fitur **Scan Struk dengan AI**
(Gemini API via HTTP, dikonfigurasi lewat `backend/.env` → `AI_MODEL`).

> Konfigurasi ada di `backend/.env` di VPS (file ini tidak di-version control /
> gitignored). Default di `application.yml` = `gemini-3.5-flash-lite`.

| Tanggal | Model | Catatan |
|---|---|---|
| 2026-08-13 | gemini-3.5-flash-lite | Default awal sejak fitur scan diperkenalkan (commit `7a804d9`). |
| 2026-09-10 | gemini-3.8-flash | Upgrade dari lite ke Flash penuh terbaru demi akurasi OCR (lite salah baca angka PB1 struk: 15.478 dibaca 16.478). |
| 2026-09-10 | gemini-3.8-flash → gemini-3.7-flash | `gemini-3.8-flash` ternyata belum aktif untuk generateContent (HTTP 503 → scan gagal). Turun ke `gemini-3.7-flash` yang terverifikasi tersedia (HTTP 200). |
