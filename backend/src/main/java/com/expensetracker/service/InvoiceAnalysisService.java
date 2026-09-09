package com.expensetracker.service;

import com.expensetracker.config.MdcTask;
import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.InvoiceData;
import com.expensetracker.data.InvoiceRepository;
import com.expensetracker.model.AiAnalysisResponse;
import com.expensetracker.model.AiInvoiceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Menganalisis invoice (gambar/PDF) via Google Gemini secara async.
 * Status invoice: ANALYZING -> TO_REVIEW (sukses) / ERROR (gagal, bisa retry).
 * Analisis tidak memperlambat request upload; dijalankan di thread pool.
 */
@Service
public class InvoiceAnalysisService implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvoiceAnalysisService.class);
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";

    private final InvoiceRepository invoiceRepository;
    private final BudgetRepository budgetRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final String fxApi;
    private final Duration fxTimeout;

    @Value("${upload.dir:/app/uploads}")
    private String uploadDir;

    public InvoiceAnalysisService(InvoiceRepository invoiceRepository,
                                  BudgetRepository budgetRepository,
                                  ObjectMapper objectMapper,
                                  @Value("${ai.gemini-api-key:}") String apiKey,
                                  @Value("${ai.model:gemini-3.5-flash-lite}") String model,
                                  @Value("${ai.timeout:600}") long timeoutSeconds,
                                  @Value("${ai.max-attempts:50}") int maxAttempts,
                                  @Value("${ai.retry-delay-ms:2000}") long retryDelayMs,
                                  @Value("${ai.fx-api:}") String fxApi,
                                  @Value("${ai.fx-timeout-ms:8000}") long fxTimeoutMs) {
        this.invoiceRepository = invoiceRepository;
        this.budgetRepository = budgetRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "gemini-3.5-flash-lite" : model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxAttempts = maxAttempts < 1 ? 1 : maxAttempts;
        this.retryDelayMs = retryDelayMs < 0 ? 0 : retryDelayMs;
        this.fxApi = fxApi == null ? "" : fxApi.trim();
        this.fxTimeout = Duration.ofMillis(fxTimeoutMs < 1 ? 8000 : fxTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void trigger(String invoiceId) {
        executor.execute(MdcTask.wrap(() -> analyze(invoiceId)));
    }

    /** Dipanggil saat startup: analisis ulang invoice yang stuck ANALYZING. */
    public void recoverAnalyzingInvoices() {
        List<String> stuck = invoiceRepository.findByStatus(InvoiceStatus.ANALYZING.value());
        for (String id : stuck) {
            LOGGER.info("recovering stuck analyzing invoice {}", id);
            trigger(id);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        recoverAnalyzingInvoices();
    }

    private void analyze(String invoiceId) {
        try {
            InvoiceData invoice = invoiceRepository.findById(invoiceId);
            if (invoice == null) {
                return;
            }
            String photoPath = invoiceRepository.getPhotoPath(invoiceId);
            if (photoPath == null) {
                invoiceRepository.updateError(invoiceId, "Invoice file not found");
                return;
            }
            Path resolved = Path.of(uploadDir).resolve(photoPath).normalize();
            byte[] bytes = Files.readAllBytes(resolved);
            String mime = mimeTypeOf(photoPath);
            String prompt = buildPrompt();
            invoiceRepository.initRetry(invoiceId, maxAttempts);
            String raw = callGeminiWithRetry(invoiceId, bytes, mime, prompt);
            AiAnalysisResponse analysis = objectMapper.readValue(raw, AiAnalysisResponse.class);
            if (!hasPurchases(analysis)) {
                invoiceRepository.markNotInvoice(invoiceId, "Bukan struk invoice");
                return;
            }
            // Bersihkan tanggal halusinasi AI sebelum disimpan & dipakai frontend.
            // Periode invoice TIDAK dipindah di sini — tetap di periode upload hingga submit,
            // agar invoice selalu tampil di daftar /scan periode berjalan.
            String cleanedDate = cleanDate(analysis.dateTime());
            AiAnalysisResponse clean = applyCurrencyConversion(analysis, cleanedDate);
            invoiceRepository.updateAnalysis(invoiceId, InvoiceStatus.TO_REVIEW.value(),
                    objectMapper.writeValueAsString(clean));
        } catch (Exception e) {
            LOGGER.error("invoice analysis failed for {}: {}", invoiceId, e.getMessage());
            invoiceRepository.updateError(invoiceId, e.getMessage());
        }
    }

    /** Jalankan analisis secara sinkron (helper untuk unit test). */
    void analyzeForTest(String invoiceId) {
        analyze(invoiceId);
    }

    /**
     * Terapkan konversi mata uang asing -> IDR memakai kurs dari API eksternal
     * (fawazahmed0). Item & total dari AI diasumsikan dalam mata uang asli
     * (original), lalu dikonversi ke IDR di sini. originalTotal dipertahankan
     * dalam mata uang asli untuk kebutuhan UI. Bila kurs tidak bisa didapat
     * (API error / mata uang tidak ada / tanggal future), exchangeRate=null
     * dan nilai dibiarkan apa adanya (UI menampilkan "kurs tidak diketahui").
     */
    private AiAnalysisResponse applyCurrencyConversion(AiAnalysisResponse analysis, String cleanedDate) {
        String currency = analysis.currency();
        if (currency == null || currency.isBlank() || "IDR".equalsIgnoreCase(currency)) {
            return copyWith(analysis, cleanedDate, analysis.total(), null, null);
        }
        String base = currency.toUpperCase(java.util.Locale.ROOT);
        String exchangeDate = exchangeDateOf(cleanedDate);
        Double rate = fetchRate(base, exchangeDate);
        if (rate == null) {
            LOGGER.warn("fx rate unavailable for {} at {}, keeping original amounts", base, exchangeDate);
            return copyWith(analysis, cleanedDate, null, null, exchangeDate);
        }
        List<AiInvoiceItem> convertedItems = analysis.items() == null
                ? null
                : analysis.items().stream()
                        .map(it -> toIdrItem(it, rate))
                        .toList();
        Long total = analysis.total() == null ? null : Math.round(analysis.total() * rate);
        return copyWith(analysis, cleanedDate, total, rate, exchangeDate, convertedItems);
    }

    private AiInvoiceItem toIdrItem(AiInvoiceItem item, double rate) {
        Long amount = item.amount() == null ? null : Math.round(item.amount() * rate);
        return new AiInvoiceItem(item.name(), amount, item.suggestedBudget());
    }

    private static String exchangeDateOf(String cleanedDate) {
        if (cleanedDate == null || cleanedDate.isBlank()) {
            return java.time.LocalDate.now().toString();
        }
        String datePart = cleanedDate.substring(0, Math.min(10, cleanedDate.length()));
        try {
            return java.time.LocalDate.parse(datePart).toString();
        } catch (Exception e) {
            return java.time.LocalDate.now().toString();
        }
    }

    private AiAnalysisResponse copyWith(AiAnalysisResponse a, String cleanedDate,
                                        Long total, Double rate, String exchangeDate) {
        return copyWith(a, cleanedDate, total, rate, exchangeDate, a.items());
    }

    private AiAnalysisResponse copyWith(AiAnalysisResponse a, String cleanedDate,
                                        Long total, Double rate, String exchangeDate, List<AiInvoiceItem> items) {
        return new AiAnalysisResponse(
                a.storeName(), total, cleanedDate, a.currency(), rate, exchangeDate, a.originalTotal(), items);
    }

    /**
     * Ambil kurs 1 {base} = IDR dari API kurs (fawazahmed0) untuk tanggal tertentu.
     * Coba tanggal yang diminta; bila tanggal future/belum rilis, coba mundur
     * hingga 5 hari sebelum menyerah. Mengembalikan null bila gagal.
     */
    Double fetchRate(String base, String date) {
        if (fxApi.isBlank()) {
            return null;
        }
        for (int back = 0; back <= 5; back++) {
            String target = back == 0 ? date : java.time.LocalDate.parse(date).minusDays(back).toString();
            try {
                Double rate = fetchRateFor(base, target);
                if (rate != null) {
                    if (back > 0) {
                        LOGGER.warn("fx date {} unavailable, using {} for {}", date, target, base);
                    }
                    return rate;
                }
            } catch (Exception e) {
                LOGGER.warn("fx fetch failed for {} at {}: {}", base, target, e.getMessage());
            }
        }
        return null;
    }

    private Double fetchRateFor(String base, String date) throws Exception {
        String url = fxApi.replace("%DATE%", date).replace("%CUR%", base.toLowerCase(java.util.Locale.ROOT));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(fxTimeout.toMillis()))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode baseNode = root.path(base.toLowerCase(java.util.Locale.ROOT)).path("idr");
        if (baseNode.isMissingNode() || baseNode.asText().isBlank()) {
            return null;
        }
        double rate = baseNode.asDouble();
        return rate > 0 ? rate : null;
    }

    /**
     * Memanggil Gemini dengan retry untuk error transien (HTTP 429/5xx, koneksi/timeout).
     * Gagal permanen (4xx lain, response tidak valid) langsung dilempar tanpa retry.
     */
    private String callGeminiWithRetry(String invoiceId, byte[] bytes, String mime, String prompt) throws Exception {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callGemini(bytes, mime, prompt);
            } catch (RetryableException | IOException e) {
                invoiceRepository.incrementRetry(invoiceId);
                if (attempt >= maxAttempts) {
                    throw e;
                }
                LOGGER.warn("attempt {}/{} failed for invoice {}: {}; retrying in {}ms",
                        attempt + 1, maxAttempts, invoiceId, e.getMessage(), retryDelayMs);
                sleepRetry();
            } catch (Exception e) {
                // Error permanen (mis. 4xx non-transien, JSON tidak valid) — jangan retry.
                LOGGER.warn("non-retryable failure for invoice {}: {}", invoiceId, e.getMessage());
                throw e;
            }
        }
        throw new IllegalStateException("Gemini analysis exhausted all attempts");
    }

    private void sleepRetry() throws InterruptedException {
        if (retryDelayMs > 0) {
            Thread.sleep(retryDelayMs);
        }
    }

    /** Normalisasi & validasi tanggal hasil AI; "" bila blank / tidak masuk akal (masa depan / terlalu tua). */
    private static String cleanDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            LocalDate date = parseFlexibleDate(raw.trim());
            LocalDate today = LocalDate.now();
            if (date.isAfter(today) || date.isBefore(today.minusYears(2))) {
                return "";
            }
            return raw.trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** Parse tanggal yang bisa berbentuk YYYY-MM-DD maupun dd-MM-yyyy (format umum struk Indonesia). */
    private static LocalDate parseFlexibleDate(String raw) {
        String datePart = raw.substring(0, 10);
        try {
            return LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return LocalDate.parse(datePart, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        }
    }

    /**
     * Sesuaikan periode invoice sesuai tanggal belanja yang diekstrak AI,
     * agar periode invoice konsisten dengan tanggal expense yang akan dibuat.
     * Tanggal yang tidak masuk akal (masa depan / terlalu tua) ditolak agar
     * invoice tidak pindah periode akibat tanggal halusinasi AI.
     */
    private void applyPurchaseDate(String invoiceId, AiAnalysisResponse analysis) {
        String raw = analysis == null ? null : analysis.dateTime();
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            LocalDate date = parseFlexibleDate(raw.trim());
            LocalDate today = LocalDate.now();
            if (date.isAfter(today) || date.isBefore(today.minusYears(2))) {
                LOGGER.warn("ignoring implausible AI date {} for invoice {}", raw, invoiceId);
                return;
            }
            invoiceRepository.updatePeriod(invoiceId, PeriodSheetName.forDate(date), PeriodSheetName.periodStart(date));
        } catch (Exception e) {
            LOGGER.warn("invalid date from AI for {}: {}", invoiceId, raw);
        }
    }

    /** True jika ada minimal satu item dengan nominal positif (artinya benar struk belanja). */
    private static boolean hasPurchases(AiAnalysisResponse analysis) {
        if (analysis == null || analysis.items() == null || analysis.items().isEmpty()) {
            return false;
        }
        return analysis.items().stream()
                .anyMatch(it -> it.amount() != null && it.amount() > 0);
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
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            int code = response.statusCode();
            // 429 (rate limit) dan 5xx (failover sesaat) bersifat transien -> retry.
            if (code == 429 || code >= 500) {
                throw new RetryableException("Gemini returned HTTP " + code);
            }
            throw new IllegalStateException("Gemini returned HTTP " + code);
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            throw new IllegalStateException("Gemini returned no analysis");
        }
        return text.asText();
    }

    private String buildPrompt() {
        List<String> budgets = budgetRepository.getOptions().stream()
                .map(o -> {
                    String line = "- " + o.name();
                    if (o.description() != null && !o.description().isBlank()) {
                        line += ": " + o.description();
                    }
                    return line;
                })
                .sorted()
                .toList();
        String budgetList = budgets.isEmpty()
                ? "(tidak ada budget terdaftar)"
                : String.join("\n", budgets);
        return "Kamu adalah asisten pencatat keuangan. Baca struk/invoice berikut dan ekstrak item belanjanya.\n"
                + "Berikan output HANYA JSON tanpa teks lain, dengan struktur:\n"
                + "{\"storeName\":\"nama toko\",\"total\":<jumlah total integer dalam IDR>,"
                + "\"dateTime\":\"tanggal & jam belanja dari struk. PENTING: struk Indonesia biasanya menulis tanggal "
                + "dengan format dd-mm-yyyy (hari-bulan-tahun), contoh '14-08-2026' berarti 14 Agustus 2026, JANGAN "
                + "terbalik menjadi tahun 2014. Konversikan ke format YYYY-MM-DD HH:mm:ss (sertakan jam:menit:detik bila "
                + "struk menampilkannya; bila hanya tanggal pakai YYYY-MM-DD; string kosong jika tidak ada).\","
                + "\"currency\":\"kode mata uang struk (IDR default)\","
                + "\"originalTotal\":<nilai total asli dalam mata uang struk (desimal; null bila IDR)>,"
                + "\"items\":[{\"name\":\"nama barang\",\"amount\":<harga integer dalam MATA UANG ASLI struk>,"
                + "\"suggestedBudget\":\"<nama budget>\"}]}\n"
                + "Daftar budget tersedia (pilih yang paling cocok per item; isi string kosong jika ragu):\n"
                + budgetList + "\n"
                + "Deteksi mata uang struk, isi \"currency\" dengan kode mata uang asli (IDR default). "
                + "SELURUH jumlah (total dan setiap item) diisi dalam MATA UANG ASLI struk — JANGAN konversi ke "
                + "IDR (konversi dilakukan sistem belakangan). Nilai \"originalTotal\" sama dengan total struk (desimal "
                + "bila ada koma, integer bila tidak). Bila mata uang IDR, isi \"currency\"=\"IDR\" dan \"originalTotal\"=null. "
                + "JANGAN abaikan diskon/promo: jika struk menampilkan potongan harga "
                + "(Disk, Disc, Promo, Potongan, Voucher), masukkan sebagai item dengan amount NEGATIF, "
                + "contoh {\"name\":\"Diskon\",\"amount\":-5000}. "
                + "JANGAN abaikan PPN/Pajak dan service charge: jika struk menampilkannya, masukkan sebagai "
                + "item terpisah dengan amount POSITIF, contoh {\"name\":\"PPN 11%\",\"amount\":900,"
                + "\"suggestedBudget\":\"<budget barang dominan>\"} dan {\"name\":\"Service\",\"amount\":5000,"
                + "\"suggestedBudget\":\"<budget barang dominan>\"}. Untuk suggestedBudget item PPN/service, "
                + "ikuti budget yang paling cocok dengan jenis belanja struk tersebut; isi string kosong jika ragu. "
                + "JANGAN abaikan pembulatan: jika struk menampilkan baris pembulatan yang membuat grand total "
                + "berbeda dari jumlah seluruh item lainnya, masukkan sebagai item \"Pembulatan\" dengan amount "
                + "sebesar selisihnya (POSITIF bila grand total lebih besar, NEGATIF bila lebih kecil), contoh "
                + "{\"name\":\"Pembulatan\",\"amount\":22}; hanya tampilkan item Pembulatan bila struk memang memuat "
                + "pembulatan tertulis, jangan dibuat-buat bila tidak ada. "
                + "SETELAH memuat barang, diskon, PPN/pajak, service, dan pembulatan (bila ada), jika jumlah seluruh "
                + "item tersebut MASIH belum sama persis dengan GRAND TOTAL yang tertulis, tambahkan SATU item "
                + "\"Penyesuaian\" sebagai penyeimbang dengan amount = (GRAND TOTAL − jumlah semua item lain), "
                + "contoh {\"name\":\"Penyesuaian\",\"amount\":1042}; amount POSITIF bila grand total lebih besar, "
                + "NEGATIF bila lebih kecil, dan isi suggestedBudget dengan budget yang paling cocok (umumnya barang "
                + "dominan). Hanya tambahkan item Penyesuaian bila ada selisih tersisa; jika sudah balance, jangan "
                + "memunculkannya. "
                + "Abaikan baris saldo dan kembalian (bukan barang); jangan jadikan TOTAL sebagai item. "
                + "Hanya masukkan barang yang benar-benar dibeli dan potongan yang valid. "
                + "Jumlah seluruh item (barang, diskon negatif, PPN, service, pembulatan, dan penyesuaian) HARUS sama "
                + "persis dengan total struk. "
                + "Jika gambar BUKAN struk/invoice belanja (mis. foto orang, pemandangan, dokumen lain), "
                + "kembalikan JSON dengan items KOSONG: {\"storeName\":\"\",\"total\":0,\"items\":[]}.";
    }

    private String mimeTypeOf(String photoPath) {
        return photoPath.toLowerCase(Locale.ROOT).endsWith(".pdf") ? "application/pdf" : "image/jpeg";
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
