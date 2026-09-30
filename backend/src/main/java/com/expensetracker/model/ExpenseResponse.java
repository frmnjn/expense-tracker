package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseResponse(
        @JsonProperty("id") String id,
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("name") String name,
        @JsonProperty("budget") String budget,
        @JsonProperty("category") String category,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description,
        @JsonProperty("hasPhoto") boolean hasPhoto,
        @JsonProperty("photoType") String photoType,
        @JsonProperty("photoName") String photoName) {

    public ExpenseResponse(String id, String dateTime, String name, String budget, Long amount, String description,
                           boolean hasPhoto, String photoType, String photoName) {
        this(id, dateTime, name, budget, null, amount, description, hasPhoto, photoType, photoName);
    }
}
