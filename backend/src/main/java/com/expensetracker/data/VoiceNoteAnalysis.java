package com.expensetracker.data;

public record VoiceNoteAnalysis(
        String id,
        String status,
        String analysisJson,
        String errorMessage,
        int retryCount,
        int retryMax,
        String aiProvider) {
}
