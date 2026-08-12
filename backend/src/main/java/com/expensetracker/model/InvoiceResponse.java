package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InvoiceResponse(
        @JsonProperty("id") String id,
        @JsonProperty("createdAt") String createdAt) {
}
