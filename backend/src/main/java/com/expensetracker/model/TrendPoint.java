package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrendPoint(
        @JsonProperty("period") String period,
        @JsonProperty("total") Long total,
        @JsonProperty("count") int count) {
}
