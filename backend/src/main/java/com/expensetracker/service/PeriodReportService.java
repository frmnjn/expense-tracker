package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.PeriodReportRepository;
import com.expensetracker.data.TopUpRepository;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.PeriodReportData;
import com.expensetracker.model.PeriodReportResponse;
import com.expensetracker.model.PeriodReportStatistics;
import com.expensetracker.model.PeriodReportsResponse;
import com.expensetracker.model.TopUpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Membuat report PDF periode sebelumnya saat periode baru dimulai, menyimpannya
 * di uploads/reports, lalu mengirimnya ke email lewat notifier.
 */
@Service
public class PeriodReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodReportService.class);
    private static final DateTimeFormatter EMAIL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH);
    private final PeriodService periodService;
    private final ExpenseRepository expenseRepository;
    private final TopUpRepository topUpRepository;
    private final BudgetRepository budgetRepository;
    private final PeriodReportRepository periodReportRepository;
    private final PeriodReportPdfRenderer renderer;
    private final NotificationService notificationService;
    private final Clock clock;
    private final Path reportDirectory;
    private final boolean enabled;
    private final int graceDays;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PeriodReportService(PeriodService periodService,
                               ExpenseRepository expenseRepository,
                               TopUpRepository topUpRepository,
                               BudgetRepository budgetRepository,
                               PeriodReportRepository periodReportRepository,
                               PeriodReportPdfRenderer renderer,
                               NotificationService notificationService,
                               Clock clock,
                               @Value("${upload.dir:/app/uploads}") String uploadDir,
                               @Value("${report.enabled:true}") boolean enabled,
                               @Value("${report.grace-days:1}") int graceDays) {
        this.periodService = periodService;
        this.expenseRepository = expenseRepository;
        this.topUpRepository = topUpRepository;
        this.budgetRepository = budgetRepository;
        this.periodReportRepository = periodReportRepository;
        this.renderer = renderer;
        this.notificationService = notificationService;
        this.clock = clock;
        this.reportDirectory = Paths.get(uploadDir, "reports");
        this.enabled = enabled;
        this.graceDays = Math.max(0, graceDays);
    }

    @Scheduled(cron = "${report.cron:0 5 0 * * *}")
    public void scheduledGenerate() {
        if (!enabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("period report skipped: previous run still in progress");
            return;
        }
        try {
            generateIfDue(LocalDate.now(clock));
        } catch (Exception e) {
            LOGGER.error("period report failed", e);
        } finally {
            running.set(false);
        }
    }

    /** Publik agar bisa diuji tanpa menunggu scheduler. */
    public void generateIfDue(LocalDate today) {
        if (!enabled) {
            return;
        }
        PaydayCalendar calendar = periodService.calendar();
        String current = calendar.forDate(today);
        LocalDate currentStart = calendar.paydayForPeriod(current);
        if (today.isAfter(currentStart.plusDays(graceDays))) {
            return;
        }

        String previous = calendar.previousPeriod(current);
        if (periodReportRepository.exists(previous)) {
            return;
        }

        List<ExpenseData> expenses = expenseRepository.getExpenses(previous);
        LocalDate previousStart = calendar.paydayForPeriod(previous);
        List<TopUpResponse> topUps = topUpRepository.findByDateRange(
                previousStart.atStartOfDay(), currentStart.atStartOfDay());
        if (expenses.isEmpty() && topUps.isEmpty()) {
            LOGGER.info("period report skipped: no transactions in {}", previous);
            return;
        }

        List<BudgetOption> budgets = budgetRepository.getOptions();
        long expenseTotal = expenses.stream().mapToLong(ExpenseData::amount).sum();
        long topUpTotal = topUps.stream()
                .map(TopUpResponse::amount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long databaseExpenseTotal = expenseRepository.totalForPeriod(previous);
        int databaseTransactionCount = expenseRepository.countForPeriod(previous);
        PeriodReportData data = new PeriodReportData(
                previous,
                previousStart,
                currentStart.minusDays(1),
                LocalDateTime.now(clock),
                clock.getZone().getId(),
                expenses,
                topUps,
                budgets,
                expenseTotal,
                topUpTotal,
                databaseExpenseTotal,
                databaseTransactionCount);
        PeriodReportStatistics statistics = PeriodReportCalculator.calculate(data);

        byte[] pdf = renderer.render(data, statistics);
        String fileName = "report-" + previous + ".pdf";
        save(fileName, pdf);
        if (!periodReportRepository.insert(previous, fileName, pdf.length)) {
            return;
        }

        notificationService.sendPeriodReport(
                previous,
                EMAIL_DATE_FORMAT.format(data.startDate()) + " - " + EMAIL_DATE_FORMAT.format(data.endDate()),
                expenseTotal,
                expenses.size(),
                topUpTotal,
                pdf,
                fileName);
        LOGGER.info("period report generated: period={} file={} size={} transactions={} expenseTotal={} topUpTotal={} reconciled={}",
                previous, fileName, pdf.length, expenses.size(), expenseTotal, topUpTotal,
                statistics.integrity().ok());
    }

    public PeriodReportsResponse getReports() {
        List<PeriodReportResponse> reports = new ArrayList<>(periodReportRepository.findAll());
        reports.sort(Comparator
                .comparing((PeriodReportResponse report) -> PaydayCalendar.parsePeriod(report.period())
                        .orElse(YearMonth.of(0, 1)))
                .reversed()
                .thenComparing(PeriodReportResponse::generatedAt, Comparator.reverseOrder()));
        return new PeriodReportsResponse(reports);
    }

    public Optional<Path> findReport(String period) {
        if (PaydayCalendar.parsePeriod(period).isEmpty()) {
            return Optional.empty();
        }
        Optional<String> fileName = periodReportRepository.findFileName(period);
        if (fileName.isEmpty()) {
            return Optional.empty();
        }
        Path path = reportDirectory.resolve(fileName.get()).normalize();
        if (!path.startsWith(reportDirectory.normalize()) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private void save(String fileName, byte[] pdf) {
        try {
            Files.createDirectories(reportDirectory);
            Files.write(reportDirectory.resolve(fileName), pdf);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save period report: " + fileName, e);
        }
    }
}
