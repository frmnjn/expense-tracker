package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiInvoiceItem(
        @JsonProperty("name") String name,
        @JsonProperty("amount") Long amount,
        @JsonProperty("suggestedBudget") String suggestedBudget,
        @JsonProperty("suggestedCategory") String suggestedCategory) {

    public AiInvoiceItem(String name, Long amount, String suggestedBudget) {
        this(name, amount, suggestedBudget, null);
    }
}
