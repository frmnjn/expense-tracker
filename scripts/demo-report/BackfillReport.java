import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.PaydayOverrideRepository;
import com.expensetracker.data.PeriodReportRepository;
import com.expensetracker.data.TopUpRepository;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.PeriodReportData;
import com.expensetracker.model.PeriodReportStatistics;
import com.expensetracker.model.TopUpResponse;
import com.expensetracker.service.PaydayCalendar;
import com.expensetracker.service.PeriodReportCalculator;
import com.expensetracker.service.PeriodReportPdfRenderer;
import com.expensetracker.service.PeriodService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class BackfillReport {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("NO_PERIODS");
            return;
        }
        String uploadDir = System.getenv().getOrDefault("UPLOAD_DIR", "/app/uploads-demo");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(System.getenv("DB_URL"));
        dataSource.setUsername(System.getenv("DB_USER"));
        dataSource.setPassword(System.getenv("DB_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        PeriodService periodService = new PeriodService(new PaydayOverrideRepository(jdbc));
        ExpenseRepository expenseRepository = new ExpenseRepository(jdbc);
        TopUpRepository topUpRepository = new TopUpRepository(jdbc);
        BudgetRepository budgetRepository = new BudgetRepository(jdbc);
        PeriodReportRepository reportRepository = new PeriodReportRepository(jdbc);
        PeriodReportPdfRenderer renderer = new PeriodReportPdfRenderer();

        for (String period : args) {
            generate(period, uploadDir, periodService, expenseRepository, topUpRepository,
                    budgetRepository, reportRepository, renderer);
        }
    }

    private static void generate(String period, String uploadDir, PeriodService periodService,
                                 ExpenseRepository expenseRepository, TopUpRepository topUpRepository,
                                 BudgetRepository budgetRepository, PeriodReportRepository reportRepository,
                                 PeriodReportPdfRenderer renderer) throws Exception {
        if (reportRepository.exists(period)) {
            System.out.println("ALREADY_EXISTS " + period);
            return;
        }

        PaydayCalendar calendar = periodService.calendar();
        LocalDate start = calendar.paydayForPeriod(period);
        LocalDate endExclusive = calendar.paydayForPeriod(calendar.nextPeriod(period));
        LocalDate end = endExclusive.minusDays(1);

        List<ExpenseData> expenses = expenseRepository.getExpenses(period);
        List<TopUpResponse> topUps = topUpRepository.findByDateRange(
                start.atStartOfDay(), endExclusive.atStartOfDay());
        List<BudgetOption> budgets = budgetRepository.getOptions();

        long expenseTotal = expenses.stream().mapToLong(ExpenseData::amount).sum();
        long topUpTotal = topUps.stream()
                .map(TopUpResponse::amount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        PeriodReportData data = new PeriodReportData(
                period,
                start,
                end,
                LocalDateTime.now(ZoneId.of("Asia/Jakarta")),
                "Asia/Jakarta",
                expenses,
                topUps,
                budgets,
                expenseTotal,
                topUpTotal,
                expenseRepository.totalForPeriod(period),
                expenseRepository.countForPeriod(period));

        PeriodReportStatistics statistics = PeriodReportCalculator.calculate(data);
        byte[] pdf = renderer.render(data, statistics);
        Path directory = Paths.get(uploadDir, "reports");
        Files.createDirectories(directory);
        String fileName = "report-" + period + ".pdf";
        Files.write(directory.resolve(fileName), pdf);
        boolean inserted = reportRepository.insert(period, fileName, pdf.length);

        System.out.println("PERIOD=" + period);
        System.out.println("RANGE=" + start + ".." + end);
        System.out.println("TRANSACTIONS=" + expenses.size());
        System.out.println("EXPENSE_TOTAL=" + expenseTotal);
        System.out.println("TOPUP_TOTAL=" + topUpTotal);
        System.out.println("RECONCILED=" + statistics.integrity().ok());
        System.out.println("FILE=" + directory.resolve(fileName));
        System.out.println("SIZE=" + pdf.length);
        System.out.println("INSERTED=" + inserted);
    }
}

