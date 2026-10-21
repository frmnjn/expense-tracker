package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VoiceAnalysisResponse(
        @JsonProperty("transcript") String transcript,
        @JsonProperty("isExpense") Boolean isExpense,
        @JsonProperty("storeName") String storeName,
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("items") List<AiInvoiceItem> items,
        @JsonProperty("name") String name,
        @JsonProperty("amount") Long amount,
        @JsonProperty("budget") String budget,
        @JsonProperty("category") String category,
        @JsonProperty("description") String description) {

    /** Kompatibilitas hasil analisis lama yang hanya berisi satu expense. */
    public VoiceAnalysisResponse(String transcript, Boolean isExpense, String name, Long amount, String dateTime,
                                 String budget, String category, String description) {
        this(transcript, isExpense, null, dateTime, null, name, amount, budget, category, description);
    }
}
