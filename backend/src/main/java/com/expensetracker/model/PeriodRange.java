package com.expensetracker.model;

import java.util.List;

public record PeriodRange(String period, String start, String end, boolean overridden) {
}
