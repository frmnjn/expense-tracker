package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryCreateRequest(
        @JsonProperty("budgetName") String budgetName,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description) {
}
