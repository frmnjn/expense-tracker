package com.expensetracker.service;

import com.expensetracker.google.GoogleSheetsClient;
import com.expensetracker.model.ExpenseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private GoogleSheetsClient googleSheetsClient;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(googleSheetsClient);
    }

    private static ExpenseRequest validRequest() {
        return new ExpenseRequest("2026-08-06 14:30", "Makan Siang", "Daily", 35000L, null);
    }

    @Test
    void createExpense_validRequest_shouldAppendToCorrectPeriodSheet() {
        assertDoesNotThrow(() -> expenseService.createExpense(validRequest()));
        verify(googleSheetsClient).appendExpense(
                eq("2026-JUL-AUG"), eq("2026-08-06 14:30"), eq("Makan Siang"),
                eq("Daily"), eq(35000L), eq(null));
    }

    @Test
    void createExpense_validRequest_shouldDecrementBudget() {
        assertDoesNotThrow(() -> expenseService.createExpense(validRequest()));
        verify(googleSheetsClient).decrementBudget("Daily", 35000L);
    }

    @Test
    void createExpense_dateTimeOnCutoff_shouldUseNextPeriodSheet() {
        ExpenseRequest request = new ExpenseRequest("2026-08-25 08:00", "Makan Siang", "Daily", 35000L, null);
        expenseService.createExpense(request);
        verify(googleSheetsClient).appendExpense(
                eq("2026-AUG-SEP"), any(), any(), any(), anyLong(), any());
    }

    @Test
    void createExpense_missingDateTime_shouldReject() {
        ExpenseRequest request = new ExpenseRequest(null, "Makan Siang", "Daily", 35000L, null);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("DateTime is required", ex.getMessage());
        verify(googleSheetsClient, never()).appendExpense(any(), any(), any(), any(), anyLong(), any());
        verify(googleSheetsClient, never()).decrementBudget(any(), anyLong());
    }

    @Test
    void createExpense_invalidDateTimeFormat_shouldReject() {
        ExpenseRequest request = new ExpenseRequest("06-08-2026", "Makan Siang", "Daily", 35000L, null);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("DateTime must be in yyyy-MM-dd HH:mm format", ex.getMessage());
    }

    @Test
    void createExpense_missingName_shouldReject() {
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", null, "Daily", 35000L, null);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("Name is required", ex.getMessage());
    }

    @Test
    void createExpense_nameTooLong_shouldReject() {
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", "x".repeat(256), "Daily", 35000L, null);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("Name must be at most 255 characters", ex.getMessage());
    }

    @Test
    void createExpense_missingBudget_shouldReject() {
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", "Makan Siang", null, 35000L, null);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("Budget is required", ex.getMessage());
    }

    @Test
    void createExpense_missingAmount_shouldReject() {
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", "Makan Siang", "Daily", null, null);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("Amount is required", ex.getMessage());
    }

    @Test
    void createExpense_zeroAmount_shouldReject() {
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", "Makan Siang", "Daily", 0L, null);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("Amount must be greater than 0", ex.getMessage());
    }

    @Test
    void createExpense_descriptionTooLong_shouldReject() {
        ExpenseRequest request = new ExpenseRequest(
                "2026-08-06 14:30", "Makan Siang", "Daily", 35000L, "x".repeat(256));
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("Description must be at most 255 characters", ex.getMessage());
    }
}
