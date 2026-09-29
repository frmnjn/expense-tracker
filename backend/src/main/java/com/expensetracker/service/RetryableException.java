package com.expensetracker.service;

/** Error transien dari provider AI (HTTP 429 / 5xx) yang layak di-retry. */
public class RetryableException extends Exception {

    private final long retryAfterMillis;

    public RetryableException(String message) {
        this(message, 0L);
    }

    /** retryAfterMillis: saran jeda dari provider (0 bila tidak ada). */
    public RetryableException(String message, long retryAfterMillis) {
        super(message);
        this.retryAfterMillis = retryAfterMillis < 0 ? 0 : retryAfterMillis;
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
        this.retryAfterMillis = 0;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
