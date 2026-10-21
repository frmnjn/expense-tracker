package com.expensetracker.service;

public enum VoiceNoteStatus {
    ANALYZING,
    TO_REVIEW,
    SUBMITTED,
    ERROR,
    NOT_EXPENSE;

    public String value() {
        return name();
    }
}
