package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExpenseRequest(
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("name") String name,
        @JsonProperty("budget") String budget,
        @JsonProperty("category") String category,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description,
        @JsonProperty("invoiceId") String invoiceId) {

    public ExpenseRequest(String dateTime, String name, String budget, Long amount, String description,
                          String invoiceId) {
        this(dateTime, name, budget, null, amount, description, invoiceId);
    }
}
