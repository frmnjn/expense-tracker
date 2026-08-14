package com.expensetracker.service;

public enum InvoiceStatus {
    ANALYZING,
    TO_REVIEW,
    SUBMITTED,
    ERROR,
    NOT_INVOICE;

    public String value() {
        return name();
    }
}
