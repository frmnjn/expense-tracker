package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TopUpResponse(
        @JsonProperty("id") String id,
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("budget") String budget,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description) {
}
