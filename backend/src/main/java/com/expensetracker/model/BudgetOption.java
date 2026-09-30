package com.expensetracker.model;

import java.util.List;

public record BudgetOption(
        String name,
        long balance,
        long alertThreshold,
        String description,
        List<CategoryOption> categories) {

    public BudgetOption(String name, long balance, long alertThreshold, String description) {
        this(name, balance, alertThreshold, description, List.of());
    }
}
