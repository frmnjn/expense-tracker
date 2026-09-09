package com.expensetracker.service;

import com.expensetracker.config.MdcTask;
import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.InvoiceData;
import com.expensetracker.data.InvoiceRepository;
import com.expensetracker.model.AiAnalysisResponse;
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

    @Value("${upload.dir:/app/uploads}")
    private String uploadDir;

    public InvoiceAnalysisService(InvoiceRepository invoiceRepository,
                                  BudgetRepository budgetRepository,
                                  ObjectMapper objectMapper,
                                  @Value("${ai.gemini-api-key:}") String apiKey,
                                  @Value("${ai.model:gemini-3.5-flash-lite}") String model,
                                  @Value("${ai.timeout:600}") long timeoutSeconds,
                                  @Value("${ai.max-attempts:50}") int maxAttempts,
                                  @Value("${ai.retry-delay-ms:2000}") long retryDelayMs) {
        this.invoiceRepository = invoiceRepository;
        this.budgetRepository = budgetRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "gemini-3.5-flash-lite" : model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxAttempts = maxAttempts < 1 ? 1 : maxAttempts;
        this.retryDelayMs = retryDelayMs < 0 ? 0 : retryDelayMs;
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
            AiAnalysisResponse clean = new AiAnalysisResponse(
                    analysis.storeName(), analysis.total(), cleanedDate, analysis.items());
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
                + "{\"storeName\":\"nama toko\",\"total\":<jumlah total integer>,"
                + "\"dateTime\":\"tanggal & jam belanja dari struk. PENTING: struk Indonesia biasanya menulis tanggal "
                + "dengan format dd-mm-yyyy (hari-bulan-tahun), contoh '14-08-2026' berarti 14 Agustus 2026, JANGAN "
                + "terbalik menjadi tahun 2014. Konversikan ke format YYYY-MM-DD HH:mm:ss (sertakan jam:menit:detik bila "
                + "struk menampilkannya; bila hanya tanggal pakai YYYY-MM-DD; string kosong jika tidak ada).\","
                + "\"items\":[{\"name\":\"nama barang\",\"amount\":<harga integer>,"
                + "\"suggestedBudget\":\"<nama budget>\"}]}\n"
                + "Daftar budget tersedia (pilih yang paling cocok per item; isi string kosong jika ragu):\n"
                + budgetList + "\n"
                + "Gunakan Rupiah. JANGAN abaikan diskon/promo: jika struk menampilkan potongan harga "
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
