package com.expensetracker.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

public final class PeriodSheetName {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_PARSE_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM")
            .toFormatter(Locale.ENGLISH);

    private PeriodSheetName() {
    }

    public static String forDate(LocalDate date) {
        LocalDate start;
        LocalDate end;
        if (date.getDayOfMonth() >= 25) {
            start = date;
            end = date.plusMonths(1);
        } else {
            start = date.minusMonths(1);
            end = date;
        }
        return start.getYear() + "-" + MONTH_FORMAT.format(start).toUpperCase(Locale.ENGLISH) + "-"
                + MONTH_FORMAT.format(end).toUpperCase(Locale.ENGLISH);
    }

    public static Optional<LocalDate> parseStartDate(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return Optional.empty();
        }
        String[] parts = sheetName.split("-");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            int year = Integer.parseInt(parts[0]);
            Month month = Month.from(MONTH_PARSE_FORMAT.parse(parts[1]));
            return Optional.of(LocalDate.of(year, month, 25));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
