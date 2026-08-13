package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BatchExpenseRequest(
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("groups") List<BatchExpenseItem> groups) {
}
