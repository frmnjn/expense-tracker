package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceNoteDetailResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("analysis") VoiceAnalysisResponse analysis,
        @JsonProperty("retryCount") Integer retryCount,
        @JsonProperty("retryMax") Integer retryMax,
        @JsonProperty("provider") String provider,
        @JsonProperty("durationMs") Integer durationMs,
        @JsonProperty("expenses") List<ExpenseResponse> expenses) {
}
