package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysisResponse(
        @JsonProperty("storeName") String storeName,
        @JsonProperty("total") Long total,
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("currency") String currency,
        @JsonProperty("exchangeRate") Double exchangeRate,
        @JsonProperty("exchangeDate") String exchangeDate,
        @JsonProperty("originalTotal") Double originalTotal,
        @JsonProperty("items") List<AiInvoiceItem> items) {
}
