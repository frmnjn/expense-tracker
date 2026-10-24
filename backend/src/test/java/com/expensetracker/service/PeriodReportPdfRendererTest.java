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
            String text = new PDFTextStripper().getText(document);
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
            assertTrue(text.contains("NEGATIF"));
            assertTrue(text.contains("Nasi padang & es teh"));
            assertTrue(text.contains("Tanpa Kategori"));
            assertTrue(text.contains("Halaman 1 dari"));
            assertFalse(text.contains("gaya hidup"));
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
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Tidak ada transaksi pada periode ini"));
            assertTrue(text.contains("Tidak ada top-up pada periode ini"));
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
