package com.expensetracker.data;

public record InvoiceAnalysis(
        String id,
        String status,
        String analysisJson,
        String errorMessage,
        int retryCount,
        int retryMax,
        String aiProvider) {
    public InvoiceAnalysis(String id, String status, String analysisJson, String errorMessage) {
        this(id, status, analysisJson, errorMessage, 0, 0, null);
    }

    public InvoiceAnalysis(String id, String status, String analysisJson, String errorMessage,
                           int retryCount, int retryMax) {
        this(id, status, analysisJson, errorMessage, retryCount, retryMax, null);
    }
}
