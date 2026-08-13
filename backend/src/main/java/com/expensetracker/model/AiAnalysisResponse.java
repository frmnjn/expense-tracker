package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiAnalysisResponse(
        @JsonProperty("storeName") String storeName,
        @JsonProperty("total") Long total,
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("items") List<AiInvoiceItem> items) {
}
