package com.expensetracker.service;

public enum EmailImportStatus {
    PENDING_REVIEW,
    IMPORTED,
    DISCARDED,
    FAILED;

    public String value() {
        return name();
    }
}
