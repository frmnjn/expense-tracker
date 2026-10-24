package com.expensetracker.model;

import com.expensetracker.data.ExpenseData;

import java.math.BigDecimal;
import java.util.List;

public record PeriodReportStatistics(
        long expenseTotal,
        int transactionCount,
        long topUpTotal,
        int periodDays,
        long averageDailyExpense,
        List<CategoryTotal> categories,
        List<BudgetCategoryTotal> budgetCategories,
        List<BudgetStat> budgets,
        List<ExpenseData> largestTransactions,
        List<String> observations,
        List<String> recommendations,
        Integrity integrity) {

    public record CategoryTotal(
            String category,
            long amount,
            int count,
            int budgetCount,
            BigDecimal percent) {
    }

    public record BudgetCategoryTotal(
            String budget,
            String category,
            long amount,
            int count,
            BigDecimal percent) {
    }

    public enum BudgetStatus {
        NEGATIVE,
        LOW,
        OK,
        NO_THRESHOLD
    }

    public record BudgetStat(
            String budget,
            long openingBalance,
            long topUps,
            long expenses,
            int count,
            long netChange,
            long balance,
            long alertThreshold,
            BudgetStatus status) {
    }

    public record Integrity(
            boolean totalsReconciled,
            int outOfPeriodTransactions,
            int periodMismatches) {

        public boolean ok() {
            return totalsReconciled && outOfPeriodTransactions == 0 && periodMismatches == 0;
        }
    }
}
