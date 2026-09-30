# Spec: Sub-category per Budget (2 tingkat, saldo di induk)

> Dokumen ini adalah spesifikasi implementasi untuk fitur **sub-category**.
> Dibuat agar sesi AI berikutnya bisa mengerjakan tanpa perlu mengulang riset.
> Baca juga `AGENTS.md` (aturan kode) dan `PRD.md` (konteks produk).

## 1. Ringkasan

Saat ini `budget` berperan ganda sebagai kategori. Fitur ini menambahkan
**sub-category** di bawah tiap budget, misalnya:

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

## 2. Keputusan final (sudah disepakati)

1. **Hierarki 1 level**: kategori (budget induk) → sub-category. Tidak ada nesting lebih dalam.
2. **Sub-category = entitas terpisah** (tabel `sub_categories`), BUKAN baris di `budgets`.
3. **Saldo/anggaran tetap di induk** (`budgets.balance`). Sub hanya **klasifikasi**; anggaran tidak dipecah.
4. **Sub opsional**. Bila tidak dipilih, expense dianggap **"Uncategorized"**.
5. **`NULL` = Uncategorized** — bukan baris khusus. Tidak ada backfill; expense lama otomatis Uncategorized.
6. **Nama sub unik per induk** (`UNIQUE(budget_id, name)`); boleh sama antar induk berbeda.
7. **Tiap sub punya `description`** (dipakai AI untuk memilih sub yang tepat).
8. **AI (scan & inbox)** menyarankan `budget` (induk) + `subCategory` (boleh kosong; dilarang "uncategorized").
9. **Laporan**: total tetap per induk; ada **breakdown/drill-down per sub** (termasuk bucket Uncategorized).
10. **Dropdown expense: pilih induk dulu, lalu sub** (sub bergantung induk; opsional).
11. Hapus sub = **soft delete** (`is_active = false`) agar riwayat expense tetap valid.
12. Expense lama **tidak diubah** (`sub_category_id = NULL`).

## 3. Non-goals

- Tidak memecah saldo/anggaran ke sub.
- Tidak menambah nesting > 1 level.
- Tidak mengubah API budget berbasis nama yang ada (`/budgets/{name}`, `ExpenseRequest.budget` string).
- Tidak ada perubahan pada notifier/email service.

## 4. Skema database

Migration baru (setelah `V20__invoice_ai_provider.sql`): **`V21__sub_categories.sql`**

```sql
-- Sub-category per budget (induk). Saldo tetap di budgets.
-- sub_category_id NULL pada expenses berarti "Uncategorized".

CREATE TABLE sub_categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id   BIGINT NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(500) NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_sub_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT uk_sub_budget_name UNIQUE (budget_id, name)
);

CREATE INDEX idx_sub_budget ON sub_categories (budget_id, is_active);

ALTER TABLE expenses ADD COLUMN sub_category_id BIGINT NULL;
ALTER TABLE expenses ADD CONSTRAINT fk_expense_sub
    FOREIGN KEY (sub_category_id) REFERENCES sub_categories(id);
CREATE INDEX idx_expenses_sub ON expenses (sub_category_id);
```

Catatan:
- Ikuti gaya migration lama (lihat `V18__invoice_ai_retry.sql`, `V19__email_imports.sql`).
- `budgets.id` FK target: cek tipe di `V1__init.sql` (BIGINT) dan pastikan konsisten.
- Jangan mengubah migration lama.

## 5. Backend

Struktur mengikuti `AGENTS.md`: controller → service → data (JdbcTemplate) + model.

### 5.1 Entitas & repository baru

**`data/SubCategoryRepository.java`** (baru), method minimal:
- `List<SubCategoryData> findByBudgetId(long budgetId)` — aktif saja untuk opsi.
- `SubCategoryData findById(long id)`
- `Long findIdByBudgetAndName(long budgetId, String name)`
- `void create(long budgetId, String name, String description)`
- `void update(long id, String name, String description)`
- `void softDelete(long id)` → `UPDATE sub_categories SET is_active = FALSE`
- `boolean hasActiveByBudgetId(long budgetId)`
- `List<SubCategoryData> findByBudgetName(String budgetName)` (untuk validasi/prompt AI)

**`data/SubCategoryData.java`** record: `id, budgetId, name, description, isActive`.

### 5.2 Repository yang diubah

