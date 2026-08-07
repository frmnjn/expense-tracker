package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TopUpRequest(
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("budget") String budget,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description) {
}
