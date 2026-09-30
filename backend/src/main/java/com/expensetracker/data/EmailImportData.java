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
        String suggestedCategory,
        String parseMethod,
        String status,
        String errorMessage,
        String expenseId,
        String createdAt) {

    public EmailImportData(String id, String messageId, String sender, String subject, String receivedAt,
                           String transactionAt, String merchant, Long amount, String description,
                           String suggestedBudget, String parseMethod, String status, String errorMessage,
                           String expenseId, String createdAt) {
        this(id, messageId, sender, subject, receivedAt, transactionAt, merchant, amount, description,
                suggestedBudget, null, parseMethod, status, errorMessage, expenseId, createdAt);
    }
}
