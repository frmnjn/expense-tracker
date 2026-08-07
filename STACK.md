# STACK.md

## Frontend

- React 19
- Vite
- TypeScript
- Mantine UI
- TanStack Query
- Axios

## Backend

- Java 25 (LTS)
- Spring Boot 4
- Maven
- Google Sheets API
- GraalVM 25 Native Image (produksi; native executable bukan JVM)

## Build Native Image (produksi)

- Dockerfile: `backend/Dockerfile.native` (multi-stage: temurin build jar → GraalVM AOT/native → runtime distroless)
- Refleksi Google Sheets: `backend/native-config/reachability-metadata.json` (via tracing agent)
- Script build: `build-native.sh` (lokal)
- Script deploy: `deploy-native.sh` (save→scp→load ke VPS)
- Generate config native: `backend/generate-native-config.sh`

## Dev

- Docker
- Docker Compose

## Rules

- Tidak boleh menggunakan Next.js
- Tidak boleh menggunakan Redux
- Tidak boleh menggunakan Tailwind
- Tidak boleh menggunakan database
- Tidak boleh menggunakan ORM