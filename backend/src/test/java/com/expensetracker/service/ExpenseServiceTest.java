package com.expensetracker.service;

import com.expensetracker.google.GoogleSheetsClient;
import com.expensetracker.model.ExpenseRef;
import com.expensetracker.model.ExpenseRequest;
import com.expensetracker.model.ExpenseResponse;
import com.expensetracker.model.TopUpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private static ExpenseRef expenseRef() {
        return new ExpenseRef("2026-JUL-AUG", 2,
                new ExpenseResponse("id123", "2026-08-06 14:30", "Makan Siang", "Daily", 35000L, null));
    }

    @Test
    void updateExpense_changeAmount_sameBudget_shouldAdjustBalanceByDelta() {
        when(googleSheetsClient.findExpense("id123")).thenReturn(expenseRef());
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", "Makan Siang", "Daily", 20000L, null);
        assertDoesNotThrow(() -> expenseService.updateExpense("id123", request));
        verify(googleSheetsClient).adjustBudgetBalance("Daily", 15000L);
        verify(googleSheetsClient).updateExpenseRow(eq("2026-JUL-AUG"), eq(2), eq("2026-08-06 14:30"),
                eq("Makan Siang"), eq("Daily"), eq(20000L), eq(null));
    }

    @Test
    void updateExpense_changeBudget_shouldMoveBalanceBetweenBudgets() {
        when(googleSheetsClient.findExpense("id123")).thenReturn(expenseRef());
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", "Makan Siang", "Weekly", 35000L, null);
        assertDoesNotThrow(() -> expenseService.updateExpense("id123", request));
        verify(googleSheetsClient).adjustBudgetBalance("Daily", 35000L);
        verify(googleSheetsClient).adjustBudgetBalance("Weekly", -35000L);
    }

    @Test
    void updateExpense_notFound_shouldReject() {
        when(googleSheetsClient.findExpense("missing")).thenReturn(null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.updateExpense("missing", validRequest()));
        assertEquals("Expense not found", ex.getMessage());
    }

    @Test
    void deleteExpense_shouldReturnAmountToBudgetAndSoftDelete() {
        when(googleSheetsClient.findExpense("id123")).thenReturn(expenseRef());
        assertDoesNotThrow(() -> expenseService.deleteExpense("id123"));
        verify(googleSheetsClient).adjustBudgetBalance("Daily", 35000L);
        verify(googleSheetsClient).softDeleteExpense("2026-JUL-AUG", 2);
    }

    @Test
    void getExpenses_missingPeriod_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.getExpenses(""));
        assertEquals("Period is required", ex.getMessage());
    }

    @Test
    void getSummary_shouldAggregateTotalAndByBudget() {
        when(googleSheetsClient.getExpenses("2026-JUL-AUG")).thenReturn(List.of(
                new ExpenseResponse("1", "2026-08-01 09:00", "a", "Daily", 1000L, null),
                new ExpenseResponse("2", "2026-08-02 09:00", "b", "Daily", 2000L, null),
                new ExpenseResponse("3", "2026-08-03 09:00", "c", "Weekly", 5000L, null)));
        var summary = expenseService.getSummary("2026-JUL-AUG");
        assertEquals(8000L, summary.total());
        assertEquals(3, summary.count());
        assertEquals("Weekly", summary.byBudget().get(0).budget());
        assertEquals(5000L, summary.byBudget().get(0).amount());
        assertEquals(1, summary.byBudget().get(0).count());
        assertEquals("Daily", summary.byBudget().get(1).budget());
        assertEquals(3000L, summary.byBudget().get(1).amount());
        assertEquals(2, summary.byBudget().get(1).count());
    }

    @Test
    void getSummary_missingPeriod_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.getSummary(""));
        assertEquals("Period is required", ex.getMessage());
    }

    @Test
    void createTopUp_validRequest_shouldAppendAndIncreaseBalance() {
        assertDoesNotThrow(() -> expenseService.createTopUp(
                new TopUpRequest("2026-08-07 10:00", "Daily", 50000L, "Gaji")));
        verify(googleSheetsClient).appendTopUp("2026-08-07 10:00", "Daily", 50000L, "Gaji");
        verify(googleSheetsClient).adjustBudgetBalance("Daily", 50000L);
    }

    @Test
    void createTopUp_missingBudget_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.createTopUp(new TopUpRequest(null, "", 50000L, null)));
        assertEquals("Budget is required", ex.getMessage());
    }

    @Test
    void createTopUp_zeroAmount_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.createTopUp(new TopUpRequest(null, "Daily", 0L, null)));
        assertEquals("Amount must be greater than 0", ex.getMessage());
    }

    @Test
    void getTrend_shouldReturnNewestPeriodsAscending() {
        when(googleSheetsClient.getPeriodSheetTitles()).thenReturn(List.of("2026-AUG-SEP", "2026-JUL-AUG"));
        when(googleSheetsClient.getExpenses("2026-JUL-AUG")).thenReturn(List.of(
                new ExpenseResponse("1", "2026-08-01 09:00", "a", "Daily", 1000L, null),
                new ExpenseResponse("2", "2026-08-02 09:00", "b", "Daily", 2000L, null)));
        when(googleSheetsClient.getExpenses("2026-AUG-SEP")).thenReturn(List.of(
                new ExpenseResponse("3", "2026-09-01 09:00", "c", "Daily", 5000L, null)));
        var trend = expenseService.getTrend(3);
        assertEquals(2, trend.periods().size());
        assertEquals("2026-JUL-AUG", trend.periods().get(0).period());
        assertEquals(3000L, trend.periods().get(0).total());
        assertEquals(2, trend.periods().get(0).count());
        assertEquals("2026-AUG-SEP", trend.periods().get(1).period());
        assertEquals(5000L, trend.periods().get(1).total());
    }

    @Test
    void getTrend_invalidMonths_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.getTrend(0));
        assertEquals("Months must be greater than 0", ex.getMessage());
    }
}
