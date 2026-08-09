package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BudgetUpdateRequest(
        @JsonProperty("name") String name,
        @JsonProperty("balance") Long balance,
        @JsonProperty("alertThreshold") Long alertThreshold) {
}
