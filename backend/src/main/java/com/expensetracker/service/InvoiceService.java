package com.expensetracker.service;

import com.expensetracker.data.InvoiceAnalysis;
import com.expensetracker.data.InvoiceData;
import com.expensetracker.data.InvoiceRepository;
import com.expensetracker.model.AiAnalysisResponse;
import com.expensetracker.model.InvoiceDetailResponse;
import com.expensetracker.model.InvoiceResponse;
import com.expensetracker.model.InvoicesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvoiceService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");

    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    @Value("${upload.dir:/app/uploads}")
    private String uploadDir;

    public InvoiceService(InvoiceRepository invoiceRepository, ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.objectMapper = objectMapper;
    }

    public InvoicesResponse listByDate(String dateTime, boolean scanOnly) {
        if (dateTime == null || dateTime.isBlank()) {
            throw new ValidationException("Date is required");
        }
        LocalDateTime parsed;
        try {
            parsed = PeriodSheetName.parseLenient(dateTime);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Date must be in yyyy-MM-dd HH:mm format");
        }
        String period = PeriodSheetName.forDate(parsed.toLocalDate());
        List<InvoiceResponse> list = (scanOnly ? invoiceRepository.findAllScan()
                : invoiceRepository.findByPeriod(period)).stream()
                .map(this::toResponse)
                .toList();
        return new InvoicesResponse(list);
    }

    public InvoiceDetailResponse getDetail(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Invoice id is required");
        }
        InvoiceData invoice = requireInvoice(id);
        InvoiceAnalysis analysis = invoiceRepository.findAnalysis(id);
        AiAnalysisResponse parsed = null;
        if (analysis != null && analysis.analysisJson() != null && !analysis.analysisJson().isBlank()) {
            try {
                parsed = objectMapper.readValue(analysis.analysisJson(), AiAnalysisResponse.class);
            } catch (Exception e) {
                LOGGER.warn("failed to parse analysis for invoice {}: {}", id, e.getMessage());
            }
        }
        return new InvoiceDetailResponse(
                invoice.id(),
                typeOf(invoice),
                analysis == null ? invoice.status() : analysis.status(),
                analysis == null ? null : analysis.errorMessage(),
                invoice.originalName(),
                parsed);
    }

    public String getInvoicePhotoPath(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Invoice id is required");
        }
        String photoPath = invoiceRepository.getPhotoPath(id);
        if (photoPath == null) {
            return null;
        }
        return Path.of(uploadDir).resolve(photoPath).normalize().toString();
    }

    public String createInvoice(String period, LocalDate periodStart, MultipartFile file) {
        String id = storeInvoice(period, periodStart, file);
        return id;
    }

    /**
     * Membuat invoice untuk alur AI (status ANALYZING). Foto biasa tetap
     * memakai {@link #createInvoice} (status default SUBMITTED).
     */
    public String createInvoiceForAi(String period, LocalDate periodStart, MultipartFile file) {
        String id = storeInvoice(period, periodStart, file);
        invoiceRepository.updateStatus(id, InvoiceStatus.ANALYZING.value());
        invoiceRepository.setScanFlow(id);
        return id;
    }

    private String storeInvoice(String period, LocalDate periodStart, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Photo is required");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException("Only jpg, png, and pdf are allowed");
        }
        String invoiceId = UUID.randomUUID().toString();
        String filename = "pdf".equals(ext) ? invoiceId + ".pdf" : invoiceId + ".jpg";
        String originalName = file.getOriginalFilename();
        try {
            Path target = Path.of(uploadDir).resolve(filename).normalize();
            Files.createDirectories(target.getParent());
            if ("pdf".equals(ext)) {
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                ImageProcessor.compressAndSave(file, target);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save photo", e);
        }
        invoiceRepository.insert(invoiceId, period, periodStart, filename, originalName);
        return invoiceId;
    }

    public void markSubmitted(String id) {
        invoiceRepository.updateStatus(id, InvoiceStatus.SUBMITTED.value());
    }

    /** Perbarui periode invoice agar sesuai tanggal belanja yang dipilih. */
    public void updatePeriod(String id, LocalDate date) {
        invoiceRepository.updatePeriod(id, PeriodSheetName.forDate(date), PeriodSheetName.periodStart(date));
    }

    public void setAnalyzing(String id) {
        invoiceRepository.updateStatus(id, InvoiceStatus.ANALYZING.value());
    }

    /**
     * Menghapus invoice + file foto jika tidak lagi dipakai expense mana pun.
     * Mengembalikan true jika dihapus, false jika masih dipakai.
     */
    public boolean deleteIfUnused(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (invoiceRepository.countExpensesUsing(id) > 0) {
            return false;
        }
        String photoPath = invoiceRepository.getPhotoPath(id);
        if (photoPath != null) {
            try {
                Files.deleteIfExists(Path.of(uploadDir).resolve(photoPath).normalize());
            } catch (IOException e) {
                LOGGER.warn("failed to delete invoice file {}: {}", photoPath, e.getMessage());
            }
        }
        invoiceRepository.delete(id);
        return true;
    }

    public InvoiceData requireInvoice(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Invoice id is required");
        }
        InvoiceData invoice = invoiceRepository.findById(id);
        if (invoice == null) {
            throw new ValidationException("Invoice not found");
        }
        return invoice;
    }

    public String typeOf(InvoiceData invoice) {
        String path = invoice == null || invoice.photoPath() == null ? "" : invoice.photoPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".pdf") ? "pdf" : "image";
    }

    private InvoiceResponse toResponse(InvoiceData invoice) {
        return new InvoiceResponse(invoice.id(), invoice.createdAt(), invoice.status(), typeOf(invoice),
                invoice.originalName());
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
