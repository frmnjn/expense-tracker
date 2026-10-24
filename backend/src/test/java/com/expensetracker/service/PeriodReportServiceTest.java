package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.PeriodReportRepository;
import com.expensetracker.data.TopUpRepository;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.PeriodReportData;
import com.expensetracker.model.PeriodReportStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodReportServiceTest {

    @Mock
    private PeriodService periodService;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private TopUpRepository topUpRepository;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private PeriodReportRepository periodReportRepository;
    @Mock
    private PeriodReportPdfRenderer renderer;
    @Mock
    private NotificationService notificationService;

    @TempDir
    private Path tempDir;

    private PeriodReportService service;

    @BeforeEach
    void setUp() {
        when(periodService.calendar()).thenReturn(new PaydayCalendar(Map.of()));
        Clock clock = Clock.fixed(Instant.parse("2026-10-22T17:30:00Z"), ZoneId.of("Asia/Jakarta"));
        service = new PeriodReportService(periodService, expenseRepository, topUpRepository, budgetRepository,
                periodReportRepository, renderer, notificationService, clock, tempDir.toString(), true, 1);
    }

    @Test
    void newPeriod_shouldGenerateAndSendOnce() {
        ExpenseData expense = new ExpenseData("e1", "2026-SEP-OCT", "2026-10-22 18:09", "Makan Siang", "Daily",
                35000, "Nasi padang", false, false, null, null, null, "Eating Out", null);
        stubReportData("2026-SEP-OCT", List.of(expense));

        service.generateIfDue(LocalDate.of(2026, 10, 23));
        when(periodReportRepository.exists("2026-SEP-OCT")).thenReturn(true);
        service.generateIfDue(LocalDate.of(2026, 10, 23));

        verify(periodReportRepository, times(1)).insert("2026-SEP-OCT", "report-2026-SEP-OCT.pdf", 9);
        verify(notificationService, times(1)).sendPeriodReport(eq("2026-SEP-OCT"), anyString(), eq(35000L),
                eq(1), eq(0L), any(byte[].class), eq("report-2026-SEP-OCT.pdf"));
        Path saved = tempDir.resolve("reports/report-2026-SEP-OCT.pdf");
        assertTrue(Files.exists(saved));

        ArgumentCaptor<PeriodReportData> data = ArgumentCaptor.forClass(PeriodReportData.class);
        verify(renderer).render(data.capture(), any(PeriodReportStatistics.class));
        assertEquals("Asia/Jakarta", data.getValue().timeZone());
        assertEquals(LocalDate.of(2026, 10, 22), data.getValue().endDate());
        assertEquals(35000, data.getValue().databaseExpenseTotal());
        assertEquals(1, data.getValue().databaseTransactionCount());
    }

    @Test
    void scheduledGenerate_shouldUseJakartaDate() {
        stubReportData("2026-SEP-OCT", List.of(new ExpenseData("e1", "2026-SEP-OCT", "2026-10-22 18:09",
                "Makan Siang", "Daily", 35000, "", false, false, null, null, null, "Eating Out", null)));

        service.scheduledGenerate();

        verify(notificationService, times(1)).sendPeriodReport(eq("2026-SEP-OCT"), anyString(), eq(35000L),
                eq(1), eq(0L), any(byte[].class), eq("report-2026-SEP-OCT.pdf"));
    }

    @Test
    void beforeNewPeriod_shouldNotSend() {
        service.generateIfDue(LocalDate.of(2026, 10, 22));

        verifyNoInteractions(expenseRepository, topUpRepository, budgetRepository, renderer,
                periodReportRepository, notificationService);
    }

    @Test
    void outsideGracePeriod_shouldNotSend() {
        service.generateIfDue(LocalDate.of(2026, 10, 26));

        verifyNoInteractions(expenseRepository, topUpRepository, budgetRepository, renderer,
                periodReportRepository, notificationService);
    }

    @Test
    void noTransactions_shouldNotSend() {
        when(periodReportRepository.exists("2026-SEP-OCT")).thenReturn(false);
        when(expenseRepository.getExpenses("2026-SEP-OCT")).thenReturn(List.of());
        when(topUpRepository.findByDateRange(any(), any())).thenReturn(List.of());

        service.generateIfDue(LocalDate.of(2026, 10, 23));

        verifyNoInteractions(renderer, notificationService);
        verify(periodReportRepository, never()).insert(anyString(), anyString(), anyLong());
        verify(notificationService, never()).sendPeriodReport(anyString(), anyString(), anyLong(),
                anyInt(), anyLong(), any(byte[].class), anyString());
    }

    private void stubReportData(String period, List<ExpenseData> expenses) {
        when(periodReportRepository.exists(period)).thenReturn(false);
        when(expenseRepository.getExpenses(period)).thenReturn(expenses);
        when(topUpRepository.findByDateRange(any(), any())).thenReturn(List.of());
        when(budgetRepository.getOptions()).thenReturn(List.of(new BudgetOption("Daily", 65000, 0, null)));
        when(expenseRepository.totalForPeriod(period))
                .thenReturn(expenses.stream().mapToLong(ExpenseData::amount).sum());
        when(expenseRepository.countForPeriod(period)).thenReturn(expenses.size());
        byte[] pdf = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        when(renderer.render(any(PeriodReportData.class), any(PeriodReportStatistics.class))).thenReturn(pdf);
        when(periodReportRepository.insert(eq(period), anyString(), eq((long) pdf.length))).thenReturn(true);
    }
}
