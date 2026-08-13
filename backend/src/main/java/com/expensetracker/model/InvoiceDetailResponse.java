package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceDetailResponse(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("status") String status,
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("name") String name,
        @JsonProperty("analysis") AiAnalysisResponse analysis) {
}
