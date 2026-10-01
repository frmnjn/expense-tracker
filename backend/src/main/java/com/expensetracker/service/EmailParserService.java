package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.CategoryOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
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
 * GEMINI_API_KEY tersedia), lalu DeepSeek sekali bila Gemini habis attempt.
 */
@Service
public class EmailParserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailParserService.class);
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final int MAX_AI_INPUT = 6000;
    private static final Pattern RETRY_IN = Pattern.compile("retry in ([0-9]+(?:\\.[0-9]+)?)s",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT = Pattern.compile("[-0-9.,]+");

    /** Token mata uang yang dikenali pada nilai nominal email. */
    private static final Pattern CURRENCY = Pattern.compile(
            "(RP|IDR|USD|SGD|EUR|JPY|MYR|AUD|GBP|CNY|HKD|KRW|THB|PHP|INR|SAR|AED|NZD|CAD|CHF|VND)");

    /** Nominal dalam mata uang aslinya (belum dikonversi ke IDR). */
    record Money(String currency, BigDecimal amount) {
    }

    private final BudgetRepository budgetRepository;
    private final ObjectMapper objectMapper;
    private final ExchangeRateService exchangeRateService;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long retryAfterCapMs;
    private final String deepseekApiKey;
    private final String deepseekModel;
    private final String deepseekBaseUrl;

    public EmailParserService(BudgetRepository budgetRepository,
                              ObjectMapper objectMapper,
                              ExchangeRateService exchangeRateService,
                              @Value("${ai.gemini-api-key:}") String apiKey,
                              @Value("${ai.model:gemini-3.5-flash-lite}") String model,
                              @Value("${ai.timeout:600}") long timeoutSeconds,
                              @Value("${ai.max-attempts:50}") int maxAttempts,
                              @Value("${ai.retry-delay-ms:2000}") long retryDelayMs,
                              @Value("${ai.deepseek-api-key:}") String deepseekApiKey,
                              @Value("${ai.deepseek-model:deepseek-flash}") String deepseekModel,
                              @Value("${ai.deepseek-base-url:https://api.deepseek.com}") String deepseekBaseUrl,
                              @Value("${ai.retry-after-cap-ms:120000}") long retryAfterCapMs) {
        this.budgetRepository = budgetRepository;
        this.objectMapper = objectMapper;
        this.exchangeRateService = exchangeRateService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-3.5-flash-lite" : model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxAttempts = maxAttempts < 1 ? 1 : maxAttempts;
        this.retryDelayMs = retryDelayMs < 0 ? 0 : retryDelayMs;
        this.retryAfterCapMs = retryAfterCapMs < 0 ? 0 : retryAfterCapMs;
        this.deepseekApiKey = deepseekApiKey == null ? "" : deepseekApiKey.trim();
        this.deepseekModel = deepseekModel == null || deepseekModel.isBlank() ? "deepseek-flash" : deepseekModel;
        String base = deepseekBaseUrl == null || deepseekBaseUrl.isBlank()
                ? "https://api.deepseek.com" : deepseekBaseUrl.trim();
        this.deepseekBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public ParsedTransaction parse(String sender, String html) {
        return parse(sender, "", html);
    }

    public ParsedTransaction parse(String sender, String subject, String html) {
        String text = HtmlText.toText(html);
        ParsedTransaction parsed = parseByRegex(sender, subject, text);
        if (parsed != null) {
            return parsed;
        }
        if (!apiKey.isBlank()) {
            return parseWithAi(text);
        }
        throw new ValidationException("Format email belum dikenali");
    }

    private ParsedTransaction parseByRegex(String sender, String subject, String text) {
        String domain = domainOf(sender);
        if (domain.endsWith("klikbca.com") || isBcaCreditCard(sender, subject, text)) {
            return parseBcaCreditCard(text);
        }
        if (domain.endsWith("bca.co.id")) {
            return parseBcaInternetJournal(text);
        }
        if (domain.endsWith("danamon.co.id")) {
            return parseDanamonQris(text);
        }
        if (domain.endsWith("jago.com")) {
            return parseJago(subject, text);
        }
        return null;
    }

    /** Kartu kredit BCA kini dikirim dari kartukreditbca@bca.co.id (dulu klikbca.com). */
    private static boolean isBcaCreditCard(String sender, String subject, String text) {
        if (sender != null && sender.toLowerCase(Locale.ROOT).contains("kartukredit")) {
            return true;
        }
        if (subject != null && subject.toLowerCase(Locale.ROOT).contains("credit card")) {
            return true;
        }
        return text.toLowerCase(Locale.ROOT).contains("kartu kredit bca");
    }

    /** BCA Credit Card Transaction Notification (regex existing dulu, V2 fallback). */
    private ParsedTransaction parseBcaCreditCard(String text) {
        ParsedTransaction parsed = regexTransaction(
                field(text, "Merchant\\s*/\\s*ATM"),
                field(text, "Sejumlah"),
                parseDateTime(field(text, "Pada\\s+Tanggal")));
        return parsed != null ? parsed : parseBcaCreditCardV2(text);
    }

    /** Fallback: label & value terpisah baris (template HTML pretty-printed). */
    private ParsedTransaction parseBcaCreditCardV2(String text) {
        return regexTransaction(
                fieldMultiline(text, "Merchant\\s*/\\s*ATM"),
                fieldMultiline(text, "Sejumlah"),
                parseDateTime(fieldMultiline(text, "Pada\\s+Tanggal")));
    }

    /** BCA Internet Transaction Journal / myBCA (regex existing dulu, V2 fallback). */
    private ParsedTransaction parseBcaInternetJournal(String text) {
        String status = field(text, "Status");
        if (status != null && !status.toLowerCase(Locale.ROOT).contains("berhasil")) {
            return null;
        }
        ParsedTransaction parsed = regexTransaction(
                field(text, "Pembayaran\\s+Ke"),
                field(text, "Total\\s+Bayar"),
                parseDateTime(field(text, "Tanggal\\s+Transaksi")));
        return parsed != null ? parsed : parseBcaInternetJournalV2(text);
    }

    /** Fallback: label multiline + varian Transfer/VA (tanpa label "Pembayaran Ke"). */
    private ParsedTransaction parseBcaInternetJournalV2(String text) {
        String status = fieldMultiline(text, "Status");
        if (status != null && !status.toLowerCase(Locale.ROOT).contains("berhasil")) {
            return null;
        }
        String merchant = firstNonNull(
                fieldMultiline(text, "Pembayaran\\s+Ke"),
                fieldMultiline(text, "Nama\\s+Perusahaan\\s*/\\s*Produk"),
                fieldMultiline(text, "Nama\\s+Penerima"),
                fieldMultiline(text, "Rekening\\s+Tujuan"));
        String amount = firstNonNull(
                fieldMultiline(text, "Total\\s+Bayar"),
                fieldMultiline(text, "Nominal"),
                fieldMultiline(text, "Total\\s+Tagihan"));
        return regexTransaction(merchant, amount,
                parseDateTime(fieldMultiline(text, "Tanggal\\s+Transaksi")));
    }

    /** D-Bank PRO QRIS Berhasil. */
    private ParsedTransaction parseDanamonQris(String text) {
        String status = field(text, "Status");
        if (status != null && !status.toLowerCase(Locale.ROOT).contains("berhasil")) {
            return null;
        }
        return regexTransaction(
                field(text, "Merchant\\s+Tujuan"),
                field(text, "Nominal"),
                parseDateTime(field(text, "Tanggal\\s+Pembayaran")));
    }

    /** Ambil value dari baris "Label : value" (titik dua opsional). */
    private static String field(String text, String labelRegex) {
        Matcher m = Pattern.compile("(?im)^\\s*" + labelRegex + "\\s*:?\\s*(.+?)\\s*$").matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Fallback bila label & value terpisah baris (mis. "Merchant / ATM" lalu
     * ":" lalu value di baris berikut — akibat HTML pretty-printed). Regex
     * existing (sebaris) dicoba dulu; ":" sendirian tidak dihitung sebagai value.
     */
    private static String fieldMultiline(String text, String labelRegex) {
        String inline = field(text, labelRegex);
        if (inline != null && !inline.replace(":", "").isBlank()) {
            return inline;
        }
        Pattern label = Pattern.compile("^\\s*" + labelRegex + "\\s*:?\\s*$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (label.matcher(lines[i].trim()).matches()) {
                for (int j = i + 1; j < Math.min(i + 3, lines.length); j++) {
                    String next = lines[j].trim().replaceFirst("^:\\s*", "").trim();
                    if (!next.isEmpty() && !next.equals(":")) {
                        return next;
                    }
                }
            }
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** Bank Jago: payment notification (Inggris, line-based). */
    private ParsedTransaction parseJago(String subject, String text) {
        String combined = ((subject == null ? "" : subject) + "\n" + text).toLowerCase(Locale.ROOT);
        if (combined.contains("between your pockets")) {
            throw new NotExpenseException("Transfer antar Pocket sendiri");
        }
        String status = fieldMultiline(text, "Transaction\\s+Status");
        if (status != null && !status.toLowerCase(Locale.ROOT).contains("success")) {
            return null;
        }
        return regexTransaction(
                fieldMultiline(text, "To\\b"),
                fieldMultiline(text, "Amount"),
                parseDateTime(fieldMultiline(text, "Transaction\\s+Date")));
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

    /**
     * Parse nominal yang bisa menyertakan kode mata uang (mis. "Rp240.390,00",
     * "IDR 59.000", "USD 0,45"). Mengembalikan nilai desimal asli tanpa dibulatkan.
     */
    static Money parseMoney(String raw) {
        if (raw == null) {
            return null;
        }
        String currency = "IDR";
        Matcher cm = CURRENCY.matcher(raw.toUpperCase(Locale.ROOT));
        while (cm.find()) {
            String code = cm.group(1);
            currency = "RP".equals(code) ? "IDR" : code;
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
        try {
            BigDecimal value = new BigDecimal(normalizeNumber(sb.toString()));
            return new Money(currency, negative ? value.negate() : value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Bangun transaksi REGEX; null bila nominal tak terbaca (agar fallback ke AI). */
    private ParsedTransaction regexTransaction(String merchant, String amountRaw, LocalDateTime date) {
        if (merchant == null) {
            return null;
        }
        Money money = parseMoney(amountRaw);
        if (money == null || money.amount().signum() <= 0) {
            return null;
        }
        return toIdrTransaction(merchant, money, date, null, null, "REGEX");
    }

    /**
     * Finalisasi nominal: IDR langsung (bulat, null bila 0 agar fallback AI);
     * non-IDR dikonversi pakai kurs tanggal transaksi. Bila kurs tak tersedia
     * atau hasilnya bulat 0, lempar exception supaya baris ditandai FAILED.
     */
    private ParsedTransaction toIdrTransaction(String merchant, Money money, LocalDateTime date,
                                               String budget, String category, String method) {
        boolean fromAi = "AI".equals(method);
        if ("IDR".equals(money.currency())) {
            long amount = money.amount().setScale(0, RoundingMode.HALF_UP).longValue();
            if (amount <= 0) {
                if (fromAi) {
                    throw new AiParseException("Nominal tidak terbaca");
                }
                return null;
            }
            return new ParsedTransaction(merchant, amount, date, budget, category, method);
        }
        String exchangeDate = ExchangeRateService.exchangeDateOf(date == null ? null : date.toString());
        Double rate = exchangeRateService.rateToIdr(money.currency(), exchangeDate);
        if (rate == null) {
            String message = "Kurs " + money.currency() + " tidak tersedia untuk " + exchangeDate;
            throw fromAi ? new AiParseException(message) : new ValidationException(message);
        }
        long idr = money.amount().multiply(BigDecimal.valueOf(rate))
                .setScale(0, RoundingMode.HALF_UP).longValue();
        if (idr <= 0) {
            String message = "Nominal " + money.currency() + " terlalu kecil untuk dikonversi ke IDR";
            throw fromAi ? new AiParseException(message) : new ValidationException(message);
        }
        return new ParsedTransaction(merchant, idr, date, budget, category, method,
                money.currency(), money.amount().doubleValue(), rate, exchangeDate);
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
                formatter("dd MMMM yyyy HH:mm"),
                formatter("dd MMMM yyyy, HH:mm:ss"),
                formatter("dd MMMM yyyy, HH:mm"));
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
        String raw;
        try {
            try {
                raw = callGeminiWithRetry(text);
            } catch (RetryableException | IOException geminiFailure) {
                LOGGER.warn("Gemini exhausted for email parse: {}; falling back to DeepSeek",
                        geminiFailure.getMessage());
                raw = callDeepSeekText(text);
            }
        } catch (Exception e) {
            LOGGER.warn("email AI call failed: {}", e.getMessage());
            throw new AiParseException("Gagal memanggil AI: " + e.getMessage(), e);
        }
        try {
            JsonNode json = objectMapper.readTree(raw);
            boolean isExpense = json.path("isExpense").asBoolean(false);
            if (!isExpense) {
                throw new NotExpenseException("Bukan transaksi pengeluaran");
            }
            String merchant = json.path("merchant").asText("").trim();
            String budget = json.path("suggestedBudget").asText("").trim();
            String category = json.path("suggestedCategory").asText("").trim();
            Money money = moneyFromAi(json.path("amount"), json.path("currency").asText(""));
            if (money == null || money.amount().signum() <= 0) {
                throw new AiParseException("Nominal tidak terbaca");
            }
            return toIdrTransaction(
                    merchant.isBlank() ? "Transaksi email" : merchant,
                    money,
                    parseDateTime(json.path("dateTime").asText("")),
                    budget.isBlank() ? null : budget,
                    category.isBlank() ? null : category,
                    "AI");
        } catch (NotExpenseException | AiParseException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("email AI parse failed: {}", e.getMessage());
            throw new AiParseException("Gagal membaca email: " + e.getMessage(), e);
        }
    }

    /** Baca node amount dari AI yang bisa berupa number atau string berformat. */
    private Money moneyFromAi(JsonNode amountNode, String currencyField) {
        if (amountNode == null || amountNode.isMissingNode() || amountNode.isNull()) {
            return null;
        }
        String currency = currencyField == null ? "" : currencyField.trim().toUpperCase(Locale.ROOT);
        if (amountNode.isNumber()) {
            BigDecimal value = BigDecimal.valueOf(amountNode.asDouble());
            return new Money(currency.isEmpty() ? "IDR" : currency, value);
        }
        Money parsed = parseMoney(amountNode.asText(""));
        if (parsed == null) {
            return null;
        }
        if (!currency.isEmpty() && "IDR".equals(parsed.currency())) {
            return new Money(currency, parsed.amount());
        }
        return parsed;
    }

    private String callGeminiWithRetry(String text) throws Exception {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callGemini(text);
            } catch (RetryableException | IOException e) {
                long suggested = e instanceof RetryableException retryable ? retryable.retryAfterMillis() : 0;
                if (attempt >= maxAttempts) {
                    throw e;
                }
                LOGGER.warn("email AI attempt {}/{} failed: {}; retrying", attempt, maxAttempts, e.getMessage());
                sleepRetry(suggested);
            }
        }
        throw new IllegalStateException("Gemini exhausted all attempts");
    }

    private void sleepRetry(long suggestedMillis) throws InterruptedException {
        long delay = Math.max(retryDelayMs, suggestedMillis);
        if (retryAfterCapMs > 0) {
            delay = Math.min(delay, retryAfterCapMs);
        }
        if (delay > 0) {
            Thread.sleep(delay);
        }
    }

    /** Ambil jeda dari header Retry-After (detik) atau pesan body ("retry in Xs"). */
    static long retryAfterMillis(HttpHeaders headers, String body) {
        if (headers != null) {
            long fromHeader = headers.firstValue("Retry-After")
                    .map(EmailParserService::parseSeconds).orElse(0L);
            if (fromHeader > 0) {
                return fromHeader;
            }
        }
        if (body != null) {
            Matcher matcher = RETRY_IN.matcher(body);
            if (matcher.find()) {
                return parseSeconds(matcher.group(1));
            }
        }
        return 0L;
    }

    private static long parseSeconds(String value) {
        try {
            return Math.round(Double.parseDouble(value.trim()) * 1000);
        } catch (Exception e) {
            return 0L;
        }
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
                throw new RetryableException("Gemini returned HTTP " + code,
                        retryAfterMillis(response.headers(), response.body()));
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

    /** Fallback sekali ke DeepSeek (teks) setelah semua attempt Gemini habis. */
    protected String callDeepSeekText(String text) throws Exception {
        if (deepseekApiKey.isBlank()) {
            throw new IllegalStateException("Gemini gagal dan DEEPSEEK_API_KEY belum dikonfigurasi");
        }
        String input = text.length() > MAX_AI_INPUT ? text.substring(0, MAX_AI_INPUT) : text;
        Map<String, Object> body = Map.of(
                "model", deepseekModel,
                "messages", List.of(Map.of("role", "user", "content", buildAiPrompt() + "\n\n" + input)),
                "response_format", Map.of("type", "json_object"),
                "thinking", Map.of("type", "disabled"),
                "max_tokens", 2048);
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(deepseekBaseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + deepseekApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            if (code == 429 || code >= 500) {
                throw new RetryableException("DeepSeek returned HTTP " + code,
                        retryAfterMillis(response.headers(), response.body()));
            }
            throw new IllegalStateException("DeepSeek returned HTTP " + code);
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode result = root.path("choices").path(0).path("message").path("content");
        if (result.isMissingNode() || result.asText().isBlank()) {
            throw new IllegalStateException("DeepSeek returned no analysis");
        }
        return result.asText();
    }

    private String buildAiPrompt() {
        List<BudgetOption> options = budgetRepository.getOptions();
        List<String> lines = new java.util.ArrayList<>();
        for (BudgetOption budget : options) {
            String line = "- " + budget.name();
            if (budget.description() != null && !budget.description().isBlank()) {
                line += ": " + budget.description();
            }
            lines.add(line);
            for (CategoryOption category : budget.categories()) {
                String catLine = "    * " + category.name();
                if (category.description() != null && !category.description().isBlank()) {
                    catLine += ": " + category.description();
                }
                lines.add(catLine);
            }
        }
        String budgetList = lines.isEmpty() ? "(tidak ada budget terdaftar)" : String.join("\n", lines);
        return "Kamu mengekstrak transaksi pengeluaran dari email notifikasi bank/e-wallet Indonesia.\n"
                + "Balas HANYA JSON dengan struktur:\n"
                + "{\"isExpense\":<true bila ini pembayaran/pengeluaran, false bila bukan (mis. transfer masuk/refund)>,\n"
                + "\"merchant\":\"nama merchant/tujuan\",\n"
                + "\"amount\":<nominal dalam MATA UANG ASLI transaksi, boleh desimal (jangan konversi ke IDR)>,\n"
                + "\"currency\":\"kode mata uang transaksi (mis. IDR, USD); IDR bila tidak disebutkan\",\n"
                + "\"dateTime\":\"waktu transaksi format YYYY-MM-DD HH:mm:ss\",\n"
                + "\"suggestedBudget\":\"<nama budget paling cocok atau string kosong>\",\n"
                + "\"suggestedCategory\":\"<nama category paling cocok atau string kosong>\"}\n"
                + "JANGAN mengonversi nominal ke Rupiah — isi \"amount\" apa adanya dalam \"currency\" aslinya; "
                + "sistem yang akan mengonversi.\n"
                + "Daftar budget (induk) beserta category (sub) yang tersedia:\n" + budgetList + "\n"
                + "Isi \"suggestedCategory\" dengan category milik budget yang dipilih; bila ragu isi string kosong "
                + "(jangan menulis \"uncategorized\"). "
                + "Abaikan nominal saldo, biaya admin yang bukan bagian transaksi, dan nomor referensi.";
    }
}
