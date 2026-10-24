package com.expensetracker.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class PeriodSheetName {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FORMATTER_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Parse datetime, menerima format menit (yyyy-MM-dd HH:mm) maupun penuh
     * sampai detik (yyyy-MM-dd HH:mm:ss). Melempar DateTimeParseException bila keduanya gagal.
     */
    public static LocalDateTime parseLenient(String value) {
        if (value == null) {
            throw new DateTimeParseException("null", "", 0);
        }
        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(value, FORMATTER_SECONDS);
        }
    }

    private PeriodSheetName() {
    }
}
