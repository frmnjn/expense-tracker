package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseResponse(
        @JsonProperty("id") String id,
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("name") String name,
        @JsonProperty("budget") String budget,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description,
        @JsonProperty("hasPhoto") boolean hasPhoto,
        @JsonProperty("photoType") String photoType) {
}
