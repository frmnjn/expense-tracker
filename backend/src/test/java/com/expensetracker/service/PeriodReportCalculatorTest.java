package com.expensetracker.service;

import com.expensetracker.data.ExpenseData;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.CategoryOption;
import com.expensetracker.model.PeriodReportData;
import com.expensetracker.model.PeriodReportStatistics;
import com.expensetracker.model.PeriodReportStatistics.BudgetStatus;
import com.expensetracker.model.PeriodReportStatistics.Integrity;
import com.expensetracker.model.TopUpResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodReportCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 25);
    private static final LocalDate END = LocalDate.of(2026, 10, 22);

    @Test
    void calculate_shouldAggregateWithoutDoubleCountingAndKeepTopUpsSeparate() {
        List<ExpenseData> expenses = List.of(
                expense("e1", "2026-10-22 18:09", "Sate", "Daily", "Eating Out", 35_000, ""),
                expense("e2", "2026-10-01 09:00", "Netflix.com", "Daily", null, 15_000, ""),
                expense("e3", "2026-10-02 09:00", "Mainan", "Other", null, 100_000, ""));
        List<TopUpResponse> topUps = List.of(
                new TopUpResponse("t1", "2026-09-25 06:12", "Daily", 50_000L, ""),
                new TopUpResponse("t2", "2026-09-25 06:12", "Other", 20_000L, ""));
        List<BudgetOption> budgets = List.of(
                new BudgetOption("Daily", 65_000, 100_000, null,
                        List.of(new CategoryOption(1L, "Eating Out", null),
                                new CategoryOption(2L, "Groceries", null))),
                new BudgetOption("Other", -1_000, 0, null));

        PeriodReportStatistics stats = PeriodReportCalculator.calculate(
                data(expenses, topUps, budgets, 150_000, 3));

        assertEquals(150_000, stats.expenseTotal());
        assertEquals(3, stats.transactionCount());
        assertEquals(70_000, stats.topUpTotal());
        assertEquals(28, stats.periodDays());
        assertEquals(5_357, stats.averageDailyExpense());
        assertTrue(stats.integrity().ok());
        assertEquals(2, stats.categories().size());
        assertEquals("Tanpa Kategori", stats.categories().get(0).category());
        assertEquals(115_000, stats.categories().get(0).amount());
        assertEquals(new BigDecimal("76.7"), stats.categories().get(0).percent());
        assertEquals(3, stats.budgetCategories().size());

        assertEquals(BudgetStatus.LOW, budget(stats, "Daily").status());
        assertEquals(50_000, budget(stats, "Daily").topUps());
        assertEquals(50_000, budget(stats, "Daily").expenses());
        assertEquals(0, budget(stats, "Daily").netChange());
        assertEquals(BudgetStatus.NEGATIVE, budget(stats, "Other").status());
    }

    @Test
    void calculate_shouldFlagIntegrityProblemsButKeepRecords() {
        List<ExpenseData> expenses = List.of(
                new ExpenseData("e1", "2026-OCT-NOV", "2026-10-23 01:09", "Late", "Daily",
                        10_000, "", false, false, null, null, null, "Eating Out", null));

        PeriodReportStatistics stats = PeriodReportCalculator.calculate(
                data(expenses, List.of(), List.of(), 99_000, 9));

        Integrity integrity = stats.integrity();
        assertFalse(integrity.ok());
        assertEquals(1, integrity.outOfPeriodTransactions());
        assertEquals(1, integrity.periodMismatches());
        assertFalse(integrity.totalsReconciled());
        assertEquals(1, stats.transactionCount());
        assertEquals(10_000, stats.expenseTotal());
        assertTrue(stats.observations().get(0).contains("Peringatan integritas data"));
    }

    @Test
    void calculate_shouldHandleEmptyPeriod() {
        PeriodReportStatistics stats = PeriodReportCalculator.calculate(
                data(List.of(), List.of(), List.of(), 0, 0));

        assertEquals(0, stats.expenseTotal());
        assertEquals(0, stats.transactionCount());
        assertEquals(0, stats.topUpTotal());
        assertEquals(0, stats.averageDailyExpense());
        assertEquals(28, stats.periodDays());
        assertTrue(stats.categories().isEmpty());
        assertTrue(stats.budgetCategories().isEmpty());
        assertTrue(stats.largestTransactions().isEmpty());
        assertTrue(stats.observations().size() >= 1);
        assertTrue(stats.observations().get(0).contains("Total pengeluaran Rp0"));
        assertTrue(stats.recommendations().get(0).contains("Tidak ada tindakan mendesak"));
    }

    @Test
    void recommendations_shouldNotAskToCategorizeUncategorizedExpenses() {
        List<ExpenseData> expenses = List.of(
                expense("e1", "2026-10-01 09:00", "Jajan", "Daily", null, 10_000, ""));
        PeriodReportStatistics stats = PeriodReportCalculator.calculate(
                data(expenses, List.of(), List.of(new BudgetOption("Daily", 100_000, 0, null)),
                        10_000, 1));

        assertTrue(stats.recommendations().stream()
                .noneMatch(recommendation -> recommendation.toLowerCase().contains("kategori")));
    }

    @Test
    void percentages_shouldRoundHalfUpAndHandleZeroTotal() {
        assertEquals(new BigDecimal("33.3"), PeriodReportCalculator.percent(1, 3));
        assertEquals(new BigDecimal("66.7"), PeriodReportCalculator.percent(2, 3));
        assertEquals(new BigDecimal("0.0"), PeriodReportCalculator.percent(10, 0));
    }

    @Test
    void reportFormat_shouldFormatRupiahAndPercent() {
        assertEquals("Rp11.995.103", ReportFormat.rupiah(11_995_103));
        assertEquals("-Rp5.000", ReportFormat.rupiah(-5_000));
        assertEquals("12.3%", ReportFormat.percent(new BigDecimal("12.3")));
        assertEquals("22 Okt 2026 18:09", ReportFormat.expenseDateTime("2026-10-22 18:09"));
    }

    private static ExpenseData expense(String id, String dateTime, String name, String budget,
                                       String category, long amount, String description) {
        return new ExpenseData(id, "2026-SEP-OCT", dateTime, name, budget, amount, description,
                false, false, null, null, null, category, null);
    }

    private static PeriodReportStatistics.BudgetStat budget(PeriodReportStatistics stats, String name) {
        return stats.budgets().stream().filter(b -> b.budget().equals(name)).findFirst().orElseThrow();
    }

    private static PeriodReportData data(List<ExpenseData> expenses, List<TopUpResponse> topUps,
                                         List<BudgetOption> budgets, long databaseTotal, int databaseCount) {
        long expenseTotal = expenses.stream().mapToLong(ExpenseData::amount).sum();
        long topUpTotal = topUps.stream().map(TopUpResponse::amount)
                .filter(java.util.Objects::nonNull).mapToLong(Long::longValue).sum();
        return new PeriodReportData("2026-SEP-OCT", START, END,
                LocalDateTime.of(2026, 10, 23, 0, 5), "Asia/Jakarta",
                expenses, topUps, budgets, expenseTotal, topUpTotal, databaseTotal, databaseCount);
    }
}
