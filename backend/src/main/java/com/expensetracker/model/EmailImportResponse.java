package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailImportResponse(
        @JsonProperty("id") String id,
        @JsonProperty("sender") String sender,
        @JsonProperty("subject") String subject,
        @JsonProperty("receivedAt") String receivedAt,
        @JsonProperty("transactionAt") String transactionAt,
        @JsonProperty("merchant") String merchant,
        @JsonProperty("amount") Long amount,
        @JsonProperty("description") String description,
        @JsonProperty("suggestedBudget") String suggestedBudget,
        @JsonProperty("parseMethod") String parseMethod,
        @JsonProperty("status") String status,
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("expenseId") String expenseId) {
}
