package com.expensetracker.data;

public record InvoiceData(
        String id,
        String period,
        String photoPath,
        String createdAt,
        String status,
        String originalName,
        int retryCount,
        int retryMax) {
    public InvoiceData(String id, String period, String photoPath, String createdAt, String status, String originalName) {
        this(id, period, photoPath, createdAt, status, originalName, 0, 0);
    }
}
