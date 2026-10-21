# AI Model Log

Riwayat model yang dipakai untuk **analisa struk** (`/scan`), **import email** (`/inbox`),
dan **voice note** (`/voice`) melalui Gemini API via HTTP.

> Konfigurasi ada di `backend/.env` di VPS (file ini tidak di-version control /
> gitignored):
> * `AI_MODEL` — scan struk & fallback parse email.
> * `AI_VOICE_MODEL` — analisis rekaman suara.
>
> Default di `application.yml`: `AI_MODEL=gemini-3.5-flash-lite`,
> `AI_VOICE_MODEL=gemini-3.5-flash-lite`.

| Tanggal | Model | Catatan |
|---|---|---|
| 2026-08-13 | gemini-3.5-flash-lite | Default awal sejak fitur scan diperkenalkan (commit `7a804d9`). |
| 2026-09-10 | gemini-3.8-flash | Upgrade dari lite ke Flash penuh terbaru demi akurasi OCR (lite salah baca angka PB1 struk: 15.478 dibaca 16.478). |
| 2026-09-10 | gemini-3.8-flash → gemini-3.7-flash | `gemini-3.8-flash` sempat gagal (HTTP 503 → scan gagal). Turun ke `gemini-3.7-flash` yang terverifikasi tersedia (HTTP 200). |
| 2026-09-10 | gemini-3.7-flash → gemini-3.8-flash | `gemini-3.7-flash` juga ternyata error transien (gagal lalu sukses setelah diulang). Kembali ke `gemini-3.8-flash` + diaktifkan auto-retry untuk menutupi error sesaat. |
| 2026-09-16 | gemini-3.8-flash → gemini-3.6-flash | `gemini-3.8-flash` kena kuota free tier (limit 20 req) → HTTP 429 terus-menerus; auto-retry 50× sia-sia. `gemini-3.6-flash` terverifikasi HTTP 200. |
| 2026-09-29 | + fallback ke `deepseek-flash` | Gemini 429 (kuota free tier limit 5) & 503 (high demand) terus-menerus. Tambah fallback: setelah 50× Gemini gagal → DeepSeek `deepseek-flash` (vision, OpenAI-compatible) sekali. Jeda retry menghormati `Retry-After`; PDF dirender ke PNG untuk DeepSeek. |
| 2026-10-21 | Voice `gemini-3.5-flash-lite` | Fitur voice note pertama kali memakai `AI_MODEL=gemini-3.6-flash`. Muncul error intermiten `503` (high demand) lalu `429` (rate limit); retry 50× terlalu agresif. Dua rekaman akhirnya tetap sampai `TO_REVIEW`. |
| 2026-10-21 | Gemini `402 RESOURCE_EXHAUSTED` | Prepayment credits Gemini habis: *“Your prepayment credits are depleted”*. Request teks sederhana juga kena 402 (bukan masalah audio/prompt). Rekaman berstatus `ERROR`. Solusi: top up saldo di AI Studio → Billing/Prepay. Setelah top up, request teks & audio kembali HTTP 200. |
| 2026-10-21 | Scan `gemini-3.8-flash`, voice `gemini-3.5-flash-lite` | Pisah model setelah saldo diisi. Scan/inbox kembali ke `gemini-3.8-flash` demi akurasi OCR; voice memakai `AI_VOICE_MODEL=gemini-3.5-flash-lite` yang lebih murah karena hasil selalu direview. Retry rekaman `c0d87502...` sukses `TO_REVIEW` dengan retry 0. |
