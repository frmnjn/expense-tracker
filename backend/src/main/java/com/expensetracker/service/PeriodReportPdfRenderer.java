package com.expensetracker.service;

import com.expensetracker.data.ExpenseData;
import com.expensetracker.model.PeriodReportData;
import com.expensetracker.model.PeriodReportStatistics;
import com.expensetracker.model.PeriodReportStatistics.BudgetCategoryTotal;
import com.expensetracker.model.PeriodReportStatistics.BudgetStat;
import com.expensetracker.model.PeriodReportStatistics.BudgetStatus;
import com.expensetracker.model.PeriodReportStatistics.CategoryTotal;
import com.expensetracker.model.TopUpResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PeriodReportPdfRenderer {

    public byte[] render(PeriodReportData data, PeriodReportStatistics stats) {
        String html = buildHtml(data, stats);
        byte[] pdf = renderPdf(html);
        return stampPageNumbers(pdf);
    }

    String buildHtml(PeriodReportData data, PeriodReportStatistics stats) {
        StringBuilder html = new StringBuilder(16384);
        html.append("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head>")
                .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>")
                .append("<title>Laporan Pengeluaran</title>")
                .append("<style>")
                .append("@page { size: A4; margin: 16mm 13mm 18mm 13mm; }")
                .append("body { font-family: Helvetica, Arial, sans-serif; font-size: 9pt; color: #222222; line-height: 1.35; }")
                .append("h1 { font-size: 20pt; margin: 0 0 4pt 0; }")
                .append("h2 { font-size: 12pt; margin: 14pt 0 6pt 0; border-bottom: 1pt solid #d0d0d0; padding-bottom: 3pt; }")
                .append("h3 { font-size: 10pt; margin: 10pt 0 4pt 0; }")
                .append(".subtitle { color: #555555; font-size: 10pt; }")
                .append(".muted { color: #666666; font-size: 8pt; }")
                .append(".note { font-size: 8pt; color: #555555; margin: 4pt 0; }")
                .append(".badge { display: inline-block; padding: 2pt 5pt; border: 1pt solid #cccccc; font-size: 8pt; }")
                .append(".warn { color: #a00020; font-weight: bold; }")
                .append(".ok { color: #146c2e; font-weight: bold; }")
                .append("table { width: 100%; border-collapse: collapse; margin: 4pt 0 8pt 0; -fs-table-paginate: paginate; }")
                .append("thead { display: table-header-group; }")
                .append("th { text-align: left; font-size: 8pt; background: #f1f1f1; padding: 3pt 4pt; border-bottom: 1pt solid #bbbbbb; }")
                .append("td { font-size: 8pt; padding: 3pt 4pt; border-bottom: 0.5pt solid #e3e3e3; vertical-align: top; }")
                .append(".amount { text-align: right; white-space: nowrap; }")
                .append(".percent { text-align: right; white-space: nowrap; }")
                .append(".center { text-align: center; }")
                .append("ul { margin: 3pt 0 8pt 14pt; padding: 0; }")
                .append("li { margin-bottom: 3pt; }")
                .append(".summary-grid td { border: none; padding: 2pt 4pt; }")
                .append(".summary-grid .label { color: #666666; }")
                .append(".summary-grid .value { text-align: right; font-weight: bold; }")
                .append(".page-break { page-break-before: always; }")
                .append(".keep { page-break-inside: avoid; }")
                .append(".desc { color: #555555; font-size: 7.5pt; }")
                .append(".status-detail { white-space: nowrap; }")
                .append(".negative { color: #a00020; font-weight: bold; }")
                .append(".budget-table { table-layout: fixed; }")
                .append(".budget-table th { white-space: normal; }")
                .append(".budget-table tr { page-break-inside: avoid; }")
                .append(".col-budget-name { width: 18%; }")
                .append(".col-budget-opening { width: 15%; }")
                .append(".col-budget-amount { width: 16%; }")
                .append(".col-budget-status { width: 19%; }")
                .append(".date { white-space: nowrap; }")
                .append(".col-date { width: 17%; }")
                .append(".col-budget { width: 15%; }")
                .append(".col-category { width: 14%; }")
                .append(".col-amount { width: 12%; }")
                .append("</style></head><body>");

        appendHeader(html, data, stats);
        appendExecutiveSummary(html, data, stats);
        appendExpenseBreakdown(html, stats);
        appendBudgetOverview(html, stats);
        appendInsights(html, stats);
        appendTransactions(html, data, stats);
        appendTopUps(html, data, stats);

        html.append("</body></html>");
        return html.toString();
    }

    private void appendHeader(StringBuilder html, PeriodReportData data, PeriodReportStatistics stats) {
        html.append("<h1>Laporan Pengeluaran</h1>")
                .append("<div class=\"subtitle\">Periode ").append(h(data.period())).append("</div>")
                .append("<div class=\"muted\">")
                .append(h(ReportFormat.date(data.startDate()))).append(" - ")
                .append(h(ReportFormat.date(data.endDate())))
                .append(" &#160; | &#160; Dibuat ")
                .append(h(ReportFormat.dateTime(data.generatedAt())))
                .append(" (").append(h(data.timeZone())).append(")")
                .append("</div>");
        if (stats.integrity().ok()) {
            html.append("<p><span class=\"badge ok\">Terekonsiliasi dengan database</span> ")
                    .append("<span class=\"muted\">").append(stats.transactionCount())
                    .append(" transaksi, ").append(h(ReportFormat.rupiah(stats.expenseTotal())))
                    .append("</span></p>");
        } else {
            html.append("<p><span class=\"badge warn\">Peringatan integritas data</span> ")
                    .append("<span class=\"muted\">total/klasifikasi tanggal perlu ditinjau</span></p>");
        }
    }

    private void appendExecutiveSummary(StringBuilder html, PeriodReportData data, PeriodReportStatistics stats) {
        html.append("<h2>Ringkasan Eksekutif</h2>")
                .append("<table class=\"summary-grid keep\">")
                .append(summaryRow("Periode laporan", data.period()))
                .append(summaryRow("Total pengeluaran", ReportFormat.rupiah(stats.expenseTotal())))
                .append(summaryRow("Jumlah transaksi", String.valueOf(stats.transactionCount())))
                .append(summaryRow("Rata-rata per hari", ReportFormat.rupiah(stats.averageDailyExpense())
                        + " selama " + stats.periodDays() + " hari"))
                .append(summaryRow("Total uang ditambahkan", ReportFormat.rupiah(stats.topUpTotal())
                        + " (tidak termasuk pengeluaran)"))
                .append("</table>");

        html.append("<h3>Kategori pengeluaran terbesar</h3>");
        if (stats.categories().isEmpty()) {
            html.append("<p class=\"muted\">Tidak ada kategori dengan pengeluaran pada periode ini.</p>");
        } else {
            html.append("<table class=\"keep\"><thead><tr><th>Kategori</th><th class=\"center\">Trx</th>")
                    .append("<th class=\"amount\">Nominal</th><th class=\"percent\">% dari total</th></tr></thead><tbody>");
            for (CategoryTotal category : stats.categories().stream().limit(5).toList()) {
                html.append("<tr><td>").append(h(category.category())).append("</td>")
                        .append("<td class=\"center\">").append(category.count()).append("</td>")
                        .append("<td class=\"amount\">").append(h(ReportFormat.rupiah(category.amount()))).append("</td>")
                        .append("<td class=\"percent\">").append(h(ReportFormat.percent(category.percent()))).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        long negative = stats.budgets().stream().filter(b -> b.status() == BudgetStatus.NEGATIVE).count();
        long low = stats.budgets().stream().filter(b -> b.status() == BudgetStatus.LOW).count();
        long noThreshold = stats.budgets().stream()
                .filter(b -> b.status() == BudgetStatus.NO_THRESHOLD).count();
        html.append("<h3>Kondisi budget</h3><ul>");
        for (String condition : budgetConditionLines(negative, low, noThreshold, stats.budgets().size())) {
            html.append("<li>").append(h(condition)).append("</li>");
        }
        html.append("</ul>");

        appendList(html, "Temuan Utama", stats.observations());
    }

    private void appendExpenseBreakdown(StringBuilder html, PeriodReportStatistics stats) {
        html.append("<h2>Rincian Pengeluaran</h2>");
        html.append("<h3>Per budget dan kategori</h3>");
        if (stats.budgetCategories().isEmpty()) {
            html.append("<p class=\"muted\">Tidak ada pengeluaran pada periode ini.</p>");
        } else {
            html.append("<table><thead><tr><th>Budget</th><th>Kategori</th><th class=\"center\">Trx</th>")
                    .append("<th class=\"amount\">Nominal</th><th class=\"percent\">% dari total</th></tr></thead><tbody>");
            for (BudgetCategoryTotal item : stats.budgetCategories()) {
                html.append("<tr><td>").append(h(item.budget())).append("</td>")
                        .append("<td>").append(h(item.category())).append("</td>")
                        .append("<td class=\"center\">").append(item.count()).append("</td>")
                        .append("<td class=\"amount\">").append(h(ReportFormat.rupiah(item.amount()))).append("</td>")
                        .append("<td class=\"percent\">").append(h(ReportFormat.percent(item.percent()))).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        html.append("<h3>Peringkat kategori</h3>");
        if (stats.categories().isEmpty()) {
            html.append("<p class=\"muted\">Tidak ada kategori dengan pengeluaran pada periode ini.</p>");
        } else {
            html.append("<table><thead><tr><th>Kategori</th><th class=\"center\">Budget</th><th class=\"center\">Trx</th>")
                    .append("<th class=\"amount\">Nominal</th><th class=\"percent\">% dari total</th></tr></thead><tbody>");
            for (CategoryTotal item : stats.categories()) {
                html.append("<tr><td>").append(h(item.category())).append("</td>")
                        .append("<td class=\"center\">").append(item.budgetCount()).append("</td>")
                        .append("<td class=\"center\">").append(item.count()).append("</td>")
                        .append("<td class=\"amount\">").append(h(ReportFormat.rupiah(item.amount()))).append("</td>")
                        .append("<td class=\"percent\">").append(h(ReportFormat.percent(item.percent()))).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
    }

    private void appendBudgetOverview(StringBuilder html, PeriodReportStatistics stats) {
        html.append("<h2>Ringkasan Budget</h2>");
        if (stats.budgets().isEmpty()) {
            html.append("<p class=\"muted\">Tidak ada budget aktif.</p>");
            return;
        }

        long negative = stats.budgets().stream().filter(b -> b.status() == BudgetStatus.NEGATIVE).count();
        long nonNegative = stats.budgets().size() - negative;

        html.append("<div class=\"keep\"><table class=\"summary-grid\">")
                .append(summaryRow("Budget dengan saldo minus", String.valueOf(negative)))
                .append(summaryRow("Budget dengan saldo nol atau lebih", String.valueOf(nonNegative)))
                .append(summaryRow("Total uang ditambahkan periode ini",
                        ReportFormat.rupiah(stats.topUpTotal())))
                .append(summaryRow("Total uang dipakai periode ini",
                        ReportFormat.rupiah(stats.expenseTotal())))
                .append("</table></div>");

        html.append("<p class=\"note\"><strong>Cara membaca angka:</strong> ")
                .append("<em>Saldo awal periode</em> adalah saldo budget pada awal periode, sama dengan ")
                .append("saldo akhir periode sebelumnya. <em>Uang yang ditambahkan</em> adalah dana yang ")
                .append("dialokasikan ke budget tersebut selama periode laporan, bukan pengeluaran atau ")
                .append("pemasukan. <em>Uang yang dipakai</em> adalah pengeluaran dari budget tersebut selama ")
                .append("periode laporan. <em>Sisa saldo akhir periode</em> adalah saldo budget pada hari ")
                .append("terakhir periode, sebelum transaksi periode berikutnya. Dengan begitu: ")
                .append("saldo awal + uang ditambahkan - uang dipakai = saldo akhir periode. ")
                .append("Penyesuaian saldo manual tidak dapat dilacak. ")
                .append("Saldo bertanda minus berarti budget sudah melewati saldo yang tersedia.</p>");

        html.append("<table class=\"budget-table\"><thead><tr><th class=\"col-budget-name\">Nama budget</th>")
                .append("<th class=\"amount col-budget-opening\">Saldo awal periode</th>")
                .append("<th class=\"amount col-budget-amount\">Uang yang ditambahkan selama periode</th>")
                .append("<th class=\"amount col-budget-amount\">Uang yang dipakai selama periode</th>")
                .append("<th class=\"amount col-budget-amount\">Sisa saldo akhir periode</th>")
                .append("<th class=\"col-budget-status\">Kondisi budget</th></tr></thead><tbody>");
        for (BudgetStat budget : stats.budgets()) {
            html.append("<tr><td>").append(h(budget.budget())).append("</td>")
                    .append("<td class=\"amount")
                    .append(budget.openingBalance() < 0 ? " negative" : "")
                    .append("\">").append(h(ReportFormat.rupiah(budget.openingBalance()))).append("</td>")
                    .append("<td class=\"amount\">").append(h(ReportFormat.rupiah(budget.topUps()))).append("</td>")
                    .append("<td class=\"amount\">").append(h(ReportFormat.rupiah(budget.expenses()))).append("</td>")
                    .append("<td class=\"amount")
                    .append(budget.balance() < 0 ? " negative" : "")
                    .append("\">").append(h(ReportFormat.rupiah(budget.balance()))).append("</td>")
                    .append("<td>").append(budgetStatusCell(budget)).append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    private void appendInsights(StringBuilder html, PeriodReportStatistics stats) {
        html.append("<h2>Analisis Keuangan</h2>");
        appendList(html, "Rekomendasi", stats.recommendations());

        if (!stats.largestTransactions().isEmpty()) {
            html.append("<h3>Transaksi terbesar</h3>")
                    .append("<table class=\"keep\"><thead><tr><th class=\"col-date\">Tanggal</th><th>Transaksi</th>")
                    .append("<th class=\"col-budget\">Budget</th><th class=\"amount col-amount\">Nominal</th></tr></thead><tbody>");
            for (ExpenseData expense : stats.largestTransactions()) {
                html.append("<tr><td class=\"date\">").append(nowrap(ReportFormat.expenseDateTime(expense.dateTime()))).append("</td>")
                        .append("<td>").append(h(expense.name())).append("</td>")
                        .append("<td>").append(h(expense.budgetName())).append("</td>")
                        .append("<td class=\"amount\">").append(h(ReportFormat.rupiah(expense.amount()))).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
    }

    private void appendTransactions(StringBuilder html, PeriodReportData data, PeriodReportStatistics stats) {
        html.append("<div class=\"page-break\"></div><h2>Lampiran A: Daftar Transaksi</h2>");
        html.append("<p class=\"muted\">Daftar lengkap pengeluaran yang tidak dihapus pada periode ini. ")
                .append("Transaksi: ").append(stats.transactionCount()).append(", total: ")
                .append(h(ReportFormat.rupiah(stats.expenseTotal()))).append(".</p>");
        if (data.expenses().isEmpty()) {
            html.append("<p class=\"muted\">Tidak ada transaksi pada periode ini.</p>");
            return;
        }
        html.append("<table><thead><tr><th class=\"col-date\">Tanggal</th><th>Transaksi</th><th class=\"col-budget\">Budget</th>")
                .append("<th class=\"col-category\">Kategori</th><th class=\"amount col-amount\">Nominal</th></tr></thead><tbody>");
        for (ExpenseData expense : data.expenses()) {
            html.append("<tr><td class=\"date\">").append(nowrap(ReportFormat.expenseDateTime(expense.dateTime()))).append("</td>")
                    .append("<td>").append(h(expense.name()));
            if (expense.description() != null && !expense.description().isBlank()) {
                html.append("<div class=\"desc\">").append(h(expense.description())).append("</div>");
            }
            html.append("</td><td>").append(h(expense.budgetName())).append("</td>")
                    .append("<td>").append(expense.categoryName() == null || expense.categoryName().isBlank()
                            ? "Tanpa Kategori" : h(expense.categoryName())).append("</td>")
                    .append("<td class=\"amount\">").append(h(ReportFormat.rupiah(expense.amount()))).append("</td></tr>");
        }
        html.append("<tr><td colspan=\"4\"><strong>Total</strong></td><td class=\"amount\"><strong>")
                .append(h(ReportFormat.rupiah(stats.expenseTotal()))).append("</strong></td></tr>")
                .append("</tbody></table>");
    }

    private void appendTopUps(StringBuilder html, PeriodReportData data, PeriodReportStatistics stats) {
        html.append("<h2>Lampiran B: Uang yang Ditambahkan</h2>");
        if (data.topUps().isEmpty()) {
            html.append("<p class=\"muted\">Tidak ada uang yang ditambahkan pada periode ini.</p>");
            return;
        }
        html.append("<table><thead><tr><th class=\"col-date\">Tanggal</th><th class=\"col-budget\">Budget</th><th class=\"amount col-amount\">Nominal</th>")
                .append("<th>Deskripsi</th></tr></thead><tbody>");
        for (TopUpResponse topUp : data.topUps()) {
            html.append("<tr><td class=\"date\">").append(nowrap(ReportFormat.expenseDateTime(topUp.dateTime()))).append("</td>")
                    .append("<td>").append(h(topUp.budget())).append("</td>")
                    .append("<td class=\"amount\">").append(h(ReportFormat.rupiah(
                            topUp.amount() == null ? 0 : topUp.amount()))).append("</td>")
                    .append("<td>").append(h(topUp.description())).append("</td></tr>");
        }
        html.append("<tr><td colspan=\"2\"><strong>Total</strong></td><td class=\"amount\"><strong>")
                .append(h(ReportFormat.rupiah(stats.topUpTotal()))).append("</strong></td><td></td></tr>")
                .append("</tbody></table>");
    }

    private static void appendList(StringBuilder html, String title, List<String> items) {
        html.append("<h3>").append(h(title)).append("</h3><ul>");
        for (String item : items) {
            html.append("<li>").append(h(item)).append("</li>");
        }
        html.append("</ul>");
    }

    private static String summaryRow(String label, String value) {
        return "<tr><td class=\"label\">" + h(label) + "</td><td class=\"value\">" + h(value) + "</td></tr>";
    }

    private static String budgetStatusCell(BudgetStat budget) {
        return switch (budget.status()) {
            case NEGATIVE -> "<span class=\"warn\">Saldo Minus</span>";
            case LOW -> "<span class=\"warn\">Saldo Menipis</span><div class=\"desc status-detail\">"
                    + "Batas " + h(ReportFormat.rupiah(budget.alertThreshold())) + "</div>";
            case OK -> "<span class=\"ok\">Saldo Tersedia</span>";
            case NO_THRESHOLD -> "<span class=\"ok\">Saldo Tersedia</span>"
                    + "<div class=\"desc status-detail\">Batas belum diatur</div>";
        };
    }

    private static List<String> budgetConditionLines(long negative, long low, long noThreshold, int budgetCount) {
        List<String> lines = new ArrayList<>();
        if (budgetCount == 0) {
            lines.add("Belum ada budget yang bisa diringkas. Tambahkan budget untuk mulai memantau pengeluaran.");
            return lines;
        }
        if (negative > 0) {
            lines.add(negative + " budget memiliki saldo minus. Periksa pengeluaran dan kondisi saldo "
                    + "budget tersebut.");
        }
        if (low > 0) {
            lines.add(low + " budget perlu mulai dihemat. Saldonya sudah di bawah batas pengingat "
                    + "yang ditentukan.");
        }
        if (noThreshold > 0) {
            lines.add(noThreshold + " budget belum memiliki batas pengingat saldo rendah. Atur batasnya "
                    + "agar kamu mendapat pengingat saat saldo mulai menipis.");
        }
        if (negative == 0 && low == 0 && noThreshold == 0) {
            lines.add("Semua budget masih di atas atau sama dengan batas pengingat saldo rendah. "
                    + "Tetap pantau pengeluaran agar saldo tidak cepat habis.");
        }
        return lines;
    }

    private static byte[] renderPdf(String html) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Gagal membuat PDF laporan periode", e);
        }
    }

    private static byte[] stampPageNumbers(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int pageCount = document.getNumberOfPages();
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 0; i < pageCount; i++) {
                PDPage page = document.getPage(i);
                String text = "Halaman " + (i + 1) + " dari " + pageCount;
                float width = font.getStringWidth(text) / 1000f * 8;
                try (PDPageContentStream content = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    content.beginText();
                    content.setFont(font, 8);
                    content.newLineAtOffset((page.getMediaBox().getWidth() - width) / 2, 20);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menambahkan nomor halaman laporan", e);
        }
    }

    private static String h(String value) {
        return escapeHtml(sanitize(value));
    }

    private static String nowrap(String value) {
        return h(value);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == '\r' || c == '\n' || c == '\t') {
                result.append(' ');
            } else if (c >= 32 && c <= 255 && c != 127) {
                result.append(c);
            } else if (c == '\u2013' || c == '\u2014') {
                result.append('-');
            } else if (c == '\u2018' || c == '\u2019') {
                result.append('\'');
            } else if (c == '\u201c' || c == '\u201d') {
                result.append('"');
            } else if (c == '\u00a0') {
                result.append(' ');
            } else {
                result.append('?');
            }
        }
        return result.toString();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
