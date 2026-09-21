package com.expensetracker.data;

public record EmailImportData(
        String id,
        String messageId,
        String sender,
        String subject,
        String receivedAt,
        String transactionAt,
        String merchant,
        Long amount,
        String description,
        String suggestedBudget,
        String parseMethod,
        String status,
        String errorMessage,
        String expenseId,
        String createdAt) {
}
