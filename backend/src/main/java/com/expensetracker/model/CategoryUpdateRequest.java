package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryUpdateRequest(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description) {
}
