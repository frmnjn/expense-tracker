package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PeriodReportsResponse(
        @JsonProperty("reports") List<PeriodReportResponse> reports) {
}
