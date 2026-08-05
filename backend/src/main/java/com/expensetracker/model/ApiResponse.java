package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse(boolean success, String message) {

    public static ApiResponse ok() {
        return new ApiResponse(true, null);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(false, message);
    }
}
