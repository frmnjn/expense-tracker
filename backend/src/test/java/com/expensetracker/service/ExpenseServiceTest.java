package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.TopUpRepository;
import com.expensetracker.model.BudgetCreateRequest;
import com.expensetracker.model.BudgetUpdateRequest;
import com.expensetracker.model.ExpenseRequest;
import com.expensetracker.model.TopUpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private TopUpRepository topUpRepository;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(budgetRepository, expenseRepository, topUpRepository);
    }

    private static ExpenseRequest validRequest() {
        return new ExpenseRequest("2026-08-06 14:30", "Makan Siang", "Daily", 35000L, null);
    }

    private static ExpenseData expenseData() {
        return new ExpenseData("id123", "2026-JUL-AUG", "2026-08-06 14:30", "Makan Siang", "Daily",
                35000L, null, false, false);
    }

    @Test
    void createExpense_validRequest_shouldInsertAndDecrementBudget() {
        when(budgetRepository.findIdByName("Daily")).thenReturn(1L);
        assertDoesNotThrow(() -> expenseService.createExpense(validRequest()));
        verify(expenseRepository).insert(anyString(), eq("2026-JUL-AUG"), eq(LocalDate.of(2026, 7, 25)),
                eq(LocalDateTime.of(2026, 8, 6, 14, 30)), eq(1L), eq("Makan Siang"), eq(35000L), eq(null));
        verify(budgetRepository).adjustBalance("Daily", -35000L);
    }

    @Test
    void createExpense_dateTimeOnCutoff_shouldUseNextPeriod() {
        when(budgetRepository.findIdByName("Daily")).thenReturn(1L);
        ExpenseRequest request = new ExpenseRequest("2026-08-25 08:00", "Makan Siang", "Daily", 35000L, null);
        assertDoesNotThrow(() -> expenseService.createExpense(request));
        verify(expenseRepository).insert(anyString(), eq("2026-AUG-SEP"), eq(LocalDate.of(2026, 8, 25)),
                eq(LocalDateTime.of(2026, 8, 25, 8, 0)), anyLong(), anyString(), anyLong(), any());
    }

    @Test
    void createExpense_missingDateTime_shouldReject() {
        ExpenseRequest request = new ExpenseRequest(null, "Makan Siang", "Daily", 35000L, null);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(request));
        assertEquals("DateTime is required", ex.getMessage());
        verify(expenseRepository, never()).insert(any(), any(), any(), any(), anyLong(), any(), anyLong(), any());
        verify(budgetRepository, never()).adjustBalance(any(), anyLong());
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

    @Test
    void updateExpense_changeAmount_sameBudget_shouldAdjustBalanceByDelta() {
        when(expenseRepository.findById("id123")).thenReturn(expenseData());
        when(budgetRepository.findIdByName("Daily")).thenReturn(1L);
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", "Makan Siang", "Daily", 20000L, null);
        assertDoesNotThrow(() -> expenseService.updateExpense("id123", request));
        verify(budgetRepository).adjustBalance("Daily", 15000L);
        verify(expenseRepository).update(eq("id123"), eq(LocalDate.of(2026, 7, 25)),
                eq(LocalDateTime.of(2026, 8, 6, 14, 30)), eq(1L), eq("Makan Siang"), eq(20000L), eq(null));
    }

    @Test
    void updateExpense_changeBudget_shouldMoveBalanceBetweenBudgets() {
        when(expenseRepository.findById("id123")).thenReturn(expenseData());
        when(budgetRepository.findIdByName("Weekly")).thenReturn(2L);
        ExpenseRequest request = new ExpenseRequest("2026-08-06 14:30", "Makan Siang", "Weekly", 35000L, null);
        assertDoesNotThrow(() -> expenseService.updateExpense("id123", request));
        verify(budgetRepository).adjustBalance("Daily", 35000L);
        verify(budgetRepository).adjustBalance("Weekly", -35000L);
    }

    @Test
    void updateExpense_notFound_shouldReject() {
        when(expenseRepository.findById("missing")).thenReturn(null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.updateExpense("missing", validRequest()));
        assertEquals("Expense not found", ex.getMessage());
    }

    @Test
    void deleteExpense_shouldReturnAmountToBudgetAndSoftDelete() {
        when(expenseRepository.findById("id123")).thenReturn(expenseData());
        assertDoesNotThrow(() -> expenseService.deleteExpense("id123"));
        verify(budgetRepository).adjustBalance("Daily", 35000L);
        verify(expenseRepository).softDelete("id123");
    }

    @Test
    void getExpenses_missingPeriod_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.getExpenses(""));
        assertEquals("Period is required", ex.getMessage());
    }

    @Test
    void getSummary_shouldAggregateTotalAndByBudget() {
        when(expenseRepository.getExpenses("2026-JUL-AUG")).thenReturn(List.of(
                new ExpenseData("1", "2026-JUL-AUG", "2026-08-01 09:00", "a", "Daily", 1000L, null, false, false),
                new ExpenseData("2", "2026-JUL-AUG", "2026-08-02 09:00", "b", "Daily", 2000L, null, false, false),
                new ExpenseData("3", "2026-JUL-AUG", "2026-08-03 09:00", "c", "Weekly", 5000L, null, false, false)));
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
    void createTopUp_validRequest_shouldInsertAndIncreaseBalance() {
        when(budgetRepository.findIdByName("Daily")).thenReturn(1L);
        assertDoesNotThrow(() -> expenseService.createTopUp(
                new TopUpRequest("2026-08-07 10:00", "Daily", 50000L, "Gaji")));
        verify(topUpRepository).insert(anyString(), eq(LocalDateTime.of(2026, 8, 7, 10, 0)),
                eq(1L), eq(50000L), eq("Gaji"));
        verify(budgetRepository).adjustBalance("Daily", 50000L);
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
        when(expenseRepository.getPeriods()).thenReturn(List.of("2026-AUG-SEP", "2026-JUL-AUG"));
        when(expenseRepository.totalForPeriod("2026-JUL-AUG")).thenReturn(3000L);
        when(expenseRepository.countForPeriod("2026-JUL-AUG")).thenReturn(2);
        when(expenseRepository.totalForPeriod("2026-AUG-SEP")).thenReturn(5000L);
        when(expenseRepository.countForPeriod("2026-AUG-SEP")).thenReturn(1);
        var trend = expenseService.getTrend(3);
        assertEquals(2, trend.periods().size());
        assertEquals("2026-JUL-AUG", trend.periods().get(0).period());
        assertEquals(3000L, trend.periods().get(0).total());
        assertEquals("2026-AUG-SEP", trend.periods().get(1).period());
        assertEquals(5000L, trend.periods().get(1).total());
    }

    @Test
    void getTrend_invalidMonths_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.getTrend(0));
        assertEquals("Months must be greater than 0", ex.getMessage());
    }

    @Test
    void createBudget_validRequest_shouldCreateWithDefaultBalance() {
        assertDoesNotThrow(() -> expenseService.createBudget(new BudgetCreateRequest("Gadget", null)));
        verify(budgetRepository).create("Gadget", 0L);
    }

    @Test
    void createBudget_withBalance_shouldCreateWithBalance() {
        assertDoesNotThrow(() -> expenseService.createBudget(new BudgetCreateRequest("Gadget", 100000L)));
        verify(budgetRepository).create("Gadget", 100000L);
    }

    @Test
    void createBudget_missingName_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.createBudget(new BudgetCreateRequest("", null)));
        assertEquals("Name is required", ex.getMessage());
    }

    @Test
    void createBudget_duplicate_shouldReject() {
        doThrow(new IllegalStateException("dup")).when(budgetRepository).create("Gadget", 0L);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.createBudget(new BudgetCreateRequest("Gadget", null)));
        assertEquals("Budget already exists", ex.getMessage());
    }

    @Test
    void deleteBudget_found_shouldSoftDelete() {
        when(budgetRepository.softDelete("Gadget")).thenReturn(true);
        assertDoesNotThrow(() -> expenseService.deleteBudget("Gadget"));
        verify(budgetRepository).softDelete("Gadget");
    }

    @Test
    void deleteBudget_notFound_shouldReject() {
        when(budgetRepository.softDelete("Gadget")).thenReturn(false);
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.deleteBudget("Gadget"));
        assertEquals("Budget not found", ex.getMessage());
    }

    @Test
    void updateBudget_shouldRenameAndSetBalance() {
        when(budgetRepository.update("Gadget", "Gadget2", 200000L)).thenReturn(true);
        assertDoesNotThrow(() -> expenseService.updateBudget("Gadget", new BudgetUpdateRequest("Gadget2", 200000L)));
        verify(budgetRepository).update("Gadget", "Gadget2", 200000L);
    }

    @Test
    void updateBudget_notFound_shouldReject() {
        when(budgetRepository.update("Gadget", "Gadget2", null)).thenReturn(false);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.updateBudget("Gadget", new BudgetUpdateRequest("Gadget2", null)));
        assertEquals("Budget not found", ex.getMessage());
    }

    @Test
    void updateBudget_duplicateName_shouldReject() {
        when(budgetRepository.update("Gadget", "Household", null)).thenThrow(new IllegalStateException("dup"));
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.updateBudget("Gadget", new BudgetUpdateRequest("Household", null)));
        assertEquals("Budget already exists", ex.getMessage());
    }

    @Test
    void updateBudget_missingName_shouldReject() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> expenseService.updateBudget("Gadget", new BudgetUpdateRequest("", null)));
        assertEquals("Name is required", ex.getMessage());
    }
}
