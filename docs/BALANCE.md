# Fitur Saldo Budget

## Konsep

Setiap budget memiliki saldo tersendiri.

Saldo disimpan sebagai kolom running pada tab `Budget` di Google Sheets (kolom B).

Alur:

- Saldo dikurangi otomatis sebesar nominal setiap kali expense tersimpan.
- Penambahan saldo (biasanya tiap gajian tanggal 25) dilakukan melalui aplikasi (endpoint `POST /topups`) dan dicatat pada tab `TopUp`.
- Saldo boleh bernilai negatif.

---

## Struktur Data (Google Sheets)

Tab `Budget`:

| Budget | Saldo     |
| ------ | --------- |
| Daily  | 500000    |
| Weekly | -10000    |

- Kolom A: nama budget.
- Kolom B: saldo running.
- Header berada di baris 1.

Backend hanya melakukan:

- Membaca kolom A dan B (untuk dropdown + saldo).
- Mengurangi nilai kolom B saat expense tersimpan.

---

## Perubahan API

### GET /options

Respons berubah dari daftar string menjadi daftar objek budget beserta saldonya.

```json
{
  "success": true,
  "data": {
    "budgets": [
      { "name": "Daily", "balance": 500000 },
      { "name": "Weekly", "balance": -10000 }
    ]
  }
}
```

Endpoint ini tetap menjadi satu-satunya sumber data untuk dropdown Budget.

---

## Backend

### GoogleSheetsClient

- `getOptions(sheetName)` — membaca tab Budget `A:B`, mengembalikan daftar `BudgetOption(name, balance)`. Nilai saldo kosong/blank diparsing sebagai `0`.
- `decrementBudget(budget, amount)` — membaca saldo cell B untuk budget terkait, mengurangi `amount`, lalu menulis ulang.

### ExpenseService

- `createExpense(request)` — setelah `appendExpense` sukses ke sheet periode, memanggil `decrementBudget(budget, amount)`.
- `createTopUp(request)` — mencatat top-up pada tab `TopUp`, lalu memanggil `adjustBudgetBalance(budget, +amount)`.

### Model

- `OptionsResponse(List<BudgetOption> budgets)`
- `BudgetOption(String name, long balance)`

---

## Frontend

### Type

```ts
interface OptionsResponse {
  budgets: { name: string; balance: number }[]
}
```

### ExpenseForm

- Dropdown Budget dibuat dua kolom menggunakan `renderOption`:
  - nama budget di kiri,
  - saldo di kanan, rata kanan, font mono,
  - warna merah jika saldo negatif.
- Kotak input hanya menampilkan nama budget yang dipilih.
- Setelah Nominal diisi, muncul kartu preview:
  - `Sisa saldo: Rp X`
  - `Nominal: Rp Y`
  - `Saldo nanti: Rp X - Y` (hijau jika >= 0, merah jika < 0)
- Preview di-disable jika budget belum dipilih atau nominal kosong / <= 0.

## Top-up

Penambahan saldo dilakukan melalui aplikasi, bukan manual di Google Sheets:

- Endpoint `POST /topups` (body: `budget`, `amount`, `description` opsional; `dateTime` opsional, default waktu sekarang).
- Setiap top-up menambah saldo budget (kolom B) dan dicatat sebagai baris pada tab `TopUp` (`Waktu | Budget | Nominal | Description | ID | Deleted`).
- `GET /topups` menampilkan riwayat top-up.
- Di UI, top-up dipicu dari ikon `+` pada tiap kartu budget di Dashboard.

---

## Catatan

- Update saldo budget terjadi setelah append expense. Jika `decrementBudget` gagal, expense tetap tersimpan dan error dicatat di log. Untuk penggunaan personal, perilaku ini toleran.
- Validasi nominal expense tetap mensyaratkan `amount > 0`. Nilai negatif hanya muncul pada tampilan saldo, bukan pada jumlah expense.
- Setiap perubahan yang memakai refleksi Google harus memastikan metadata native image ter-update melalui `./backend/generate-native-config.sh`.
