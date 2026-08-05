package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExpenseRequest(
        @JsonProperty("date") String date,
        @JsonProperty("description") String description,
        @JsonProperty("amount") Long amount) {
}