**`data/BudgetRepository.java`**
- Tidak mengubah API nama yang ada.
- Tambah cara agar `BudgetOption` menyertakan daftar sub (bisa digabung di service).

**`data/ExpenseRepository.java`**
- `SELECT_COLS` (sekitar :16-20): tambahkan `LEFT JOIN sub_categories s ON s.id = e.sub_category_id` dan kolom `s.name AS sub_category_name`.
- `mapRow` (:112-133): isi `subCategoryName` (boleh null).
- `insert(...)` (:28-34): tambah parameter `subCategoryId` (nullable) + kolom `sub_category_id`.
- `update(...)` (:75-82): update `sub_category_id`.
- `getSummary`-nya ada di service; yang di sini: tambah query breakdown per sub:
  - `List<SubSummary> sumBySubForPeriod(String period, String budgetName)` →
    `SELECT COALESCE(s.name,'Uncategorized') AS sub, SUM(e.amount) AS amount, COUNT(*) AS count
     FROM expenses e LEFT JOIN sub_categories s ON s.id=e.sub_category_id
     WHERE e.period=? AND e.deleted=FALSE AND e.budget_id=(SELECT id FROM budgets WHERE name=?)
     GROUP BY sub ORDER BY amount DESC`
- `ExpenseData` record: tambah `subCategoryName` (String, nullable).

### 5.3 Service

**`service/ExpenseService.java`**
- Sub CRUD:
  - `getSubCategories(String budgetName)` → list untuk UI.
  - `createSubCategory(String budgetName, String name, String description)` → validasi nama unik per budget, budget ada & aktif.
  - `updateSubCategory(long id, String name, String description)` → validasi.
  - `deleteSubCategory(long id)` → soft delete (riwayat tetap menunjuk sub lama).
- `getOptions()` (:61-63): sertakan sub-categories per budget pada `BudgetOption` (untuk cascading select + prompt AI).
- `createExpense` (:138-163) & `createExpenseBatch` (:177-233) & `updateExpense` (:357-382):
  - Terima `subCategory` (nama, opsional). Resolve `sub_category_id` **hanya bila** sub ada.
  - Validasi: bila `subCategory` diberikan, sub harus milik budget yang dipilih; kalau tidak → `ValidationException`.
  - `subCategoryId = null` bila kosong (Uncategorized).
- `getSummary` (:277-296): tambah `subCategories` per budget (breakdown), termasuk bucket "Uncategorized" dari hasil query `sumBySubForPeriod`.
- `requireBudgetId` (:434-440) tetap; tambah helper `requireSubCategoryId(budgetId, subName)`.

**`service/InvoiceAnalysisService.java`**
- `buildPrompt()` (:522-588): kirim hierarki. Untuk tiap budget aktif tampilkan nama + deskripsi, lalu sub-sub-nya (indentasi + deskripsi sub).
- Tambah instruksi:
  - isi `suggestedBudget` dengan **nama induk**,
  - isi `suggestedSubCategory` dengan **nama sub** yang paling cocok, atau string kosong bila ragu/tidak ada,
  - **JANGAN** menulis "uncategorized".
- `AiInvoiceItem` (:model) tambah field `suggestedSubCategory`.

**`service/EmailParserService.java`**
- `buildPrompt()` (:522-539): sama, sertakan hierarki + minta `suggestedSubCategory` di JSON.
- `parseWithAi` (:368-401): baca `suggestedSubCategory` → `ParsedTransaction.suggestedSubCategory`.
- `ParsedTransaction` record: tambah field.

### 5.4 Model/DTO

- Baru: `model/SubCategoryOption.java`, `model/SubCategoryCreateRequest.java`, `model/SubCategoryUpdateRequest.java`.
- Ubah:
  - `model/BudgetOption.java` → tambah `List<SubCategoryOption> subCategories`.
  - `model/ExpenseRequest.java` → tambah `String subCategory`.
  - `model/ExpenseResponse.java` → tambah `String subCategory`.
  - `model/BatchExpenseItem.java` → tambah `String subCategory`.
  - `model/BudgetSummary.java` → tambah `List<SubCategorySummary> subCategories`.
  - Baru `model/SubCategorySummary.java` (`subCategory, amount, count`).
  - `model/AiInvoiceItem.java` → tambah `suggestedSubCategory`.

### 5.5 Controller

