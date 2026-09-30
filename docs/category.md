# Spec: Category per Budget (2 tingkat, saldo di induk)

> Dokumen ini adalah spesifikasi implementasi untuk fitur **category**.
> Dibuat agar sesi AI berikutnya bisa mengerjakan tanpa perlu mengulang riset.
> Baca juga `AGENTS.md` (aturan kode) dan `PRD.md` (konteks produk).

## 1. Ringkasan

Saat ini `budget` berperan ganda sebagai kategori. Fitur ini menambahkan
**category** di bawah tiap budget, misalnya:

```
Household Makan
 ├── Groceries
 ├── Eating Out
 ├── Coffee
 └── Milk
Household Other
 ├── Toiletries
 ├── Cleaning
 ├── Kitchen
 ├── Home Supplies
 └── Maintenance
```

Istilah: **budget = induk**, **category = anak**.

## 2. Keputusan final (sudah disepakati)

1. **Hierarki 1 level**: budget (induk) → category. Tidak ada nesting lebih dalam.
2. **Category = entitas terpisah** (tabel `categories`), BUKAN baris di `budgets`.
3. **Saldo/anggaran tetap di induk** (`budgets.balance`). Category hanya **klasifikasi**; anggaran tidak dipecah.
4. **Category opsional**. Bila tidak dipilih, expense dianggap **"Uncategorized"**.
5. **`NULL` = Uncategorized** — bukan baris khusus. Tidak ada backfill; expense lama otomatis Uncategorized.
6. **Nama category unik per budget** (`UNIQUE(budget_id, name)`); boleh sama antar budget berbeda.
7. **Tiap category punya `description`** (dipakai AI untuk memilih category yang tepat).
8. **AI (scan & inbox)** menyarankan `budget` (induk) + `category` (boleh kosong; dilarang "uncategorized").
9. **Laporan**: total tetap per budget; ada **breakdown/drill-down per category** (termasuk bucket Uncategorized).
10. **Dropdown expense: pilih budget dulu, lalu category** (category bergantung budget; opsional).
11. Hapus category = **soft delete** (`is_active = false`) agar riwayat expense tetap valid.
12. Expense lama **tidak diubah** (`category_id = NULL`).

## 3. Non-goals

- Tidak memecah saldo/anggaran ke category.
- Tidak menambah nesting > 1 level.
- Tidak mengubah API budget berbasis nama yang ada (`/budgets/{name}`, `ExpenseRequest.budget` string).
- Tidak ada perubahan pada notifier/email service.

## 4. Skema database

Migration baru (setelah `V20__invoice_ai_provider.sql`): **`V21__categories.sql`**

```sql
-- Category per budget (induk). Saldo tetap di budgets.
-- category_id NULL pada expenses berarti "Uncategorized".

CREATE TABLE categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id   BIGINT NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(500) NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_category_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT uk_category_budget_name UNIQUE (budget_id, name)
);

CREATE INDEX idx_category_budget ON categories (budget_id, is_active);

ALTER TABLE expenses ADD COLUMN category_id BIGINT NULL;
ALTER TABLE expenses ADD CONSTRAINT fk_expense_category
    FOREIGN KEY (category_id) REFERENCES categories(id);
CREATE INDEX idx_expenses_category ON expenses (category_id);
```

Catatan:
- Ikuti gaya migration lama (lihat `V18__invoice_ai_retry.sql`, `V19__email_imports.sql`).
- `budgets.id` FK target: cek tipe di `V1__init.sql` (BIGINT) dan pastikan konsisten.
- Jangan mengubah migration lama.

## 5. Backend

Struktur mengikuti `AGENTS.md`: controller → service → data (JdbcTemplate) + model.

### 5.1 Entitas & repository baru

**`data/CategoryRepository.java`** (baru), method minimal:
- `List<CategoryData> findByBudgetId(long budgetId)` — aktif saja untuk opsi.
- `CategoryData findById(long id)`
- `Long findIdByBudgetAndName(long budgetId, String name)`
- `void create(long budgetId, String name, String description)`
- `void update(long id, String name, String description)`
- `void softDelete(long id)` → `UPDATE categories SET is_active = FALSE`
- `boolean hasActiveByBudgetId(long budgetId)`
- `List<CategoryData> findByBudgetName(String budgetName)` (untuk validasi/prompt AI)

**`data/CategoryData.java`** record: `id, budgetId, name, description, isActive`.

