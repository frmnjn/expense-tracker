package com.expensetracker.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mengirim notifikasi email ke microservice notifier (SMTP/Gmail) sebagai HTML.
 * Pengiriman dijalankan async (thread pool), sehingga tidak memperlambat request.
 * Fire-and-log: kegagalan mengirim tidak pernah menggagalkan operasi utama.
 */
@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
    private static final NumberFormat RUPIAH = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    private final HttpClient httpClient;
    private final String notifierUrl;
    private final List<String> emails;
    private final boolean testMode;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public NotificationService(@Value("${notify.notifier-url:}") String notifierUrl,
                               @Value("${notify.emails:}") String emails,
                               @Value("${notify.test-mode:false}") boolean testMode,
                               @Value("${notify.test-email:}") String testEmail) {
        this.notifierUrl = notifierUrl;
        this.testMode = testMode;
        this.emails = resolveRecipients(emails, testMode, testEmail);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        if (testMode) {
            LOGGER.info("notification TEST MODE active, recipients={}", this.emails);
        }
    }

    private static List<String> resolveRecipients(String emails, boolean testMode, String testEmail) {
        if (testMode) {
            String email = cleanEmail(testEmail);
            return email.isBlank() ? List.of() : List.of(email);
        }
        if (emails == null || emails.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(emails.split(","))
                .map(NotificationService::cleanEmail)
                .filter(e -> !e.isBlank())
                .toList();
    }

    public void sendExpenseCreated(String name, String budget, long amount, String dateTime, long budgetBalance) {
        String body = buildEmail(
                "Expense recorded",
                "A new expense was added to your budget.",
                amount,
                "Amount",
                row("Name", name),
                row("Budget", budget),
                row("Date", dateTime),
                row("Remaining in " + budget, RUPIAH.format(budgetBalance)));
        send("💳 Expense recorded: " + name + " · " + RUPIAH.format(amount), body);
    }

    public void sendBudgetAlert(String budget, long balance, long threshold) {
        String body = buildEmail(
                "Low balance alert",
                "Your budget is getting low.",
                balance,
                "Current balance",
                row("Budget", budget),
                row("Threshold", RUPIAH.format(threshold)),
                row("Shortfall", RUPIAH.format(balance - threshold)));
        send("⚠️ Low balance: " + budget, body);
    }

    public void sendTopUp(String budget, long amount, long newBalance) {
        String body = buildEmail(
                "Top-up received",
                "Your budget balance has been topped up.",
                amount,
                "Amount added",
                row("Budget", budget),
                row("New balance", RUPIAH.format(newBalance)));
        send("💰 Top-up: " + budget + " +" + RUPIAH.format(amount), body);
    }

    public void sendBudgetCreated(String name, long balance, long alertThreshold) {
        String body = buildEmail(
                "New budget created",
                "A new budget is ready to use.",
                balance,
                "Initial balance",
                row("Budget", name),
                row("Alert threshold", alertThreshold > 0 ? RUPIAH.format(alertThreshold) : "Disabled"));
        send("🎉 New budget: " + name, body);
    }

    public void sendBatchCreated(long total, int count, String dateTime, Map<String, Long> byBudget) {
        String[] breakdown = byBudget.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> row(e.getKey(), RUPIAH.format(e.getValue())))
                .toArray(String[]::new);
        String[] rows = new String[breakdown.length + 1];
        rows[0] = row("Date", dateTime);
        System.arraycopy(breakdown, 0, rows, 1, breakdown.length);
        String body = buildEmail(
                "Expenses recorded from scan",
                count + " expense(s) created from a scanned invoice.",
                total,
                "Total",
                rows);
        send("📋 Expenses recorded: " + RUPIAH.format(total), body);
    }

    private void send(String subject, String body) {
        if (notifierUrl == null || notifierUrl.isBlank() || emails.isEmpty()) {
            return;
        }
        String json = "{\"to\":" + toJsonArray(emails)
                + ",\"subject\":" + toJsonString(subject)
                + ",\"body\":" + toJsonString(body)
                + ",\"contentType\":\"text/html\"}";
        executor.execute(() -> doSend(json));
    }

    private void doSend(String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(notifierUrl + "/send"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                LOGGER.warn("notification send failed: status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOGGER.warn("notification send error: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private static String cleanEmail(String value) {
        return value.trim().replace("\"", "");
    }

    private static String buildEmail(String title, String subtitle, long heroAmount, String heroLabel,
                                     String... rows) {
        return "<!DOCTYPE html><html lang=\"en\"><body style=\"margin:0;padding:0;background:#f2f3f7\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td align=\"center\" style=\"padding:28px 16px\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:480px;width:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',"
                + "Roboto,Helvetica,Arial,sans-serif\">"
                + "<tr><td style=\"background:#863bff;border-radius:12px 12px 0 0;padding:22px 24px;color:#ffffff\">"
                + "<div style=\"font-size:18px;font-weight:700\">" + esc(title) + "</div>"
                + "<div style=\"font-size:13px;opacity:.85;margin-top:2px\">" + esc(subtitle) + "</div>"
                + "</td></tr>"
                + "<tr><td style=\"background:#ffffff;border-radius:0 0 12px 12px;padding:24px\">"
                + "<div style=\"text-align:center;color:#9aa0a6;font-size:13px\">" + esc(heroLabel) + "</div>"
                + "<div style=\"text-align:center;color:#111111;font-size:32px;font-weight:800;margin:6px 0 20px\">"
                + esc(RUPIAH.format(heroAmount)) + "</div>"
                + buildRows(rows)
                + "<div style=\"border-top:1px solid #eeeeee;margin-top:20px;padding-top:14px;"
                + "color:#9aa0a6;font-size:12px;line-height:1.5\">"
                + "Automated message from Expense Tracker. Replies to this address are not monitored.</div>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String buildRows(String[] rows) {
        StringBuilder sb = new StringBuilder();
        for (String r : rows) {
            if (r != null && !r.isBlank()) {
                sb.append(r);
            }
        }
        return sb.toString();
    }

    private static String row(String label, String value) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"margin-bottom:10px\"><tr>"
                + "<td style=\"color:#9aa0a6;font-size:13px;padding-right:12px\">" + esc(label) + "</td>"
                + "<td style=\"text-align:right;color:#222222;font-size:13px;font-weight:600\">" + esc(value) + "</td>"
                + "</tr></table>";
    }

    private static String toJsonArray(List<String> values) {
        return values.stream().map(NotificationService::toJsonString).toList().toString();
    }

    private static String toJsonString(String value) {
        return "\"" + esc(value) + "\"";
    }

    private static String esc(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
