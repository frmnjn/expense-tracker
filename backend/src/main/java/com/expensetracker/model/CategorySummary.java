package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategorySummary(
        @JsonProperty("category") String category,
        @JsonProperty("amount") Long amount,
        @JsonProperty("count") int count) {
}
