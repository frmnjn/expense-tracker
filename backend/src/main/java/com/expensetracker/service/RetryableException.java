package com.expensetracker.service;

/** Error transien dari Gemini (HTTP 429 / 5xx) yang layak di-retry. */
public class RetryableException extends Exception {

    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
