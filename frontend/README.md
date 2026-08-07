# Expense Tracker Frontend

Frontend React untuk aplikasi pencatatan pengeluaran.

## Tech Stack

- React 19
- Vite
- TypeScript
- Mantine UI
- TanStack Query
- Axios

## Struktur

```text
src/
├── components/
├── pages/
├── services/   (API client)
├── hooks/
├── types/
└── utils/
```

## API & Proxy

Frontend memanggil backend via Axios dengan base URL `/api` (relatif). Di Docker, nginx (frontend image) me-proxy `/api` → `backend:8080`.

Config: `VITE_API_URL` (default `/api`).
- Untuk Docker: `/api` (diprox y nginx, bisa diakses dari HP).
- Untuk dev lokal Vite: `http://localhost:8080`.

## Docker

Image frontend berisi nginx yang:
- Menyajikan build statis.
- Me-proxy `/api` ke backend.
- Serve `public/sw.js` & manifest untuk PWA.

Dockerfile: `frontend/Dockerfile`

## Development Lokal

```bash
npm install
VITE_API_URL=http://localhost:8080 npm run dev
```