### 5.2 Repository yang diubah

**`data/BudgetRepository.java`**
- Tidak mengubah API nama yang ada.
- Tambah cara agar `BudgetOption` menyertakan daftar category (bisa digabung di service).

**`data/ExpenseRepository.java`**
- `SELECT_COLS` (sekitar :16-20): tambahkan `LEFT JOIN categories c ON c.id = e.category_id` dan kolom `c.name AS category_name`.
- `mapRow` (:112-133): isi `categoryName` (boleh null).
- `insert(...)` (:28-34): tambah parameter `categoryId` (nullable) + kolom `category_id`.
- `update(...)` (:75-82): update `category_id`.
- `getSummary`-nya ada di service; yang di sini: tambah query breakdown per category:
  - `List<CategorySum> sumByCategoryForPeriod(String period, String budgetName)` →
    `SELECT COALESCE(c.name,'Uncategorized') AS category, SUM(e.amount) AS amount, COUNT(*) AS count
     FROM expenses e LEFT JOIN categories c ON c.id=e.category_id
     WHERE e.period=? AND e.deleted=FALSE AND e.budget_id=(SELECT id FROM budgets WHERE name=?)
     GROUP BY category ORDER BY amount DESC`
- `ExpenseData` record: tambah `categoryName` (String, nullable).

### 5.3 Service

**`service/ExpenseService.java`**
- Category CRUD:
  - `getCategories(String budgetName)` → list untuk UI.
  - `createCategory(String budgetName, String name, String description)` → validasi nama unik per budget, budget ada & aktif.
  - `updateCategory(long id, String name, String description)` → validasi.
  - `deleteCategory(long id)` → soft delete (riwayat tetap menunjuk category lama).
- `getOptions()` (:61-63): sertakan category per budget pada `BudgetOption` (untuk cascading select + prompt AI).
- `createExpense` (:138-163) & `createExpenseBatch` (:177-233) & `updateExpense` (:357-382):
  - Terima `category` (nama, opsional). Resolve `category_id` **hanya bila** category ada.
  - Validasi: bila `category` diberikan, category harus milik budget yang dipilih; kalau tidak → `ValidationException`.
  - `categoryId = null` bila kosong (Uncategorized).
- `getSummary` (:277-296): tambah `categories` per budget (breakdown), termasuk bucket "Uncategorized" dari hasil query `sumByCategoryForPeriod`.
- `requireBudgetId` (:434-440) tetap; tambah helper `requireCategoryId(budgetId, categoryName)`.

**`service/InvoiceAnalysisService.java`**
- `buildPrompt()` (:522-588): kirim hierarki. Untuk tiap budget aktif tampilkan nama + deskripsi, lalu category-nya (indentasi + deskripsi category).
- Tambah instruksi:
  - isi `suggestedBudget` dengan **nama budget**,
  - isi `suggestedCategory` dengan **nama category** yang paling cocok, atau string kosong bila ragu/tidak ada,
  - **JANGAN** menulis "uncategorized".
- `AiInvoiceItem` (:model) tambah field `suggestedCategory`.

**`service/EmailParserService.java`**
- `buildPrompt()` (:522-539): sama, sertakan hierarki + minta `suggestedCategory` di JSON.
- `parseWithAi` (:368-401): baca `suggestedCategory` → `ParsedTransaction.suggestedCategory`.
- `ParsedTransaction` record: tambah field.

### 5.4 Model/DTO

- Baru: `model/CategoryOption.java`, `model/CategoryCreateRequest.java`, `model/CategoryUpdateRequest.java`.
- Ubah:
  - `model/BudgetOption.java` → tambah `List<CategoryOption> categories`.
  - `model/ExpenseRequest.java` → tambah `String category`.
  - `model/ExpenseResponse.java` → tambah `String category`.
  - `model/BatchExpenseItem.java` → tambah `String category`.
  - `model/BudgetSummary.java` → tambah `List<CategorySummary> categories`.
  - Baru `model/CategorySummary.java` (`category, amount, count`).
  - `model/AiInvoiceItem.java` → tambah `suggestedCategory`.

### 5.5 Controller

**`controller/ExpenseController.java`**
- Baru:
  - `GET /categories?budget=<nama>` (:dekat /options)
  - `POST /categories` (body `budgetName, name, description`)
  - `PUT /categories/{id}`
  - `DELETE /categories/{id}`
