package com.expensetracker.service;

import com.expensetracker.config.MdcTask;
import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.VoiceNoteData;
import com.expensetracker.data.VoiceNoteRepository;
import com.expensetracker.model.AiInvoiceItem;
import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.CategoryOption;
import com.expensetracker.model.VoiceAnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menganalisis rekaman suara secara async: transkripsi + ekstraksi satu
 * expense dalam satu panggilan Gemini (audio multimodal). Status voice note:
 * ANALYZING -> TO_REVIEW (sukses) / ERROR (gagal, bisa retry).
 */
@Service
public class VoiceNoteAnalysisService implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceNoteAnalysisService.class);
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final String PROVIDER_GEMINI = "GEMINI";
    private static final Pattern RETRY_IN = Pattern.compile("retry in ([0-9]+(?:\\.[0-9]+)?)s",
            Pattern.CASE_INSENSITIVE);

    private final VoiceNoteRepository voiceNoteRepository;
    private final BudgetRepository budgetRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100),
            runnable -> new Thread(runnable, "voice-analysis"));
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long retryAfterCapMs;

    @Value("${upload.dir:/app/uploads}")
    private String uploadDir;

    public VoiceNoteAnalysisService(VoiceNoteRepository voiceNoteRepository,
                                    BudgetRepository budgetRepository,
                                    ObjectMapper objectMapper,
                                    @Value("${ai.gemini-api-key:}") String apiKey,
                                    @Value("${ai.voice-model:gemini-3.5-flash-lite}") String model,
                                    @Value("${ai.timeout:600}") long timeoutSeconds,
                                    @Value("${ai.max-attempts:50}") int maxAttempts,
                                    @Value("${ai.retry-delay-ms:2000}") long retryDelayMs,
                                    @Value("${ai.retry-after-cap-ms:120000}") long retryAfterCapMs) {
        this.voiceNoteRepository = voiceNoteRepository;
        this.budgetRepository = budgetRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "gemini-3.5-flash-lite" : model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxAttempts = maxAttempts < 1 ? 1 : maxAttempts;
        this.retryDelayMs = retryDelayMs < 0 ? 0 : retryDelayMs;
        this.retryAfterCapMs = retryAfterCapMs < 0 ? 0 : retryAfterCapMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void trigger(String voiceNoteId) {
        if (!inFlight.add(voiceNoteId)) {
            LOGGER.info("voice note {} is already queued/analyzing, skipping duplicate trigger", voiceNoteId);
            return;
        }
        try {
            executor.execute(MdcTask.wrap(() -> {
                try {
                    analyze(voiceNoteId);
                } finally {
                    inFlight.remove(voiceNoteId);
                }
            }));
        } catch (RejectedExecutionException e) {
            inFlight.remove(voiceNoteId);
            LOGGER.warn("voice analysis queue full, rejecting {}", voiceNoteId);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String id : voiceNoteRepository.findByStatus(VoiceNoteStatus.ANALYZING.value())) {
            LOGGER.info("recovering stuck analyzing voice note {}", id);
            trigger(id);
        }
    }

    private void analyze(String voiceNoteId) {
        try {
            VoiceNoteData note = voiceNoteRepository.findById(voiceNoteId);
            if (note == null) {
                return;
            }
            LOGGER.info("analyzing voice note {} with model {}", voiceNoteId, model);
            Path file = Path.of(uploadDir).resolve(note.filePath()).normalize();
            if (!Files.exists(file)) {
                voiceNoteRepository.updateError(voiceNoteId, "Voice note file not found");
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            String prompt = buildPrompt(note.referenceDate());

            voiceNoteRepository.setProvider(voiceNoteId, PROVIDER_GEMINI);
            voiceNoteRepository.initRetry(voiceNoteId, maxAttempts);
            String raw = callGeminiWithRetry(voiceNoteId, bytes, note.mimeType(), prompt);

            VoiceAnalysisResponse analysis = objectMapper.readValue(raw, VoiceAnalysisResponse.class);
            if (analysis.isExpense() == null || !analysis.isExpense()) {
                voiceNoteRepository.markNotExpense(voiceNoteId, "Rekaman bukan catatan pengeluaran");
                return;
            }
            List<AiInvoiceItem> items = analysis.items();
            if (items == null || items.isEmpty()) {
                // Kompatibilitas bila model mengembalikan format lama (satu expense).
                if (analysis.name() != null && analysis.amount() != null) {
                    items = List.of(new AiInvoiceItem(analysis.name(), analysis.amount(),
                            analysis.budget(), analysis.category()));
                }
            }
            if (!hasPurchaseItems(items)) {
                voiceNoteRepository.markNotExpense(voiceNoteId, "Rekaman bukan catatan pengeluaran");
                return;
            }
            VoiceAnalysisResponse clean = new VoiceAnalysisResponse(
                    analysis.transcript(),
                    true,
                    analysis.storeName(),
                    cleanDate(analysis.dateTime()),
                    items,
                    null,
                    null,
                    null,
                    null,
                    null);
            voiceNoteRepository.updateAnalysis(voiceNoteId, VoiceNoteStatus.TO_REVIEW.value(),
                    objectMapper.writeValueAsString(clean), clean.transcript());
        } catch (Exception e) {
            LOGGER.error("voice note analysis failed for {}: {}", voiceNoteId, e.getMessage());
            voiceNoteRepository.updateError(voiceNoteId, e.getMessage());
        }
    }

    /** Jalankan analisis secara sinkron (helper untuk unit test). */
    void analyzeForTest(String voiceNoteId) {
        analyze(voiceNoteId);
    }

    private String callGeminiWithRetry(String voiceNoteId, byte[] bytes, String mime, String prompt) throws Exception {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callGemini(bytes, mime, prompt);
            } catch (RetryableException | IOException e) {
                long suggested = e instanceof RetryableException retryable ? retryable.retryAfterMillis() : 0;
                voiceNoteRepository.incrementRetry(voiceNoteId);
                if (attempt >= maxAttempts) {
                    throw e;
                }
                LOGGER.warn("voice attempt {}/{} failed for {}: {}; retrying",
                        attempt + 1, maxAttempts, voiceNoteId, e.getMessage());
                sleepRetry(suggested);
            } catch (Exception e) {
                LOGGER.warn("non-retryable voice failure for {}: {}", voiceNoteId, e.getMessage());
                throw e;
            }
        }
        throw new IllegalStateException("Voice analysis exhausted all attempts");
    }

    protected String callGemini(byte[] bytes, String mime, String prompt) throws Exception {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }
        String encoded = Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", prompt),
                        Map.of("inline_data", Map.of("mime_type", mime, "data", encoded))))),
                "generationConfig", Map.of("responseMimeType", "application/json"));
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_BASE + "/models/" + model + ":generateContent?key=" + apiKey))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            if (code == 429 || code >= 500) {
                throw new RetryableException("Gemini returned HTTP " + code,
                        retryAfterMillis(response.headers(), response.body()));
            }
            throw new IllegalStateException("Gemini returned HTTP " + code);
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            throw new IllegalStateException("Gemini returned no voice analysis");
        }
        return text.asText();
    }

    private void sleepRetry(long suggestedMillis) throws InterruptedException {
        long delay = Math.max(retryDelayMs, suggestedMillis);
        if (retryAfterCapMs > 0) {
            delay = Math.min(delay, retryAfterCapMs);
        }
        if (delay > 0) {
            LOGGER.info("sleeping {}ms before next voice attempt", delay);
            Thread.sleep(delay);
        }
    }

    static long retryAfterMillis(HttpHeaders headers, String body) {
        if (headers != null) {
            long fromHeader = headers.firstValue("Retry-After").map(VoiceNoteAnalysisService::parseSeconds).orElse(0L);
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

    private static String cleanDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        try {
            LocalDate date = parseFlexibleDate(value);
            LocalDate today = LocalDate.now();
            if (date.isAfter(today) || date.isBefore(today.minusYears(2))) {
                return "";
            }
            return value;
        } catch (Exception e) {
            return "";
        }
    }

    private static LocalDate parseFlexibleDate(String raw) {
        String datePart = raw.substring(0, Math.min(raw.length(), 10));
        try {
            return LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return LocalDate.parse(datePart, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        }
    }

    private static boolean hasPurchaseItems(List<AiInvoiceItem> items) {
        return items != null && items.stream().anyMatch(it -> it.amount() != null && it.amount() > 0);
    }

    private static String budgetLine(BudgetOption budget) {
        String line = "- " + budget.name();
        if (budget.description() != null && !budget.description().isBlank()) {
            line += ": " + budget.description();
        }
        return line;
    }

    private static String categoryLine(CategoryOption category) {
        String line = category.name();
        if (category.description() != null && !category.description().isBlank()) {
            line += ": " + category.description();
        }
        return line;
    }

    private String buildPrompt(String referenceDate) {
        List<BudgetOption> options = budgetRepository.getOptions();
        List<String> lines = new ArrayList<>();
        for (BudgetOption budget : options) {
            lines.add(budgetLine(budget));
            for (CategoryOption category : budget.categories()) {
                lines.add("    * " + categoryLine(category));
            }
        }
        String budgetList = lines.isEmpty() ? "(tidak ada budget terdaftar)" : String.join("\n", lines);
        return "Kamu adalah asisten pencatat keuangan. Dengar rekaman suara berikut dan ubah menjadi DAFTAR item "
                + "pengeluaran.\n"
                + "Waktu referensi user (waktu lokal saat merekam): " + referenceDate + ". Gunakan waktu ini untuk "
                + "menafsirkan tanggal/jam relatif seperti 'hari ini', 'kemarin', 'tadi pagi', atau 'jam 3 sore'.\n"
                + "Berikan output HANYA JSON tanpa teks lain dengan struktur:\n"
                + "{\"transcript\":\"transkrip lengkap ucapan\",\"isExpense\":true,"
                + "\"storeName\":\"nama toko/tempat (string kosong bila tidak disebut)\","
                + "\"dateTime\":\"YYYY-MM-DD HH:mm atau string kosong bila tidak disebut\","
                + "\"items\":[{\"name\":\"nama item\",\"amount\":<nominal integer Rupiah>,"
                + "\"suggestedBudget\":\"nama budget\",\"suggestedCategory\":\"nama category\"}]}\n"
                + "PENTING: pisahkan SETIAP barang/pos yang disebut menjadi item terpisah. "
                + "Contoh: 'belanja sayur dua puluh ribu, telur tiga puluh ribu, ayam lima puluh ribu' "
                + "menghasilkan TIGA item. Jika user hanya menyebut satu total tanpa rincian, buat satu item "
                + "dengan nama ringkas (mis. 'Belanja sayur').\n"
                + "Aturan nominal: konversi ucapan Indonesia ke integer tanpa titik/koma. "
                + "Contoh: 'lima puluh ribu'=50000, '50rb'=50000, 'satu juta dua ratus ribu'=1200000.\n"
                + "Aturan nama item: ringkas dan jelas. Bila ada tempat/keperluan, boleh disertakan dalam nama item.\n"
                + "Aturan storeName: isi nama toko/tempat belanja yang disebut user, contoh 'Tukang Sayur Komplek', "
                + "'Indomaret', 'Warung Bu Sri'. Jika tidak disebut, isi string kosong.\n"
                + "Aturan budget: pilih HANYA dari daftar budget di bawah. \"suggestedCategory\" harus category milik "
                + "budget yang dipilih; bila tidak cocok kosongkan dengan string kosong. Jangan menulis 'uncategorized'.\n"
                + "Daftar budget (induk) beserta category (sub) yang tersedia:\n" + budgetList + "\n"
                + "Jika rekaman BUKAN catatan pengeluaran (mis. obrolan biasa), kembalikan "
                + "{\"transcript\":\"...\",\"isExpense\":false,\"storeName\":\"\",\"dateTime\":\"\",\"items\":[]}.";
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
