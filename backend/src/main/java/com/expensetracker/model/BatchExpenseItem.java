package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BatchExpenseItem(
        @JsonProperty("name") String name,
        @JsonProperty("budget") String budget,
        @JsonProperty("category") String category,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description) {

    public BatchExpenseItem(String name, String budget, Long amount, String description) {
        this(name, budget, null, amount, description);
    }
}