- `GET /options` (:53-62) otomatis kaya category bila `BudgetOption` diperluas.
- Expense CRUD (:225-369) terima `category` dari request (tanpa endpoint baru).

### 5.6 Error handling & logging

- Ikuti format error existing: `{ "success": false, "message": "..." }`.
- Validasi category bukan milik budget → pesan jelas (mis. "Category tidak sesuai budget").
- Jangan log credential. Log error DB/validasi secukupnya.

## 6. Frontend

Framework: React 19 + TS + Mantine + TanStack Query + Axios (lihat `AGENTS.md`).
Penting: dropdown `Select` di dalam `Modal fullScreen` mobile wajib
`comboboxProps={{ withinPortal: false }}` (lihat catatan di `AGENTS.md`).

### 6.1 Types & API
- `types/expense.ts`:
  - `BudgetOption` (+`categories`), `Category` type baru.
  - `BudgetCreateRequest`/`BudgetUpdateRequest` tidak berubah (category dikelola terpisah).
  - `Expense` (+`category?`), `BatchExpenseItem` (+`category?`).
  - `AiInvoiceItem` (+`suggestedCategory?`).
  - `BudgetSummary` (+`categories`), `CategorySummary`.
- `services/expense.ts`: `getCategories(budget)`, `createCategory`, `updateCategory`, `deleteCategory`.

### 6.2 Hooks
- `hooks/useBudgets.ts`: invalidasi `['options']` + `['categories', budget]` setelah mutasi.
- Hook baru `useCategories(budget)` (query key `['categories', budget]`).

### 6.3 Form & select (cascading)
Di semua tempat pemilihan budget untuk expense, ubah jadi **budget → category (opsional)**:
- `components/ExpenseForm.tsx` (select budget :108-111,156-178).
- `components/ReviewModal.tsx` (item budget select :454-464,500-508; matching :38-44).
- `components/ImportEmailModal.tsx` (matching :29-34; select :114-123).
- `pages/HistoryPage.tsx` (filter :324-328; edit modal :540).
- `components/TopUpModal.tsx` (top-up tetap ke budget; category tidak relevan — biarkan).

Aturan UI:
- Category select hanya muncul setelah budget dipilih.
- Placeholder/opsi kosong = **"Uncategorized"**.
- Saat budget berubah, reset category.
- Matching AI: cari budget by `suggestedBudget`; lalu cari category di dalamnya by `suggestedCategory`; kalau tak cocok → kosong (Uncategorized).

### 6.4 Kelola category
- Di `components/dashboard/BudgetHealth.tsx`, tambah aksi menu **"Kelola Category"** per kartu budget.
- Modal baru `components/ManageCategoriesModal.tsx`: list category + deskripsi, tambah/edit/hapus (soft delete). Tampilkan peringatan bila dihapus (riwayat tetap ada).
- Konsisten dengan modal budget existing (`AddBudgetModal.tsx`, `EditBudgetModal.tsx`).

### 6.5 Dashboard & laporan
- `DashboardPage.tsx` (:50-87) + `components/dashboard/SpendingByBudget.tsx`: tampilkan total per budget, bisa **drill-down** ke breakdown per category (termasuk "Uncategorized").
- `utils/insights.ts` (:73-147): pastikan agregasi tetap per budget (tidak terganggu category).
- Riwayat/`TransactionCard.tsx` (:34): tampilkan label category (bila ada).

## 7. Prompt AI — teks yang ditambahkan

Di `InvoiceAnalysisService.buildPrompt()` (dan analog `EmailParserService`), setelah daftar budget:

```
Struktur kategori (budget → category):
- Household Makan: <deskripsi budget>
    * Groceries: <deskripsi category>
    * Eating Out: <deskripsi category>
    ...
- Household Other: <deskripsi budget>
    * Toiletries: ...
...
Untuk tiap item isi "suggestedBudget" = nama BUDGET dan
"suggestedCategory" = nama CATEGORY yang paling cocok.
Bila ragu atau tidak ada category yang cocok, isi "suggestedCategory" dengan
string kosong (jangan menulis "uncategorized").
```

Pertahankan seluruh aturan pajak/diskon/Penyesuaian yang sudah ada.

## 8. Aturan validasi (ringkas)

