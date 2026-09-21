package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mengekstrak detail transaksi dari isi email notifikasi bank. Strategi
 * hibrida: regex per format bank (BCA kartu kredit, BCA myBCA, D-Bank QRIS)
 * lebih dulu; bila field penting tidak terbaca, fallback ke Gemini (bila
 * GEMINI_API_KEY tersedia). Tidak mengubah layanan analisa struk.
 */
@Service
public class EmailParserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailParserService.class);
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final int MAX_AI_INPUT = 6000;

    private static final Pattern AMOUNT = Pattern.compile("[-0-9.,]+");

    private final BudgetRepository budgetRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxAttempts;
    private final long retryDelayMs;

    public EmailParserService(BudgetRepository budgetRepository,
                              ObjectMapper objectMapper,
                              @Value("${ai.gemini-api-key:}") String apiKey,
                              @Value("${ai.model:gemini-3.5-flash-lite}") String model,
                              @Value("${ai.timeout:600}") long timeoutSeconds,
                              @Value("${ai.max-attempts:50}") int maxAttempts,
                              @Value("${ai.retry-delay-ms:2000}") long retryDelayMs) {
        this.budgetRepository = budgetRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-3.5-flash-lite" : model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxAttempts = maxAttempts < 1 ? 1 : maxAttempts;
        this.retryDelayMs = retryDelayMs < 0 ? 0 : retryDelayMs;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public ParsedTransaction parse(String sender, String html) {
        String text = HtmlText.toText(html);
        ParsedTransaction parsed = parseByRegex(sender, text);
        if (parsed != null) {
            return parsed;
        }
        if (!apiKey.isBlank()) {
            return parseWithAi(text);
        }
        throw new ValidationException("Format email belum dikenali");
    }

    private ParsedTransaction parseByRegex(String sender, String text) {
        String domain = domainOf(sender);
        if (domain.endsWith("klikbca.com")) {
            return parseBcaCreditCard(text);
        }
        if (domain.endsWith("bca.co.id")) {
            return parseBcaInternetJournal(text);
        }
        if (domain.endsWith("danamon.co.id")) {
            return parseDanamonQris(text);
        }
        return null;
    }

    /** BCA Credit Card Transaction Notification. */
    private ParsedTransaction parseBcaCreditCard(String text) {
        String merchant = field(text, "Merchant\\s*/\\s*ATM");
        Long amount = parseAmount(field(text, "Sejumlah"));
        if (merchant == null || amount == null || amount <= 0) {
            return null;
        }
        return new ParsedTransaction(merchant, amount,
                parseDateTime(field(text, "Pada\\s+Tanggal")), null, "REGEX");
    }

    /** BCA Internet Transaction Journal (myBCA / QRIS). */
    private ParsedTransaction parseBcaInternetJournal(String text) {
        String status = field(text, "Status");
        if (status != null && !status.toLowerCase(Locale.ROOT).contains("berhasil")) {
            return null;
        }
        String merchant = field(text, "Pembayaran\\s+Ke");
        Long amount = parseAmount(field(text, "Total\\s+Bayar"));
        if (merchant == null || amount == null || amount <= 0) {
            return null;
        }
        return new ParsedTransaction(merchant, amount,
                parseDateTime(field(text, "Tanggal\\s+Transaksi")), null, "REGEX");
    }

    /** D-Bank PRO QRIS Berhasil. */
    private ParsedTransaction parseDanamonQris(String text) {
        String status = field(text, "Status");
        if (status != null && !status.toLowerCase(Locale.ROOT).contains("berhasil")) {
            return null;
        }
        String merchant = field(text, "Merchant\\s+Tujuan");
        Long amount = parseAmount(field(text, "Nominal"));
        if (merchant == null || amount == null || amount <= 0) {
            return null;
        }
        return new ParsedTransaction(merchant, amount,
                parseDateTime(field(text, "Tanggal\\s+Pembayaran")), null, "REGEX");
    }

    /** Ambil value dari baris "Label : value" (titik dua opsional). */
    private static String field(String text, String labelRegex) {
        Matcher m = Pattern.compile("(?im)^\\s*" + labelRegex + "\\s*:?\\s*(.+?)\\s*$").matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String domainOf(String sender) {
        if (sender == null) {
            return "";
        }
        int at = sender.lastIndexOf('@');
        String domain = at >= 0 ? sender.substring(at + 1) : sender;
        return domain.replaceAll("[<>\\s]", "").toLowerCase(Locale.ROOT);
    }

    /** Parse nominal rupiah yang bisa berformat Indonesia (1.035.600,00) maupun US (25,000.00). */
    static Long parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = AMOUNT.matcher(raw);
        StringBuilder sb = new StringBuilder();
        boolean negative = false;
        while (m.find()) {
            for (char c : m.group().toCharArray()) {
                if (c == '-' && sb.length() == 0) {
                    negative = true;
                } else if (Character.isDigit(c) || c == '.' || c == ',') {
                    sb.append(c);
                }
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        String number = normalizeNumber(sb.toString());
        try {
            double value = Double.parseDouble(number);
            long rounded = Math.round(value);
            return negative ? -rounded : rounded;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeNumber(String s) {
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            // Separator terakhir adalah desimal.
            if (lastComma > lastDot) {
                return s.replace(".", "").replace(",", ".");
            }
            return s.replace(",", "");
        }
        if (lastComma >= 0) {
            return isThousandsSeparator(s, ',') ? s.replace(",", "") : s.replace(',', '.');
        }
        if (lastDot >= 0) {
            return isThousandsSeparator(s, '.') ? s.replace(".", "") : s;
        }
        return s;
    }

    /** True bila separator dipakai sebagai pemisah ribuan (semua grup setelahnya 3 digit). */
    private static boolean isThousandsSeparator(String s, char sep) {
        String[] parts = s.split(Pattern.quote(String.valueOf(sep)), -1);
        if (parts.length < 2) {
            return false;
        }
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].length() != 3) {
                return false;
            }
        }
        return true;
    }

    static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replaceAll("(?i)\\b(WIB|WITA|WIT)\\b", "").trim();
        List<DateTimeFormatter> formatters = List.of(
                formatter("yyyy-MM-dd HH:mm:ss"),
                formatter("yyyy-MM-dd HH:mm"),
                formatter("dd-MM-yyyy HH:mm:ss"),
                formatter("dd-MM-yyyy HH:mm"),
                formatter("dd MMM yyyy HH:mm:ss"),
                formatter("dd MMM yyyy HH:mm"),
                formatter("dd MMMM yyyy HH:mm:ss"),
                formatter("dd MMMM yyyy HH:mm"));
        for (DateTimeFormatter f : formatters) {
            try {
                return LocalDateTime.parse(value, f);
            } catch (Exception ignored) {
                // coba format berikutnya
            }
        }
        return null;
    }

    private static DateTimeFormatter formatter(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.of("id", "ID"));
    }

    private ParsedTransaction parseWithAi(String text) {
        try {
            String raw = callGeminiWithRetry(text);
            JsonNode json = objectMapper.readTree(raw);
            boolean isExpense = json.path("isExpense").asBoolean(false);
            if (!isExpense) {
                throw new NotExpenseException("Bukan transaksi pengeluaran");
            }
            long amount = json.path("amount").asLong(0);
            if (amount <= 0) {
                throw new ValidationException("Nominal tidak terbaca");
            }
            String merchant = json.path("merchant").asText("").trim();
            String budget = json.path("suggestedBudget").asText("").trim();
            return new ParsedTransaction(
                    merchant.isBlank() ? "Transaksi email" : merchant,
                    amount,
                    parseDateTime(json.path("dateTime").asText("")),
                    budget.isBlank() ? null : budget,
                    "AI");
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("email AI parse failed: {}", e.getMessage());
            throw new ValidationException("Gagal membaca email: " + e.getMessage());
        }
    }

    private String callGeminiWithRetry(String text) throws Exception {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callGemini(text);
            } catch (RetryableException | IOException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                LOGGER.warn("email AI attempt {}/{} failed: {}; retrying in {}ms",
                        attempt, maxAttempts, e.getMessage(), retryDelayMs);
                if (retryDelayMs > 0) {
                    Thread.sleep(retryDelayMs);
                }
            }
        }
        throw new IllegalStateException("Gemini exhausted all attempts");
    }

    protected String callGemini(String text) throws Exception {
        String input = text.length() > MAX_AI_INPUT ? text.substring(0, MAX_AI_INPUT) : text;
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", buildAiPrompt() + "\n\n" + input)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_BASE + "/models/" + model + ":generateContent?key=" + apiKey))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            int code = response.statusCode();
            // 429 (rate limit) & 5xx (failover sesaat) transien -> retry.
            if (code == 429 || code >= 500) {
                throw new RetryableException("Gemini returned HTTP " + code);
            }
            throw new IllegalStateException("Gemini returned HTTP " + code);
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode result = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (result.isMissingNode() || result.asText().isBlank()) {
            throw new IllegalStateException("Gemini returned no analysis");
        }
        return result.asText();
    }

    private String buildAiPrompt() {
        List<String> budgets = budgetRepository.getOptions().stream()
                .map(o -> o.description() == null || o.description().isBlank()
                        ? o.name()
                        : o.name() + ": " + o.description())
                .sorted()
                .toList();
        String budgetList = budgets.isEmpty() ? "(tidak ada budget terdaftar)" : String.join("\n", budgets);
        return "Kamu mengekstrak transaksi pengeluaran dari email notifikasi bank/e-wallet Indonesia.\n"
                + "Balas HANYA JSON dengan struktur:\n"
                + "{\"isExpense\":<true bila ini pembayaran/pengeluaran, false bila bukan (mis. transfer masuk/refund)>,\n"
                + "\"merchant\":\"nama merchant/tujuan\",\n"
                + "\"amount\":<nominal integer dalam Rupiah tanpa desimal>,\n"
                + "\"dateTime\":\"waktu transaksi format YYYY-MM-DD HH:mm:ss\",\n"
                + "\"suggestedBudget\":\"<nama budget paling cocok atau string kosong>\"}\n"
                + "Daftar budget tersedia:\n" + budgetList + "\n"
                + "Abaikan nominal saldo, biaya admin yang bukan bagian transaksi, dan nomor referensi.";
    }
}
