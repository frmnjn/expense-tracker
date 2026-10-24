package com.expensetracker.service;

import com.expensetracker.data.ExpenseData;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.CategoryOption;
import com.expensetracker.model.PeriodReportData;
import com.expensetracker.model.PeriodReportStatistics;
import com.expensetracker.model.TopUpResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodReportPdfRendererTest {

    private final PeriodReportPdfRenderer renderer = new PeriodReportPdfRenderer();

    @Test
    void render_shouldContainSummaryBreakdownsAndFixedDate() throws Exception {
        PeriodReportData data = data(List.of(
                expense("e1", "2026-10-22 18:09", "Makan Siang", "Daily", "Eating Out",
                        35_000, "Nasi padang & es teh"),
                expense("e2", "2026-10-01 09:00", "Netflix.com", "Daily", null, 15_000, "")),
                List.of(new TopUpResponse("t1", "2026-10-01 09:00", "Daily", 100_000L, "Top up")),
                List.of(new BudgetOption("Daily", -1_000, 50_000, "Makan harian",
                        List.of(new CategoryOption(1L, "Eating Out", null)))));
        PeriodReportStatistics stats = PeriodReportCalculator.calculate(data);

        byte[] pdf = renderer.render(data, stats);

        assertTrue(pdf.length > 1_000);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Laporan Pengeluaran"));
            assertTrue(text.contains("2026-SEP-OCT"));
            assertTrue(text.contains("Ringkasan Eksekutif"));
            assertTrue(text.contains("Rincian Pengeluaran"));
            assertTrue(text.contains("Ringkasan Budget"));
            assertTrue(text.contains("Analisis Keuangan"));
            assertTrue(text.contains("Lampiran A"));
            assertTrue(text.contains("22 Okt 2026 18:09"));
            assertTrue(text.contains("Rp35.000"));
            assertTrue(text.contains("Rp100.000"));
            assertTrue(text.contains("Saldo Minus"));
            assertTrue(text.contains("1 budget memiliki saldo minus. Periksa pengeluaran dan kondisi "
                    + "saldo budget tersebut."));
            assertTrue(text.contains("-Rp1.000"));
            assertTrue(text.contains("Nasi padang & es teh"));
            assertTrue(text.contains("Tanpa Kategori"));
            assertTrue(text.contains("Saldo awal periode"));
            assertTrue(text.contains("Uang yang dipakai selama periode"));
            assertTrue(text.contains("Uang yang ditambahkan selama periode"));
            assertTrue(text.contains("Sisa saldo akhir periode"));
            assertTrue(text.contains("Kondisi budget"));
            assertTrue(text.contains("Cara membaca angka"));
            assertTrue(text.contains("periode berikutnya"));
            assertTrue(text.contains("Penyesuaian saldo manual"));
            assertFalse(text.contains("Perubahan bersih"));
            assertFalse(text.contains("Saldo berjalan"));
            assertFalse(text.contains("Perlu Perhatian"));
            assertFalse(text.contains("Observasi"));
            assertTrue(text.contains("Halaman 1 dari"));
            assertFalse(text.contains("gaya hidup"));
        }
    }

    @Test
    void render_shouldShowPlainLanguageBudgetSummaryAndKeepAllBudgets() throws Exception {
        List<ExpenseData> expenses = List.of(
                expense("e1", "2026-10-02 10:00", "Makan", "Negatif", "Food", 20_000, ""),
                expense("e2", "2026-10-03 10:00", "Belanja", "Aman", null, 30_000, ""));
        List<TopUpResponse> topUps = List.of(
                new TopUpResponse("t1", "2026-10-01 09:00", "Negatif", 5_000L, ""),
                new TopUpResponse("t2", "2026-10-01 09:00", "Aman", 50_000L, ""));
        List<BudgetOption> budgets = List.of(
                new BudgetOption("Negatif", -25_000, 0, null),
                new BudgetOption("Aman", 200_000, 100_000, null),
                new BudgetOption("Perhatian", 15_000, 50_000, null),
                new BudgetOption("Nol", 0, 0, null),
                new BudgetOption("TanpaAmbang", 75_000, 0, null));
        PeriodReportData data = data(expenses, topUps, budgets);
        PeriodReportStatistics stats = PeriodReportCalculator.calculate(data);

        byte[] pdf = renderer.render(data, stats);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("1 budget memiliki saldo minus. Periksa pengeluaran dan kondisi "
                    + "saldo budget tersebut."));
            assertTrue(text.contains("1 budget perlu mulai dihemat. Saldonya sudah di bawah batas "
                    + "pengingat yang ditentukan."));
            assertTrue(text.contains("2 budget belum memiliki batas pengingat saldo rendah. Atur batasnya "
                    + "agar kamu mendapat pengingat saat saldo mulai menipis."));
            assertTrue(text.contains("Budget dengan saldo minus 1"));
            assertTrue(text.contains("Budget dengan saldo nol atau lebih 4"));
            assertTrue(text.contains("Total uang ditambahkan periode ini Rp55.000"));
            assertTrue(text.contains("Total uang dipakai periode ini Rp50.000"));
            assertTrue(text.contains("Saldo Minus"));
            assertTrue(text.contains("Saldo Menipis"));
            assertTrue(text.contains("Batas Rp50.000"));
            assertTrue(text.contains("Batas belum diatur"));
            assertTrue(text.contains("Saldo Tersedia"));
            assertTrue(text.contains("-Rp25.000"));
            assertTrue(text.contains("-Rp10.000"));
            assertTrue(text.contains("Rp180.000"));
            assertTrue(text.contains("Rp0"));
            assertTrue(text.contains("Nol"));
            assertTrue(text.contains("TanpaAmbang"));
            assertFalse(text.contains("Semua budget masih di atas"));
            assertFalse(text.contains("Perlu Perhatian"));

            int conditionStart = text.indexOf("Kondisi budget");
            assertTrue(conditionStart > 0);
            int minus = text.indexOf("memiliki saldo minus", conditionStart);
            int low = text.indexOf("perlu mulai dihemat", conditionStart);
            int noThreshold = text.indexOf("belum memiliki batas pengingat saldo rendah", conditionStart);
            assertTrue(minus < low);
            assertTrue(low < noThreshold);

            int tableStart = text.indexOf("Nama budget");
            assertTrue(tableStart > 0);
            int openingHeader = text.indexOf("Saldo awal periode", tableStart);
            int addedHeader = text.indexOf("Uang yang ditambahkan selama periode", tableStart);
            int usedHeader = text.indexOf("Uang yang dipakai selama periode", tableStart);
            int endingHeader = text.indexOf("Sisa saldo akhir periode", tableStart);
            assertTrue(openingHeader < addedHeader);
            assertTrue(addedHeader < usedHeader);
            assertTrue(usedHeader < endingHeader);
            int negative = text.indexOf("Negatif", tableStart);
            int attention = text.indexOf("Perhatian", tableStart);
            int zero = text.indexOf("Nol", tableStart);
            int withoutThreshold = text.indexOf("TanpaAmbang", tableStart);
            int available = text.indexOf("Aman", tableStart);
            assertTrue(negative < attention);
            assertTrue(attention < zero);
            assertTrue(zero < withoutThreshold);
            assertTrue(withoutThreshold < available);
        }
    }

    @Test
    void render_shouldWarnWhenAllWarningThresholdsAreInactive() throws Exception {
        PeriodReportData data = data(List.of(), List.of(),
                List.of(new BudgetOption("Daily", 100_000, 0, null)));
        PeriodReportStatistics stats = PeriodReportCalculator.calculate(data);

        byte[] pdf = renderer.render(data, stats);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("1 budget belum memiliki batas pengingat saldo rendah. Atur batasnya "
                    + "agar kamu mendapat pengingat saat saldo mulai menipis."));
            assertTrue(text.contains("Saldo Tersedia"));
            assertTrue(text.contains("Batas belum diatur"));
            assertFalse(text.contains("memiliki saldo minus"));
            assertFalse(text.contains("perlu mulai dihemat"));
            assertFalse(text.contains("Semua budget masih di atas"));
        }
    }

    @Test
    void render_shouldShowNormalConditionWhenAllBalancesAtOrAboveThreshold() throws Exception {
        PeriodReportData data = data(List.of(), List.of(), List.of(
                new BudgetOption("Tepat", 50_000, 50_000, null),
                new BudgetOption("Aman", 100_000, 50_000, null)));
        PeriodReportStatistics stats = PeriodReportCalculator.calculate(data);

        byte[] pdf = renderer.render(data, stats);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Semua budget masih di atas atau sama dengan batas pengingat saldo "
                    + "rendah. Tetap pantau pengeluaran agar saldo tidak cepat habis."));
            assertTrue(text.contains("Saldo Tersedia"));
            assertFalse(text.contains("Saldo Menipis"));
            assertFalse(text.contains("Saldo Minus"));
            assertFalse(text.contains("belum memiliki batas pengingat saldo rendah"));
        }
    }

    @Test
    void render_shouldRepeatTableHeadersAcrossPagesAndNumberPages() throws Exception {
        List<ExpenseData> expenses = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            expenses.add(expense("e" + i, "2026-10-0" + (1 + i % 9) + " 10:00",
                    "Transaction " + i, "Daily", "Eating Out", 10_000 + i,
                    "Long description for transaction " + i + " that should wrap safely without truncation."));
        }
        PeriodReportData data = data(expenses, List.of(),
                List.of(new BudgetOption("Daily", 1_000_000, 0, null,
                        List.of(new CategoryOption(1L, "Eating Out", null)))));
        PeriodReportStatistics stats = PeriodReportCalculator.calculate(data);

        byte[] pdf = renderer.render(data, stats);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            int pageCount = document.getNumberOfPages();
            int pagesWithAppendixHeader = 0;
            for (int page = 1; page <= pageCount; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                assertTrue(text.contains("Halaman " + page + " dari " + pageCount));
                if (text.contains("Tanggal") && text.contains("Kategori") && text.contains("Nominal")) {
                    pagesWithAppendixHeader++;
                }
            }
            assertTrue(pagesWithAppendixHeader >= 2);
        }
    }

    @Test
    void render_shouldHandleEmptyPeriod() throws Exception {
        PeriodReportData data = data(List.of(), List.of(), List.of());
        PeriodReportStatistics stats = PeriodReportCalculator.calculate(data);

        byte[] pdf = renderer.render(data, stats);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Belum ada budget yang bisa diringkas. Tambahkan budget untuk mulai "
                    + "memantau pengeluaran."));
            assertTrue(text.contains("Tidak ada transaksi pada periode ini"));
            assertTrue(text.contains("Tidak ada uang yang ditambahkan pada periode ini"));
            assertFalse(text.contains("belum memiliki batas pengingat"));
            assertEquals(2, document.getNumberOfPages());
        }
    }

    private static PeriodReportData data(List<ExpenseData> expenses, List<TopUpResponse> topUps,
                                         List<BudgetOption> budgets) {
        long expenseTotal = expenses.stream().mapToLong(ExpenseData::amount).sum();
        long topUpTotal = topUps.stream().map(TopUpResponse::amount)
                .filter(java.util.Objects::nonNull).mapToLong(Long::longValue).sum();
        return new PeriodReportData(
                "2026-SEP-OCT",
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 10, 22),
                LocalDateTime.of(2026, 10, 23, 0, 5),
                "Asia/Jakarta",
                expenses,
                topUps,
                budgets,
                expenseTotal,
                topUpTotal,
                expenseTotal,
                expenses.size());
    }

    private static ExpenseData expense(String id, String dateTime, String name, String budget,
                                       String category, long amount, String description) {
        return new ExpenseData(id, "2026-SEP-OCT", dateTime, name, budget, amount, description,
                false, false, null, null, null, category, null);
    }
}
