package com.expensetracker.data;

public record InvoiceData(
        String id,
        String period,
        String photoPath,
        String createdAt,
        String status,
        String originalName,
        int retryCount,
        int retryMax,
        String aiProvider) {
    public InvoiceData(String id, String period, String photoPath, String createdAt, String status, String originalName) {
        this(id, period, photoPath, createdAt, status, originalName, 0, 0, null);
    }

    public InvoiceData(String id, String period, String photoPath, String createdAt, String status,
                       String originalName, int retryCount, int retryMax) {
        this(id, period, photoPath, createdAt, status, originalName, retryCount, retryMax, null);
    }
}
