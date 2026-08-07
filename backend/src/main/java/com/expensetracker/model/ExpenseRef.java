package com.expensetracker.model;

public record ExpenseRef(String sheetName, int rowIndex, ExpenseResponse expense) {
}
