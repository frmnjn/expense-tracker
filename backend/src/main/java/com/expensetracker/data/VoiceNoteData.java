package com.expensetracker.data;

public record VoiceNoteData(
        String id,
        String period,
        String referenceDate,
        String filePath,
        String mimeType,
        Integer durationMs,
        String createdAt,
        String status,
        int retryCount,
        int retryMax,
        String aiProvider) {
}
