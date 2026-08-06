package com.expensetracker.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodSheetNameTest {

    @Test
    void dateBeforeCutoff_shouldUsePreviousAndCurrentMonth() {
        assertEquals("2026-JUL-AUG", PeriodSheetName.forDate(LocalDate.of(2026, 8, 6)));
    }

    @Test
    void dateOnCutoff_shouldStartNewPeriod() {
        assertEquals("2026-AUG-SEP", PeriodSheetName.forDate(LocalDate.of(2026, 8, 25)));
    }

    @Test
    void firstDayOfMonth_shouldUsePreviousAndCurrentMonth() {
        assertEquals("2026-JUL-AUG", PeriodSheetName.forDate(LocalDate.of(2026, 8, 1)));
    }

    @Test
    void lastDayBeforeCutoff_shouldStayInCurrentPeriod() {
        assertEquals("2026-JUL-AUG", PeriodSheetName.forDate(LocalDate.of(2026, 8, 24)));
    }

    @Test
    void januaryBeforeCutoff_shouldRollBackToPreviousYear() {
        assertEquals("2025-DEC-JAN", PeriodSheetName.forDate(LocalDate.of(2026, 1, 5)));
    }

    @Test
    void januaryOnCutoff_shouldStartNextPeriod() {
        assertEquals("2026-JAN-FEB", PeriodSheetName.forDate(LocalDate.of(2026, 1, 25)));
    }

    @Test
    void decemberOnCutoff_shouldRollForwardToNextYear() {
        assertEquals("2026-DEC-JAN", PeriodSheetName.forDate(LocalDate.of(2026, 12, 25)));
    }

    @Test
    void parseStartDate_shouldReturnPeriodStartDate() {
        assertEquals(LocalDate.of(2026, 7, 25), PeriodSheetName.parseStartDate("2026-JUL-AUG").orElseThrow());
    }

    @Test
    void parseStartDate_shouldHandleYearRollover() {
        assertEquals(LocalDate.of(2025, 12, 25), PeriodSheetName.parseStartDate("2025-DEC-JAN").orElseThrow());
    }

    @Test
    void parseStartDate_invalidName_shouldReturnEmpty() {
        assertEquals(java.util.Optional.empty(), PeriodSheetName.parseStartDate("Budget"));
    }

    @Test
    void parseStartDate_nullOrBlank_shouldReturnEmpty() {
        assertEquals(java.util.Optional.empty(), PeriodSheetName.parseStartDate(null));
        assertEquals(java.util.Optional.empty(), PeriodSheetName.parseStartDate("  "));
    }
}
