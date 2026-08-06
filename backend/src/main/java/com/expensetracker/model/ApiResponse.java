package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse(boolean success, String message, Object data) {

    public static ApiResponse ok() {
        return new ApiResponse(true, null, null);
    }

    public static ApiResponse ok(Object data) {
        return new ApiResponse(true, null, data);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null);
    }
}
