package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExpenseRequest(
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("name") String name,
        @JsonProperty("budget") String budget,
        @JsonProperty("bank") String bank,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description) {
}
