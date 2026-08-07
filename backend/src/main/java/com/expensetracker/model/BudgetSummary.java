package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BudgetSummary(
        @JsonProperty("budget") String budget,
        @JsonProperty("amount") Long amount) {
}
