package com.expensetracker.data;

public record InvoiceAnalysis(
        String id,
        String status,
        String analysisJson,
        String errorMessage,
        int retryCount,
        int retryMax) {
    public InvoiceAnalysis(String id, String status, String analysisJson, String errorMessage) {
        this(id, status, analysisJson, errorMessage, 0, 0);
    }
}
