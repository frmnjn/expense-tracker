package com.expensetracker.model;

import com.expensetracker.data.ExpenseData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PeriodReportData(
        String period,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime generatedAt,
        String timeZone,
        List<ExpenseData> expenses,
        List<TopUpResponse> topUps,
        List<BudgetOption> budgets,
        long expenseTotal,
        long topUpTotal,
        long databaseExpenseTotal,
        int databaseTransactionCount) {
}
