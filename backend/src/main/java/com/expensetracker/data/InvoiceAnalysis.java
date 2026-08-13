package com.expensetracker.data;

public record InvoiceAnalysis(
        String id,
        String status,
        String analysisJson,
        String errorMessage) {
}
