package com.expensetracker.service;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
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

    @Value("${upload.dir:/app/uploads}")
    private String uploadDir;

    public InvoiceAnalysisService(InvoiceRepository invoiceRepository,
                                  BudgetRepository budgetRepository,
                                  ObjectMapper objectMapper,
                                  @Value("${ai.gemini-api-key:}") String apiKey,
                                  @Value("${ai.model:gemini-3.5-flash-lite}") String model,
                                  @Value("${ai.timeout:600}") long timeoutSeconds) {
        this.invoiceRepository = invoiceRepository;
        this.budgetRepository = budgetRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "gemini-3.5-flash-lite" : model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void trigger(String invoiceId) {
        executor.execute(() -> analyze(invoiceId));
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
            String raw = callGemini(bytes, mime, prompt);
            AiAnalysisResponse analysis = objectMapper.readValue(raw, AiAnalysisResponse.class);
            if (!hasPurchases(analysis)) {
                invoiceRepository.markNotInvoice(invoiceId, "Bukan struk invoice");
                return;
            }
            // Bersihkan tanggal halusinasi AI sebelum disimpan & dipakai frontend,
            // agar invoice tidak pindah periode / expense tidak dibuat di tanggal ngawur.
            String cleanedDate = cleanDate(analysis.dateTime());
            AiAnalysisResponse clean = new AiAnalysisResponse(
                    analysis.storeName(), analysis.total(), cleanedDate, analysis.items());
            applyPurchaseDate(invoiceId, clean);
            invoiceRepository.updateAnalysis(invoiceId, InvoiceStatus.TO_REVIEW.value(),
                    objectMapper.writeValueAsString(clean));
        } catch (Exception e) {
            LOGGER.error("invoice analysis failed for {}: {}", invoiceId, e.getMessage());
            invoiceRepository.updateError(invoiceId, e.getMessage());
        }
    }

    /** Normalisasi & validasi tanggal hasil AI; "" bila blank / tidak masuk akal (masa depan / terlalu tua). */
    private static String cleanDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(raw.trim().substring(0, 10));
            LocalDate today = LocalDate.now();
            if (date.isAfter(today) || date.isBefore(today.minusYears(2))) {
                return "";
            }
            return raw.trim();
        } catch (Exception e) {
            return "";
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
            // pakai 10 karakter pertama sebagai YYYY-MM-DD (menoleransi format penuh jam:menit:detik)
            LocalDate date = LocalDate.parse(raw.trim().substring(0, 10));
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

    private String callGemini(byte[] bytes, String mime, String prompt) throws Exception {
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
            throw new IllegalStateException("Gemini returned HTTP " + response.statusCode());
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
                .map(o -> o.name())
                .sorted()
                .toList();
        String budgetList = budgets.isEmpty()
                ? "(tidak ada budget terdaftar)"
                : String.join("\n", budgets.stream().map(b -> "- " + b).toList());
        return "Kamu adalah asisten pencatat keuangan. Baca struk/invoice berikut dan ekstrak item belanjanya.\n"
                + "Berikan output HANYA JSON tanpa teks lain, dengan struktur:\n"
                + "{\"storeName\":\"nama toko\",\"total\":<jumlah total integer>,"
                + "\"dateTime\":\"tanggal & jam belanja dari struk: format YYYY-MM-DD HH:mm:ss bila struk "
                + "menampilkan jam; bila hanya tanggal maka YYYY-MM-DD; string kosong jika tidak ada\","
                + "\"items\":[{\"name\":\"nama barang\",\"amount\":<harga integer>,"
                + "\"suggestedBudget\":\"<nama budget>\"}]}\n"
                + "Daftar budget tersedia (pilih yang paling cocok per item; isi string kosong jika ragu):\n"
                + budgetList + "\n"
                + "Gunakan Rupiah. JANGAN abaikan diskon/promo: jika struk menampilkan potongan harga "
                + "(Disk, Disc, Promo, Potongan, Voucher), masukkan sebagai item dengan amount NEGATIF, "
                + "contoh {\"name\":\"Diskon\",\"amount\":-5000}. "
                + "Abaikan baris saldo, kembalian, PPN, pembulatan, dan TOTAL (bukan barang). "
                + "Hanya masukkan barang yang benar-benar dibeli dan potongan yang valid. "
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
