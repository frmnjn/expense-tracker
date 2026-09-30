package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BudgetSummary(
        @JsonProperty("budget") String budget,
        @JsonProperty("amount") Long amount,
        @JsonProperty("count") int count,
        @JsonProperty("categories") List<CategorySummary> categories) {

    public BudgetSummary(String budget, Long amount, int count) {
        this(budget, amount, count, List.of());
    }
}
