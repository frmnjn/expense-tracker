package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BatchExpenseItem(
        @JsonProperty("name") String name,
        @JsonProperty("budget") String budget,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description) {
}
