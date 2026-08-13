package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceResponse(
        @JsonProperty("id") String id,
        @JsonProperty("createdAt") String createdAt,
        @JsonProperty("status") String status,
        @JsonProperty("type") String type,
        @JsonProperty("name") String name) {
}
