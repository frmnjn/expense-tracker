package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.PeriodReportRepository;
import com.expensetracker.data.TopUpRepository;
import com.expensetracker.model.PeriodReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodReportServiceListTest {

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
        Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneId.of("Asia/Jakarta"));
        service = new PeriodReportService(periodService, expenseRepository, topUpRepository, budgetRepository,
                periodReportRepository, renderer, notificationService, clock, tempDir.toString(), true, 1);
    }

    @Test
    void getReports_shouldSortByLatestPeriodNotGeneratedAt() {
        when(periodReportRepository.findAll()).thenReturn(List.of(
                new PeriodReportResponse("2026-AUG-SEP", "a.pdf", 100, "2026-10-24 06:40"),
                new PeriodReportResponse("2026-SEP-OCT", "b.pdf", 200, "2026-10-24 00:04"),
                new PeriodReportResponse("2026-OCT-NOV", "c.pdf", 300, "2026-10-23 12:00")));

        List<String> periods = service.getReports().reports().stream()
                .map(PeriodReportResponse::period)
                .toList();

        assertEquals(List.of("2026-OCT-NOV", "2026-SEP-OCT", "2026-AUG-SEP"), periods);
    }
}
