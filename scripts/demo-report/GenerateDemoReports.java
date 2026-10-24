import com.expensetracker.data.ExpenseData;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.PeriodReportData;
import com.expensetracker.model.PeriodReportStatistics;
import com.expensetracker.model.TopUpResponse;
import com.expensetracker.service.PeriodReportCalculator;
import com.expensetracker.service.PeriodReportPdfRenderer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class GenerateDemoReports {

    private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    public static void main(String[] args) throws Exception {
        Path dataFile = Path.of(args.length > 0 ? args[0] : "frontend/public/demo/data.json");
        Path outputDir = Path.of(args.length > 1 ? args[1] : "frontend/public/demo/reports");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(dataFile.toFile());
        JsonNode reportInputs = root.path("reportsInput");

        Files.createDirectories(outputDir);
        try (var files = Files.list(outputDir)) {
            files.filter(path -> path.getFileName().toString().startsWith("report-")
                            && path.getFileName().toString().endsWith(".pdf"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            throw new IllegalStateException("Gagal menghapus report lama: " + path, e);
                        }
                    });
        }

        PeriodReportPdfRenderer renderer = new PeriodReportPdfRenderer();
        ArrayNode reports = mapper.createArrayNode();

        for (JsonNode input : reportInputs) {
            String period = input.path("period").asText();
            LocalDate startDate = LocalDate.parse(input.path("startDate").asText());
            LocalDate endDate = LocalDate.parse(input.path("endDate").asText());
            List<ExpenseData> expenses = mapper.convertValue(
                    input.path("expenses"), new TypeReference<List<ExpenseData>>() {});
            List<TopUpResponse> topUps = mapper.convertValue(
                    input.path("topUps"), new TypeReference<List<TopUpResponse>>() {});
            List<BudgetOption> budgets = mapper.convertValue(
                    input.path("budgets"), new TypeReference<List<BudgetOption>>() {});

            long expenseTotal = expenses.stream().mapToLong(ExpenseData::amount).sum();
            long topUpTotal = topUps.stream()
                    .map(TopUpResponse::amount)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .sum();
            LocalDateTime generatedAt = LocalDateTime.now(ZONE).withNano(0);

            PeriodReportData data = new PeriodReportData(
                    period,
                    startDate,
                    endDate,
                    generatedAt,
                    ZONE.getId(),
                    expenses,
                    topUps,
                    budgets,
                    expenseTotal,
                    topUpTotal,
                    expenseTotal,
                    expenses.size());
            PeriodReportStatistics statistics = PeriodReportCalculator.calculate(data);
            byte[] pdf = renderer.render(data, statistics);

            String fileName = "report-" + period + ".pdf";
            Path target = outputDir.resolve(fileName);
            Files.write(target, pdf);

            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("period", period);
            metadata.put("fileName", fileName);
            metadata.put("sizeBytes", pdf.length);
            metadata.put("generatedAt", generatedAt.toString());
            reports.add(metadata);

            System.out.println("REPORT " + period + " transactions=" + expenses.size()
                    + " size=" + pdf.length + " reconciled=" + statistics.integrity().ok());
        }

        ObjectNode output = mapper.createObjectNode();
        output.set("reports", reports);
        Path metadataFile = outputDir.getParent().resolve("reports.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(metadataFile.toFile(), output);
        System.out.println("Reports metadata: " + metadataFile);
    }
}
