package com.expensetracker.service;

public enum InvoiceStatus {
    ANALYZING,
    TO_REVIEW,
    SUBMITTED,
    ERROR;

    public String value() {
        return name();
    }
}
