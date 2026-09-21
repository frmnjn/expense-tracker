package com.expensetracker.service;

import com.expensetracker.data.EmailImportData;
import com.expensetracker.data.EmailImportRepository;
import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.model.ExpenseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailImportServiceTest {

    @Mock
    private EmailImportRepository emailImportRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private EmailInboxService emailInboxService;
    @Mock
    private EmailParserService emailParserService;
    @Mock
    private MerchantDiscardRule merchantDiscardRule;

    private EmailImportService service;

    @BeforeEach
    void setUp() {
        service = new EmailImportService(emailImportRepository, expenseRepository, expenseService,
                emailInboxService, emailParserService, merchantDiscardRule);
    }

    private static ExpenseRequest request(long amount) {
        return new ExpenseRequest("2026-09-20 12:00", "TOTAL BUAH SEGAR", "Belanja", amount, null, null);
    }

    private static EmailImportData importRow(String status) {
        return new EmailImportData("e1", "<msg-1@danamon>", "dbank.app@danamon.co.id",
                "Pembayaran QRIS Berhasil", "2026-09-20T10:00", "2026-09-20T12:00", "TOTAL BUAH SEGAR", 57_500L,
                "Pembayaran QRIS Berhasil", null, "REGEX", status, null, null, "2026-09-20T10:00");
    }

    private static ExpenseData existingExpense() {
        return new ExpenseData("x1", "2026-AUG-SEP", "2026-09-20 11:00", "TOTAL BUAH SEGAR",
                "Belanja", 57_500L, "", false, false, null, null, null);
    }

    @Test
    void rejectsImportWhenSameAmountExistsInPeriod() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("PENDING_REVIEW"));
        when(expenseRepository.findByPeriodAndAmount(anyString(), eq(57_500L)))
                .thenReturn(List.of(existingExpense()));

        assertThrows(DuplicateExpenseException.class,
                () -> service.importToExpense("e1", request(57_500L), false));

        verify(expenseService, never()).createExpense(any());
        verify(emailImportRepository, never()).markImported(anyString(), anyString());
    }

    @Test
    void forceSkipsDuplicateCheckAndImports() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("PENDING_REVIEW"));
        when(expenseService.createExpense(any())).thenReturn("exp-1");

        service.importToExpense("e1", request(57_500L), true);

        verify(expenseRepository, never()).findByPeriodAndAmount(anyString(), anyLong());
        verify(emailImportRepository).markImported("e1", "exp-1");
    }

    @Test
    void importsWhenNoMatchingExpense() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("PENDING_REVIEW"));
        when(expenseRepository.findByPeriodAndAmount(anyString(), eq(57_500L))).thenReturn(List.of());
        when(expenseService.createExpense(any())).thenReturn("exp-2");

        service.importToExpense("e1", request(57_500L), false);

        verify(emailImportRepository).markImported("e1", "exp-2");
    }

    @Test
    void rejectsImportWhenAlreadyProcessed() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("IMPORTED"));

        assertThrows(ValidationException.class,
                () -> service.importToExpense("e1", request(57_500L), false));

        verify(expenseService, never()).createExpense(any());
    }

    @Test
    void discardsFailedRow() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("FAILED"));

        service.discard("e1");

        verify(emailImportRepository).markDiscarded("e1");
    }

    @Test
    void retryFailedBecomesPendingReview() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("FAILED"));
        when(emailInboxService.fetchByMessageId("<msg-1@danamon>"))
                .thenReturn(new FetchedEmail("dbank.app@danamon.co.id", "<p>qris</p>"));
        when(emailParserService.parse(anyString(), anyString())).thenReturn(new ParsedTransaction(
                "TOTAL BUAH SEGAR", 57_500L, LocalDateTime.of(2026, 9, 20, 19, 56), "Belanja", "AI"));
        when(merchantDiscardRule.shouldDiscard("TOTAL BUAH SEGAR")).thenReturn(false);

        service.retry("e1");

        verify(emailImportRepository).updateParsed(eq("e1"), any(), eq("TOTAL BUAH SEGAR"), eq(57_500L),
                eq("Belanja"), eq("AI"), eq("PENDING_REVIEW"), isNull());
    }

    @Test
    void retryFailedDiscardedWhenNotExpense() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("FAILED"));
        when(emailInboxService.fetchByMessageId(anyString()))
                .thenReturn(new FetchedEmail("dbank.app@danamon.co.id", "<p>promo</p>"));
        when(emailParserService.parse(anyString(), anyString()))
                .thenThrow(new NotExpenseException("Bukan transaksi pengeluaran"));

        service.retry("e1");

        verify(emailImportRepository).updateParsed(eq("e1"), isNull(), isNull(), isNull(), isNull(),
                eq("AI"), eq("DISCARDED"), eq("Bukan transaksi pengeluaran"));
    }

    @Test
    void retryFailedDiscardedByMerchantRule() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("FAILED"));
        when(emailInboxService.fetchByMessageId(anyString()))
                .thenReturn(new FetchedEmail("KartuKreditBCA@klikbca.com", "<p>superindo</p>"));
        when(emailParserService.parse(anyString(), anyString())).thenReturn(new ParsedTransaction(
                "SUPERINDO CNE", 85_490L, LocalDateTime.of(2026, 9, 20, 12, 0), null, "REGEX"));
        when(merchantDiscardRule.shouldDiscard("SUPERINDO CNE")).thenReturn(true);

        service.retry("e1");

        verify(emailImportRepository).updateParsed(eq("e1"), any(), eq("SUPERINDO CNE"), eq(85_490L),
                isNull(), eq("REGEX"), eq("DISCARDED"), eq("Auto-discard: merchant SUPERINDO CNE"));
    }

    @Test
    void retryRejectedWhenNotFailed() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("PENDING_REVIEW"));

        assertThrows(ValidationException.class, () -> service.retry("e1"));

        verify(emailInboxService, never()).fetchByMessageId(anyString());
    }

    @Test
    void retryKeepsFailedWhenEmailNotFound() {
        when(emailImportRepository.findById("e1")).thenReturn(importRow("FAILED"));
        when(emailInboxService.fetchByMessageId(anyString())).thenReturn(null);

        assertThrows(ValidationException.class, () -> service.retry("e1"));

        verify(emailImportRepository).markFailed("e1", "Email tidak ditemukan di inbox");
    }

    @Test
    void listAllReturnsEveryStatus() {
        when(emailImportRepository.findAll())
                .thenReturn(List.of(importRow("PENDING_REVIEW"), importRow("FAILED")));

        List<EmailImportData> result = service.list("ALL");

        assertEquals(2, result.size());
        verify(emailImportRepository).findAll();
        verify(emailImportRepository, never()).findByStatus(anyString());
    }
}
