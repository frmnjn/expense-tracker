package com.expensetracker.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Menentukan periode berdasarkan tanggal gajian. Tanggal gajian default adalah
 * tanggal 25, digeser mundur ke hari kerja sebelumnya bila jatuh pada Sabtu atau
 * Minggu. Tanggal merah disesuaikan melalui override manual per periode.
 */
public final class PaydayCalendar {

    private static final int DEFAULT_PAYDAY_DAY = 25;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_PARSE_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM")
            .toFormatter(Locale.ENGLISH);

    private final Map<String, LocalDate> overrides;

    public PaydayCalendar(Map<String, LocalDate> overrides) {
        this.overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
    }

    public Map<String, LocalDate> overrides() {
        return overrides;
    }

    public boolean isOverridden(String period) {
        return overrides.containsKey(period);
    }

    public static String periodName(YearMonth month) {
        YearMonth next = month.plusMonths(1);
        return month.getYear() + "-" + MONTH_FORMAT.format(month).toUpperCase(Locale.ENGLISH) + "-"
                + MONTH_FORMAT.format(next).toUpperCase(Locale.ENGLISH);
    }

    public static Optional<YearMonth> parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return Optional.empty();
        }
        String[] parts = period.split("-");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            int year = Integer.parseInt(parts[0]);
            Month month = Month.from(MONTH_PARSE_FORMAT.parse(parts[1]));
            Month next = Month.from(MONTH_PARSE_FORMAT.parse(parts[2]));
            YearMonth result = YearMonth.of(year, month);
            if (!next.equals(result.plusMonths(1).getMonth())) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public LocalDate paydayForPeriod(String period) {
        YearMonth month = parsePeriod(period)
                .orElseThrow(() -> new IllegalArgumentException("Invalid period: " + period));
        LocalDate override = overrides.get(period);
        if (override != null) {
            return override;
        }
        return defaultPayday(month);
    }

    public static LocalDate defaultPayday(YearMonth month) {
        LocalDate payday = month.atDay(DEFAULT_PAYDAY_DAY);
        while (payday.getDayOfWeek() == DayOfWeek.SATURDAY || payday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            payday = payday.minusDays(1);
        }
        return payday;
    }

    /** Nama periode yang memuat tanggal tersebut. */
    public String forDate(LocalDate date) {
        String current = periodName(YearMonth.from(date));
        if (!date.isBefore(paydayForPeriod(current))) {
            return current;
        }
        return periodName(YearMonth.from(date).minusMonths(1));
    }

    public LocalDate periodStart(LocalDate date) {
        return paydayForPeriod(forDate(date));
    }

    public LocalDate periodEnd(LocalDate date) {
        return periodEnd(forDate(date));
    }

    public LocalDate periodEnd(String period) {
        YearMonth month = parsePeriod(period)
                .orElseThrow(() -> new IllegalArgumentException("Invalid period: " + period));
        return paydayForPeriod(periodName(month.plusMonths(1)));
    }

    public String nextPeriod(String period) {
        YearMonth month = parsePeriod(period)
                .orElseThrow(() -> new IllegalArgumentException("Invalid period: " + period));
        return periodName(month.plusMonths(1));
    }

    public Optional<LocalDate> parseStartDate(String period) {
        return parsePeriod(period).map(month -> paydayForPeriod(periodName(month)));
    }
}
