package com.expensetracker.service;

import com.expensetracker.data.InvoiceData;
import com.expensetracker.data.InvoiceRepository;
import com.expensetracker.model.InvoiceResponse;
import com.expensetracker.model.InvoicesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final InvoiceRepository invoiceRepository;

    @Value("${upload.dir:/app/uploads}")
    private String uploadDir;

    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public InvoicesResponse listByDate(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            throw new ValidationException("Date is required");
        }
        LocalDateTime parsed;
        try {
            parsed = LocalDateTime.parse(dateTime, PeriodSheetName.FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Date must be in yyyy-MM-dd HH:mm format");
        }
        String period = PeriodSheetName.forDate(parsed.toLocalDate());
        List<InvoiceResponse> list = invoiceRepository.findByPeriod(period).stream()
                .map(inv -> new InvoiceResponse(inv.id(), inv.createdAt()))
                .toList();
        return new InvoicesResponse(list);
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
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Photo is required");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException("Only jpg and png are allowed");
        }
        String invoiceId = UUID.randomUUID().toString();
        String filename = invoiceId + ".jpg";
        try {
            Path target = Path.of(uploadDir).resolve(filename).normalize();
            Files.createDirectories(target.getParent());
            ImageProcessor.compressAndSave(file, target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save photo", e);
        }
        invoiceRepository.insert(invoiceId, period, periodStart, filename);
        return invoiceId;
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

    public InvoiceData requireInvoice(String id) {        if (id == null || id.isBlank()) {
            throw new ValidationException("Invoice id is required");
        }
        InvoiceData invoice = invoiceRepository.findById(id);
        if (invoice == null) {
            throw new ValidationException("Invoice not found");
        }
        return invoice;
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
