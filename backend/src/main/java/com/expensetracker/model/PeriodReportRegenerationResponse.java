package com.expensetracker.model;

import java.util.List;

public record PeriodReportRegenerationResponse(
        List<String> regenerated,
        List<String> failed) {
}
