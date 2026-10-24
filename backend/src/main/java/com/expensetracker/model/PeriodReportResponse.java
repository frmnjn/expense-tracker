package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PeriodReportResponse(
        @JsonProperty("period") String period,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("sizeBytes") long sizeBytes,
        @JsonProperty("generatedAt") String generatedAt) {
}
