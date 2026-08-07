package com.expensetracker.model;

import java.util.List;

public record ExpensesResponse(List<ExpenseResponse> expenses) {
}
