package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SummaryResponse(
        @JsonProperty("period") String period,
        @JsonProperty("total") Long total,
        @JsonProperty("count") int count,
        @JsonProperty("byBudget") List<BudgetSummary> byBudget) {
}
