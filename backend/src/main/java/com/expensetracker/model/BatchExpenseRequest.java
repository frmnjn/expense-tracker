package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BatchExpenseRequest(
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("voiceNoteId") String voiceNoteId,
        @JsonProperty("groups") List<BatchExpenseItem> groups) {

    public BatchExpenseRequest(String dateTime, String invoiceId, List<BatchExpenseItem> groups) {
        this(dateTime, invoiceId, null, groups);
    }
}
