package com.expensetracker.data;

public record ExpenseData(
        String id,
        String period,
        String dateTime,
        String name,
        String budgetName,
        long amount,
        String description,
        boolean deleted,
        boolean hasPhoto,
        String invoiceId,
        String photoType,
        String photoName,
        String categoryName,
        String voiceNoteId) {

    public ExpenseData(String id, String period, String dateTime, String name, String budgetName, long amount,
                       String description, boolean deleted, boolean hasPhoto, String invoiceId, String photoType,
                       String photoName) {
        this(id, period, dateTime, name, budgetName, amount, description, deleted, hasPhoto, invoiceId, photoType,
                photoName, null, null);
    }

    public ExpenseData(String id, String period, String dateTime, String name, String budgetName, long amount,
                       String description, boolean deleted, boolean hasPhoto, String invoiceId, String photoType,
                       String photoName, String categoryName) {
        this(id, period, dateTime, name, budgetName, amount, description, deleted, hasPhoto, invoiceId, photoType,
                photoName, categoryName, null);
    }
}
