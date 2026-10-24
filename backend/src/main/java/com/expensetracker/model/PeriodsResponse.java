package com.expensetracker.model;

import java.util.List;

public record PeriodsResponse(List<String> periods, String currentPeriod, List<PeriodRange> calendar) {
}
