package com.expensetracker.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ReportFormat {

    private static final ThreadLocal<NumberFormat> RUPIAH =
            ThreadLocal.withInitial(() -> NumberFormat.getIntegerInstance(new Locale("id", "ID")));
    private static final Locale INDONESIAN = new Locale("id", "ID");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", INDONESIAN);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", INDONESIAN);

    private ReportFormat() {
    }

    public static String rupiah(long value) {
        String sign = value < 0 ? "-" : "";
        return sign + "Rp" + RUPIAH.get().format(Math.abs(value));
    }

    public static String percent(BigDecimal value) {
        return value.toPlainString() + "%";
    }

    public static String date(LocalDate value) {
        return DATE.format(value);
    }

    public static String dateTime(LocalDateTime value) {
        return DATE_TIME.format(value);
    }

    public static String expenseDateTime(String raw) {
        try {
            return DATE_TIME.format(PeriodSheetName.parseLenient(raw));
        } catch (RuntimeException e) {
            return raw == null ? "" : raw;
        }
    }
}
