package com.expensetracker.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaydayCalendarTest {

    private final PaydayCalendar calendar = new PaydayCalendar(Map.of());

    @Test
    void dateBeforeCutoff_shouldUsePreviousAndCurrentMonth() {
        assertEquals("2026-JUL-AUG", calendar.forDate(LocalDate.of(2026, 8, 6)));
    }

    @Test
    void dateOnCutoff_shouldStartNewPeriod() {
        assertEquals("2026-AUG-SEP", calendar.forDate(LocalDate.of(2026, 8, 25)));
    }

    @Test
    void firstDayOfMonth_shouldUsePreviousAndCurrentMonth() {
        assertEquals("2026-JUL-AUG", calendar.forDate(LocalDate.of(2026, 8, 1)));
    }

    @Test
    void lastDayBeforeCutoff_shouldStayInCurrentPeriod() {
        assertEquals("2026-JUL-AUG", calendar.forDate(LocalDate.of(2026, 8, 24)));
    }

    @Test
    void januaryBeforeCutoff_shouldRollBackToPreviousYear() {
        assertEquals("2025-DEC-JAN", calendar.forDate(LocalDate.of(2026, 1, 5)));
    }

    @Test
    void januaryOnCutoff_shouldStartNextPeriod() {
        assertEquals("2026-JAN-FEB", calendar.forDate(LocalDate.of(2026, 1, 25)));
    }

    @Test
    void decemberOnCutoff_shouldRollForwardToNextYear() {
        assertEquals("2026-DEC-JAN", calendar.forDate(LocalDate.of(2026, 12, 25)));
    }

    @Test
    void saturdayPayday_shouldShiftBackToFriday() {
        // 25 Juli 2026 = Sabtu -> 24 Juli 2026 (Jumat)
        assertEquals(LocalDate.of(2026, 7, 24), calendar.parseStartDate("2026-JUL-AUG").orElseThrow());
        assertEquals("2026-JUL-AUG", calendar.forDate(LocalDate.of(2026, 7, 24)));
        assertEquals("2026-JUN-JUL", calendar.forDate(LocalDate.of(2026, 7, 23)));
    }

    @Test
    void sundayPayday_shouldShiftBackToFriday() {
        // 25 Oktober 2026 = Minggu -> 23 Oktober 2026 (Jumat)
        assertEquals(LocalDate.of(2026, 10, 23), calendar.parseStartDate("2026-OCT-NOV").orElseThrow());
        assertEquals("2026-OCT-NOV", calendar.forDate(LocalDate.of(2026, 10, 23)));
        assertEquals("2026-SEP-OCT", calendar.forDate(LocalDate.of(2026, 10, 22)));
    }

    @Test
    void override_shouldReplaceDefaultPayday() {
        PaydayCalendar overridden = new PaydayCalendar(Map.of(
                "2026-OCT-NOV", LocalDate.of(2026, 10, 21),
                "2026-NOV-DEC", LocalDate.of(2026, 11, 20)));
        assertTrue(overridden.isOverridden("2026-OCT-NOV"));
        assertFalse(overridden.isOverridden("2026-DEC-JAN"));
        assertEquals(LocalDate.of(2026, 10, 21), overridden.parseStartDate("2026-OCT-NOV").orElseThrow());
        assertEquals("2026-OCT-NOV", overridden.forDate(LocalDate.of(2026, 10, 21)));
        assertEquals("2026-SEP-OCT", overridden.forDate(LocalDate.of(2026, 10, 20)));
        assertEquals(LocalDate.of(2026, 11, 20), overridden.periodEnd("2026-OCT-NOV"));
        assertEquals(LocalDate.of(2026, 11, 20), overridden.parseStartDate("2026-NOV-DEC").orElseThrow());
    }


    @Test
    void previousPeriod_shouldMoveToPreviousMonth() {
        assertEquals("2026-SEP-OCT", calendar.previousPeriod("2026-OCT-NOV"));
    }

    @Test
    void previousPeriod_shouldHandleYearRollover() {
        assertEquals("2025-DEC-JAN", calendar.previousPeriod("2026-JAN-FEB"));
    }

    @Test
    void parseStartDate_shouldHandleYearRollover() {
        assertEquals(LocalDate.of(2025, 12, 25), calendar.parseStartDate("2025-DEC-JAN").orElseThrow());
    }

    @Test
    void parseStartDate_invalidName_shouldReturnEmpty() {
        assertEquals(java.util.Optional.empty(), calendar.parseStartDate("Budget"));
    }

    @Test
    void parseStartDate_nullOrBlank_shouldReturnEmpty() {
        assertEquals(java.util.Optional.empty(), calendar.parseStartDate(null));
        assertEquals(java.util.Optional.empty(), calendar.parseStartDate("  "));
    }
}
