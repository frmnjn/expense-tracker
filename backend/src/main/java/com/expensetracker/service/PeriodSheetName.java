package com.expensetracker.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

public final class PeriodSheetName {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_PARSE_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM")
            .toFormatter(Locale.ENGLISH);

    private PeriodSheetName() {
    }

    public static LocalDate periodStart(LocalDate date) {
        LocalDate start = date.getDayOfMonth() >= 25 ? date : date.minusMonths(1);
        return LocalDate.of(start.getYear(), start.getMonth(), 25);
    }

    public static String forDate(LocalDate date) {
        LocalDate start = periodStart(date);
        LocalDate end = start.plusMonths(1);
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