- Category **opsional**; kosong → `category_id = NULL` (Uncategorized).
- Bila diisi, category **wajib milik budget** yang dipilih.
- Nama category **unik per budget**.
- Hapus category = soft delete; expense lama tetap valid.
- AI boleh kosong; dilarang "uncategorized".
- Top-up tetap ke budget (induk), bukan category.

## 9. Edge cases

- Budget tanpa category → select category kosong/placeholder; expense tetap bisa disimpan (Uncategorized).
- Category dihapus padahal dipakai expense lama → laporan tetap menghitung expense itu (join by id; nama category nonaktif tetap bisa ditampilkan, atau tampil "Uncategorized" bila ikut terfilter is_active — **putuskan saat implementasi**: sarankan tampilkan nama category lama agar riwayat utuh).
- Rename category → expense lama otomatis ikut (join by id).
- Dua budget boleh punya category bernama sama (mis. "Maintenance"), validasi scope per `budget_id`.
- Import email/scan yang menyarankan category tidak dikenal → kosong.
- Jangan memecah notifikasi/alert: alert tetap berbasis budget.

## 10. Testing

Backend (JUnit + Mockito, lihat `ExpenseServiceTest.java`):
- CRUD category: create/update/delete, nama duplikat dalam satu budget ditolak, beda budget boleh sama.
- Expense: category valid → tersimpan; category bukan milik budget → error; category kosong → NULL.
- `getSummary`: breakdown per category + bucket Uncategorized.
- `InvoiceAnalysisServiceTest`: assertion field `suggestedCategory`.
- `EmailParserServiceTest`: ditto.

Jalankan test (toolchain lokal / docker maven):
```
cd backend && mvn -q test
```
(atau via image `maven:3.9-eclipse-temurin-25` bila maven lokal tak ada.)

Frontend:
- `npm run lint` (oxlint) & `npm run build` (tsc -b && vite build).

## 11. Dokumentasi

- Update `PRD.md`:
  - Tambah model category (bagian Budget / Scan).
  - Aturan Uncategorized (NULL).
  - Prompt AI (budget + category).
- Update `AI_MODEL_LOG.md` bila prompt AI berubah signifikan.

## 12. Migration & kompatibilitas

- Additive; API budget berbasis nama tidak berubah.
- Expense lama tetap valid (category NULL).
- Tidak ada backfill.
- Rollback: `ALTER TABLE expenses DROP COLUMN category_id; DROP TABLE categories;`
  (migration baru untuk rollback bila perlu; jangan edit migration lama).

## 13. Estimasi

- Effort: ±1,75–2 hari (±24 file).
- Runtime/API cost: praktis nol (1 migration; prompt AI +≈100–250 token/scan → <$0,0001/scan).
- Risiko: rendah–sedang (titik paling "gigit": cascading select di 4 tempat + breakdown dashboard).

## 14. Checklist implementasi

### Tahap 1 — Data & Backend
- [x] `V21__categories.sql`
- [x] `CategoryRepository` + `CategoryData`
- [x] Model: `CategoryOption`, `CategoryCreateRequest`, `CategoryUpdateRequest`, `CategorySummary`
- [x] `BudgetOption` + `categories`
- [x] `ExpenseData`/`ExpenseRequest`/`ExpenseResponse`/`BatchExpenseItem` + category
- [x] `ExpenseRepository` join + insert/update `category_id` (breakdown per category dihitung in-memory di `ExpenseService.getSummary`)
- [x] `ExpenseService` CRUD category + validasi + summary breakdown
- [x] `ExpenseController` endpoint category
- [x] Unit test backend

### Tahap 2 — AI
- [x] `AiInvoiceItem` + `suggestedCategory`
- [x] Prompt `InvoiceAnalysisService` + `EmailParserService` (hierarki + category)
- [x] `ParsedTransaction` + `suggestedCategory` (+ persist `email_imports.suggested_category`)
- [x] Test AI

### Tahap 3 — Frontend
- [x] `types/expense.ts`, `services/expense.ts`, hooks
- [x] Cascading select (ExpenseForm, ReviewModal, ImportEmailModal, HistoryPage)
- [x] `ManageCategoriesModal` + aksi di `BudgetHealth`
- [x] Breakdown/drill-down dashboard (incl. Uncategorized)
- [x] lint + build

### Tahap 4 — Docs & rilis
- [x] Update `PRD.md`
- [x] `mvn test` + build backend
- [x] `npm run lint && npm run build`
- [ ] Deploy (`docker-compose.prod.yml`) + verifikasi health
