package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceNoteResponse(
        @JsonProperty("id") String id,
        @JsonProperty("createdAt") String createdAt,
        @JsonProperty("status") String status,
        @JsonProperty("durationMs") Integer durationMs,
        @JsonProperty("retryCount") Integer retryCount,
        @JsonProperty("retryMax") Integer retryMax,
        @JsonProperty("provider") String provider) {
}
