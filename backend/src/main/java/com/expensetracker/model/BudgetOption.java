package com.expensetracker.model;

public record BudgetOption(String name, long balance, long alertThreshold, String description) {
}
