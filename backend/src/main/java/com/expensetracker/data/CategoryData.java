package com.expensetracker.data;

public record CategoryData(
        long id,
        long budgetId,
        String name,
        String description,
        boolean isActive) {
}
