package com.expensetracker.service;

/**
 * Kegagalan ekstraksi yang terjadi saat parsing dengan AI (setelah regex gagal).
 * Dipakai agar baris email disimpan dengan parseMethod "AI", bukan "REGEX".
 */
public class AiParseException extends ValidationException {

    public AiParseException(String message) {
        super(message);
    }

    public AiParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
