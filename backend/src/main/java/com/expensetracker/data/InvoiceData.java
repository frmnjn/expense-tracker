package com.expensetracker.data;

public record InvoiceData(
        String id,
        String period,
        String photoPath,
        String createdAt) {
}
