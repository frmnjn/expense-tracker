package com.expensetracker.service;

import com.expensetracker.data.ExpenseData;
import com.expensetracker.model.PeriodReportData;
import com.expensetracker.model.PeriodReportStatistics;
import com.expensetracker.model.PeriodReportStatistics.BudgetCategoryTotal;
import com.expensetracker.model.PeriodReportStatistics.BudgetStat;
import com.expensetracker.model.PeriodReportStatistics.BudgetStatus;
import com.expensetracker.model.PeriodReportStatistics.CategoryTotal;
import com.expensetracker.model.PeriodReportStatistics.Integrity;
import com.expensetracker.model.TopUpResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PeriodReportCalculator {

    private static final int MAX_OBSERVATIONS = 5;
    private static final int MAX_RECOMMENDATIONS = 5;
    private static final int MAX_LARGEST_TRANSACTIONS = 5;

    private static final String TANPA_KATEGORI = "Tanpa Kategori";

    private static final Comparator<BudgetStat> BUDGET_ATTENTION_ORDER =
            Comparator.comparingInt((BudgetStat budget) -> budgetPriority(budget.status()))
                    .thenComparingLong(BudgetStat::balance)
                    .thenComparing(BudgetStat::budget);

    private PeriodReportCalculator() {
    }

    public static PeriodReportStatistics calculate(PeriodReportData data) {
        long expenseTotal = data.expenses().stream().mapToLong(ExpenseData::amount).sum();
        long topUpTotal = data.topUps().stream()
                .map(TopUpResponse::amount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        int periodDays = (int) Math.max(1,
                ChronoUnit.DAYS.between(data.startDate(), data.endDate()) + 1);
        long averageDaily = expenseTotal == 0
                ? 0
                : BigDecimal.valueOf(expenseTotal)
                        .divide(BigDecimal.valueOf(periodDays), 0, RoundingMode.HALF_UP)
                        .longValue();

        Map<String, Long> categoryAmount = new LinkedHashMap<>();
        Map<String, Integer> categoryCount = new LinkedHashMap<>();
        Map<String, Set<String>> categoryBudgets = new LinkedHashMap<>();
        Map<String, Map<String, Long>> budgetCategoryAmount = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> budgetCategoryCount = new LinkedHashMap<>();
        Map<String, Long> expenseByBudget = new LinkedHashMap<>();
        Map<String, Integer> countByBudget = new LinkedHashMap<>();

        for (ExpenseData expense : data.expenses()) {
            String category = categoryName(expense);
            String budget = expense.budgetName();
            categoryAmount.merge(category, expense.amount(), Long::sum);
            categoryCount.merge(category, 1, Integer::sum);
            categoryBudgets.computeIfAbsent(category, key -> new LinkedHashSet<>()).add(budget);
            budgetCategoryAmount.computeIfAbsent(budget, key -> new LinkedHashMap<>())
                    .merge(category, expense.amount(), Long::sum);
            budgetCategoryCount.computeIfAbsent(budget, key -> new LinkedHashMap<>())
                    .merge(category, 1, Integer::sum);
            expenseByBudget.merge(budget, expense.amount(), Long::sum);
            countByBudget.merge(budget, 1, Integer::sum);
        }

        List<CategoryTotal> categories = categoryAmount.entrySet().stream()
                .map(entry -> new CategoryTotal(
                        entry.getKey(),
                        entry.getValue(),
                        categoryCount.getOrDefault(entry.getKey(), 0),
                        categoryBudgets.getOrDefault(entry.getKey(), Set.of()).size(),
                        percent(entry.getValue(), expenseTotal)))
                .sorted(Comparator.comparingLong(CategoryTotal::amount).reversed()
                        .thenComparing(CategoryTotal::category))
                .toList();

        List<BudgetCategoryTotal> budgetCategories = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> budgetEntry : budgetCategoryAmount.entrySet()) {
            Map<String, Integer> counts = budgetCategoryCount.getOrDefault(budgetEntry.getKey(), Map.of());
            for (Map.Entry<String, Long> categoryEntry : budgetEntry.getValue().entrySet()) {
                budgetCategories.add(new BudgetCategoryTotal(
                        budgetEntry.getKey(),
                        categoryEntry.getKey(),
                        categoryEntry.getValue(),
                        counts.getOrDefault(categoryEntry.getKey(), 0),
                        percent(categoryEntry.getValue(), expenseTotal)));
            }
        }
        budgetCategories.sort(Comparator.comparingLong(BudgetCategoryTotal::amount).reversed()
                .thenComparing(BudgetCategoryTotal::budget)
                .thenComparing(BudgetCategoryTotal::category));

        Map<String, Long> topUpsByBudget = new LinkedHashMap<>();
        for (TopUpResponse topUp : data.topUps()) {
            long amount = topUp.amount() == null ? 0 : topUp.amount();
            topUpsByBudget.merge(topUp.budget(), amount, Long::sum);
        }

        List<BudgetStat> budgetStats = data.budgets().stream()
                .map(budget -> {
                    long topUps = topUpsByBudget.getOrDefault(budget.name(), 0L);
                    long expenses = expenseByBudget.getOrDefault(budget.name(), 0L);
                    int count = countByBudget.getOrDefault(budget.name(), 0);
                    return new BudgetStat(
                            budget.name(),
                            budget.balance() - topUps + expenses,
                            topUps,
                            expenses,
                            count,
                            topUps - expenses,
                            budget.balance(),
                            budget.alertThreshold(),
                            budgetStatus(budget.balance(), budget.alertThreshold()));
                })
                .sorted(BUDGET_ATTENTION_ORDER)
                .toList();

        List<ExpenseData> largestTransactions = data.expenses().stream()
                .filter(expense -> expense.amount() > 0)
                .sorted(Comparator.comparingLong(ExpenseData::amount).reversed()
                        .thenComparing(ExpenseData::dateTime, Comparator.reverseOrder()))
                .limit(MAX_LARGEST_TRANSACTIONS)
                .toList();

        Integrity integrity = integrity(data, expenseTotal);

        List<String> observations = buildObservations(
                data, expenseTotal, topUpTotal, periodDays, averageDaily,
                categories, largestTransactions, integrity);
        List<String> recommendations = buildRecommendations(budgetStats, integrity);

        return new PeriodReportStatistics(
                expenseTotal,
                data.expenses().size(),
                topUpTotal,
                periodDays,
                averageDaily,
                categories,
                budgetCategories,
                budgetStats,
                largestTransactions,
                observations,
                recommendations,
                integrity);
    }

    private static List<String> buildObservations(
            PeriodReportData data,
            long expenseTotal,
            long topUpTotal,
            int periodDays,
            long averageDaily,
            List<CategoryTotal> categories,
            List<ExpenseData> largestTransactions,
            Integrity integrity) {
        List<String> observations = new ArrayList<>();
        if (!integrity.ok()) {
            observations.add("Peringatan integritas data: total atau klasifikasi tanggal laporan "
                    + "belum sepenuhnya cocok dengan database.");
        }
        observations.add("Total pengeluaran " + ReportFormat.rupiah(expenseTotal) + " dari "
                + data.expenses().size() + " transaksi, rata-rata "
                + ReportFormat.rupiah(averageDaily) + " per hari selama " + periodDays + " hari.");
        if (!categories.isEmpty() && !TANPA_KATEGORI.equals(categories.get(0).category())) {
            CategoryTotal top = categories.get(0);
            observations.add("Kategori terbesar adalah " + top.category() + " sebesar "
                    + ReportFormat.rupiah(top.amount()) + " (" + ReportFormat.percent(top.percent())
                    + " dari pengeluaran).");
        }

        if (!largestTransactions.isEmpty()) {
            ExpenseData largest = largestTransactions.get(0);
            observations.add("Transaksi terbesar adalah " + largest.name() + " pada "
                    + ReportFormat.expenseDateTime(largest.dateTime()) + " sebesar "
                    + ReportFormat.rupiah(largest.amount()) + " ("
                    + ReportFormat.percent(percent(largest.amount(), expenseTotal)) + " dari total).");
        }
        if (topUpTotal != 0) {
            observations.add("Uang yang ditambahkan periode ini sebesar " + ReportFormat.rupiah(topUpTotal)
                    + "; nominal ini bukan pengeluaran.");
        }
        return observations.stream().limit(MAX_OBSERVATIONS).toList();
    }

    private static List<String> buildRecommendations(
            List<BudgetStat> budgetStats,
            Integrity integrity) {
        List<String> recommendations = new ArrayList<>();
        List<String> negative = budgetStats.stream()
                .filter(budget -> budget.status() == BudgetStatus.NEGATIVE)
                .map(BudgetStat::budget)
                .toList();
        if (!negative.isEmpty()) {
            recommendations.add("Budget berikut saldonya minus. Periksa pengeluaran atau tambahkan saldo: "
                    + String.join(", ", negative) + ".");
        }
        List<String> low = budgetStats.stream()
                .filter(budget -> budget.status() == BudgetStatus.LOW)
                .map(BudgetStat::budget)
                .toList();
        if (!low.isEmpty()) {
            recommendations.add("Budget berikut perlu mulai dihemat karena saldonya sudah di bawah "
                    + "batas pengingat: " + String.join(", ", low) + ".");
        }
        List<String> noThreshold = budgetStats.stream()
                .filter(budget -> budget.status() == BudgetStatus.NO_THRESHOLD)
                .map(BudgetStat::budget)
                .toList();
        if (!noThreshold.isEmpty()) {
            recommendations.add("Atur batas pengingat saldo rendah untuk budget berikut agar kamu mendapat "
                    + "pengingat otomatis: " + String.join(", ", noThreshold) + ".");
        }
        if (!integrity.ok()) {
            recommendations.add("Periksa kembali klasifikasi periode dan total terhadap database "
                    + "sebelum menggunakan laporan ini.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Tidak ada tindakan mendesak dari data periode ini.");
        }
        return recommendations.stream().limit(MAX_RECOMMENDATIONS).toList();
    }

    private static Integrity integrity(PeriodReportData data, long expenseTotal) {
        int outOfPeriod = 0;
        int periodMismatch = 0;
        LocalDateTime lower = data.startDate().atStartOfDay();
        LocalDateTime upper = data.endDate().plusDays(1).atStartOfDay();
        for (ExpenseData expense : data.expenses()) {
            if (!data.period().equals(expense.period())) {
                periodMismatch++;
            }
            try {
                LocalDateTime dateTime = PeriodSheetName.parseLenient(expense.dateTime());
                if (dateTime.isBefore(lower) || !dateTime.isBefore(upper)) {
                    outOfPeriod++;
                }
            } catch (DateTimeParseException e) {
                outOfPeriod++;
            }
        }
        boolean reconciled = expenseTotal == data.databaseExpenseTotal()
                && data.expenses().size() == data.databaseTransactionCount();
        return new Integrity(reconciled, outOfPeriod, periodMismatch);
    }

    private static BudgetStatus budgetStatus(long balance, long alertThreshold) {
        if (balance < 0) {
            return BudgetStatus.NEGATIVE;
        }
        if (alertThreshold > 0 && balance < alertThreshold) {
            return BudgetStatus.LOW;
        }
        return alertThreshold > 0 ? BudgetStatus.OK : BudgetStatus.NO_THRESHOLD;
    }

    private static int budgetPriority(BudgetStatus status) {
        return switch (status) {
            case NEGATIVE -> 0;
            case LOW -> 1;
            case NO_THRESHOLD -> 2;
            case OK -> 3;
        };
    }

    private static boolean isUncategorized(ExpenseData expense) {
        return expense.categoryName() == null || expense.categoryName().isBlank();
    }

    private static String categoryName(ExpenseData expense) {
        return isUncategorized(expense) ? TANPA_KATEGORI : expense.categoryName().trim();
    }

    static BigDecimal percent(long amount, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