**`controller/ExpenseController.java`**
- Baru:
  - `GET /subcategories?budget=<nama>` (:dekat /options)
  - `POST /subcategories` (body `budgetName, name, description`)
  - `PUT /subcategories/{id}`
  - `DELETE /subcategories/{id}`
- `GET /options` (:53-62) otomatis kaya sub bila `BudgetOption` diperluas.
- Expense CRUD (:225-369) terima `subCategory` dari request (tanpa endpoint baru).

### 5.6 Error handling & logging

- Ikuti format error existing: `{ "success": false, "message": "..." }`.
- Validasi sub bukan milik budget → pesan jelas (mis. "Sub-category tidak sesuai budget").
- Jangan log credential. Log error DB/validasi secukupnya.

## 6. Frontend

Framework: React 19 + TS + Mantine + TanStack Query + Axios (lihat `AGENTS.md`).
Penting: dropdown `Select` di dalam `Modal fullScreen` mobile wajib
`comboboxProps={{ withinPortal: false }}` (lihat catatan di `AGENTS.md`).

### 6.1 Types & API
- `types/expense.ts`:
  - `BudgetOption` (+`subCategories`), `SubCategory` type baru.
  - `BudgetCreateRequest`/`BudgetUpdateRequest` tidak berubah (sub dikelola terpisah).
  - `Expense` (+`subCategory?`), `BatchExpenseItem` (+`subCategory?`).
  - `AiInvoiceItem` (+`suggestedSubCategory?`).
  - `BudgetSummary` (+`subCategories`), `SubCategorySummary`.
- `services/expense.ts`: `getSubCategories(budget)`, `createSubCategory`, `updateSubCategory`, `deleteSubCategory`.

### 6.2 Hooks
- `hooks/useBudgets.ts`: invalidasi `['options']` + `['subcategories', budget]` setelah mutasi.
- Hook baru `useSubCategories(budget)` (query key `['subcategories', budget]`).

### 6.3 Form & select (cascading)
Di semua tempat pemilihan budget untuk expense, ubah jadi **induk → sub (opsional)**:
- `components/ExpenseForm.tsx` (select budget :108-111,156-178).
- `components/ReviewModal.tsx` (item budget select :454-464,500-508; matching :38-44).
- `components/ImportEmailModal.tsx` (matching :29-34; select :114-123).
- `pages/HistoryPage.tsx` (filter :324-328; edit modal :540).
- `components/TopUpModal.tsx` (top-up tetap ke induk; sub tidak relevan — biarkan).

Aturan UI:
- Sub select hanya muncul setelah induk dipilih.
- Placeholder/opsi kosong = **"Uncategorized"**.
- Saat induk berubah, reset sub.
- Matching AI: cari budget by `suggestedBudget`; lalu cari sub di dalamnya by `suggestedSubCategory`; kalau tak cocok → kosong (Uncategorized).

### 6.4 Kelola sub-category
- Di `components/dashboard/BudgetHealth.tsx`, tambah aksi menu **"Kelola Sub"** per kartu budget.
- Modal baru `components/ManageSubCategoriesModal.tsx`: list sub + deskripsi, tambah/edit/hapus (soft delete). Tampilkan peringatan bila dihapus (riwayat tetap ada).
- Konsisten dengan modal budget existing (`AddBudgetModal.tsx`, `EditBudgetModal.tsx`).

### 6.5 Dashboard & laporan
- `DashboardPage.tsx` (:50-87) + `components/dashboard/SpendingByBudget.tsx`: tampilkan total per induk, bisa **drill-down** ke breakdown per sub (termasuk "Uncategorized").
- `utils/insights.ts` (:73-147): pastikan agregasi tetap per induk (tidak terganggu sub).
- Riwayat/`TransactionCard.tsx` (:34): tampilkan label sub (bila ada).

## 7. Prompt AI — teks yang ditambahkan

Di `InvoiceAnalysisService.buildPrompt()` (dan analog `EmailParserService`), setelah daftar budget:

```
Struktur kategori (induk → sub-category):
- Household Makan: <deskripsi induk>
    * Groceries: <deskripsi sub>
    * Eating Out: <deskripsi sub>
    ...
- Household Other: <deskripsi induk>
    * Toiletries: ...
...
Untuk tiap item isi "suggestedBudget" = nama INDUK dan
"suggestedSubCategory" = nama SUB yang paling cocok.
Bila ragu atau tidak ada sub yang cocok, isi "suggestedSubCategory" dengan
string kosong (jangan menulis "uncategorized").
```

