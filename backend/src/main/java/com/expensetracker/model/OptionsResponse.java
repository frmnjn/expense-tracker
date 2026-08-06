package com.expensetracker.model;

import java.util.List;

public record OptionsResponse(List<String> budgets, List<String> banks) {
}
