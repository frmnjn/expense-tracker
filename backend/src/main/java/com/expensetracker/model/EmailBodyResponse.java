package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmailBodyResponse(
        @JsonProperty("body") String body,
        @JsonProperty("format") String format) {
}