Pertahankan seluruh aturan pajak/diskon/Penyesuaian yang sudah ada.

## 8. Aturan validasi (ringkas)

- Sub **opsional**; kosong → `sub_category_id = NULL` (Uncategorized).
- Bila diisi, sub **wajib milik budget** yang dipilih.
- Nama sub **unik per induk**.
- Hapus sub = soft delete; expense lama tetap valid.
- AI boleh kosong; dilarang "uncategorized".
- Top-up tetap ke budget (induk), bukan sub.

## 9. Edge cases

- Budget tanpa sub → select sub kosong/placeholder; expense tetap bisa disimpan (Uncategorized).
- Sub dihapus padahal dipakai expense lama → laporan tetap menghitung expense itu (join by id; nama sub nonaktif tetap bisa ditampilkan, atau tampil "Uncategorized" bila ikut terfilter is_active — **putuskan saat implementasi**: sarankan tampilkan nama sub lama agar riwayat utuh).
- Rename sub → expense lama otomatis ikut (join by id).
- Dua induk boleh punya sub bernama sama (mis. "Maintenance"), validasi scope per `budget_id`.
- Import email/scan yang menyarankan sub tidak dikenal → kosong.
- Jangan memecah notifikasi/alert: alert tetap berbasis budget.

## 10. Testing

Backend (JUnit + Mockito, lihat `ExpenseServiceTest.java`):
- CRUD sub: create/update/delete, nama duplikat dalam satu induk ditolak, beda induk boleh sama.
- Expense: sub valid → tersimpan; sub bukan milik budget → error; sub kosong → NULL.
- `getSummary`: breakdown per sub + bucket Uncategorized.
- `InvoiceAnalysisServiceTest`: assertion field `suggestedSubCategory`.
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
  - Tambah model sub-category (bagian Budget / Scan).
  - Aturan Uncategorized (NULL).
  - Prompt AI (budget + sub).
- Update `AI_MODEL_LOG.md` bila prompt AI berubah signifikan.

## 12. Migration & kompatibilitas

- Additive; API budget berbasis nama tidak berubah.
- Expense lama tetap valid (sub NULL).
- Tidak ada backfill.
- Rollback: `ALTER TABLE expenses DROP COLUMN sub_category_id; DROP TABLE sub_categories;`
  (migration baru untuk rollback bila perlu; jangan edit migration lama).

## 13. Estimasi

- Effort: ±1,75–2 hari (±24 file).
- Runtime/API cost: praktis nol (1 migration; prompt AI +≈100–250 token/scan → <$0,0001/scan).
- Risiko: rendah–sedang (titik paling "gigit": cascading select di 4 tempat + breakdown dashboard).

## 14. Checklist implementasi

### Tahap 1 — Data & Backend
- [ ] `V21__sub_categories.sql`
- [ ] `SubCategoryRepository` + `SubCategoryData`
- [ ] Model: `SubCategoryOption`, `SubCategoryCreateRequest`, `SubCategoryUpdateRequest`, `SubCategorySummary`
- [ ] `BudgetOption` + `subCategories`
- [ ] `ExpenseData`/`ExpenseRequest`/`ExpenseResponse`/`BatchExpenseItem` + sub
- [ ] `ExpenseRepository` join + insert/update sub + `sumBySubForPeriod`
- [ ] `ExpenseService` CRUD sub + validasi + summary breakdown
- [ ] `ExpenseController` endpoint sub
- [ ] Unit test backend

### Tahap 2 — AI
- [ ] `AiInvoiceItem` + `suggestedSubCategory`
- [ ] Prompt `InvoiceAnalysisService` + `EmailParserService` (hierarki + sub)
- [ ] `ParsedTransaction` + `suggestedSubCategory`
- [ ] Test AI

### Tahap 3 — Frontend
- [ ] `types/expense.ts`, `services/expense.ts`, hooks
- [ ] Cascading select (ExpenseForm, ReviewModal, ImportEmailModal, HistoryPage)
- [ ] `ManageSubCategoriesModal` + aksi di `BudgetHealth`
- [ ] Breakdown/drill-down dashboard (incl. Uncategorized)
- [ ] lint + build

### Tahap 4 — Docs & rilis
- [ ] Update `PRD.md`
- [ ] `mvn test` + build backend
- [ ] `npm run lint && npm run build`
- [ ] Deploy (`docker-compose.prod.yml`) + verifikasi health
