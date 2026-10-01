package com.expensetracker.service;

import com.expensetracker.data.EmailImportData;
import com.expensetracker.data.EmailImportRepository;
import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.model.ExpenseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Business logic antrian transaksi hasil import email: daftar, konversi ke
 * expense (via {@link ExpenseService} agar saldo & notifikasi tetap konsisten),
 * dan buang. Hanya baris PENDING_REVIEW yang bisa diproses.
 */
@Service
public class EmailImportService {

    private static final NumberFormat RUPIAH = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));

    private final EmailImportRepository emailImportRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;
    private final EmailInboxService emailInboxService;
    private final EmailParserService emailParserService;
    private final MerchantDiscardRule merchantDiscardRule;

    public EmailImportService(EmailImportRepository emailImportRepository,
                              ExpenseRepository expenseRepository,
                              ExpenseService expenseService,
                              EmailInboxService emailInboxService,
                              EmailParserService emailParserService,
                              MerchantDiscardRule merchantDiscardRule) {
        this.emailImportRepository = emailImportRepository;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
        this.emailInboxService = emailInboxService;
        this.emailParserService = emailParserService;
        this.merchantDiscardRule = merchantDiscardRule;
    }

    public List<EmailImportData> list(String status) {
        if (status != null && "ALL".equalsIgnoreCase(status.trim())) {
            return emailImportRepository.findAll();
        }
        return emailImportRepository.findByStatus(normalizeStatus(status));
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return EmailImportStatus.PENDING_REVIEW.value();
        }
        try {
            return EmailImportStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).value();
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Status tidak dikenal: " + status);
        }
    }

    @Transactional
    public void importToExpense(String id, ExpenseRequest request, boolean force) {
        EmailImportData data = requirePending(id);
        if (!force) {
            checkDuplicate(request);
        }
        String expenseId = expenseService.createExpense(request);
        emailImportRepository.markImported(data.id(), expenseId, request.name(), request.amount(),
                parseTransactionAt(request.dateTime()));
    }

    /** Tanggal yang diimport (sudah divalidasi createExpense); null bila tak terparse. */
    private static LocalDateTime parseTransactionAt(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            return null;
        }
        try {
            return PeriodSheetName.parseLenient(dateTime);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Deteksi duplikat: expense aktif dengan nominal persis sama di periode yang
     * sama. Periode diambil dari dateTime yang dikirim; bila formatnya tidak valid,
     * pengecekan dilewati (createExpense yang akan melaporkan error format).
     */
    private void checkDuplicate(ExpenseRequest request) {
        if (request.amount() == null || request.amount() <= 0) {
            return;
        }
        String period = periodOf(request.dateTime());
        if (period == null) {
            return;
        }
        List<ExpenseData> matches = expenseRepository.findByPeriodAndAmount(period, request.amount());
        if (!matches.isEmpty()) {
            String names = matches.stream()
                    .map(ExpenseData::name)
                    .distinct()
                    .collect(Collectors.joining(", "));
            throw new DuplicateExpenseException("Kemungkinan duplikat: sudah ada pengeluaran "
                    + RUPIAH.format(request.amount()) + " di periode " + period + " (" + names + ").");
        }
    }

    private static String periodOf(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            return null;
        }
        try {
            LocalDateTime parsed = PeriodSheetName.parseLenient(dateTime);
            return PeriodSheetName.forDate(parsed.toLocalDate());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Transactional
    public void discard(String id) {
        EmailImportData data = require(id);
        if (!EmailImportStatus.PENDING_REVIEW.value().equals(data.status())
                && !EmailImportStatus.FAILED.value().equals(data.status())) {
            throw new ValidationException("Transaksi ini sudah diproses");
        }
        emailImportRepository.markDiscarded(id);
    }

    /**
     * Proses ulang baris FAILED: ambil ulang email dari IMAP berdasarkan
     * Message-ID lalu parse lagi. Sukses -> PENDING_REVIEW (atau DISCARDED bila
     * kena rule merchant); bukan transaksi -> DISCARDED; masih gagal -> FAILED.
     */
    @Transactional
    public void retry(String id) {
        EmailImportData data = require(id);
        if (!EmailImportStatus.FAILED.value().equals(data.status())) {
            throw new ValidationException("Hanya transaksi gagal yang bisa dicoba lagi");
        }
        FetchedEmail fetched = emailInboxService.fetchByMessageId(data.messageId());
        if (fetched == null) {
            emailImportRepository.markFailed(id, "Email tidak ditemukan di inbox");
            throw new ValidationException("Email tidak ditemukan di inbox");
        }
        try {
            ParsedTransaction parsed = emailParserService.parse(fetched.sender(), fetched.subject(), fetched.body());
            String description = descriptionWithNote(fetched.subject(), parsed);
            if (merchantDiscardRule.shouldDiscard(parsed.merchant())) {
                emailImportRepository.updateParsed(id, parsed.transactionAt(), parsed.merchant(),
                        parsed.amount(), description, parsed.suggestedBudget(), parsed.suggestedCategory(),
                        parsed.parseMethod(), EmailImportStatus.DISCARDED.value(),
                        "Auto-discard: merchant " + parsed.merchant());
            } else {
                emailImportRepository.updateParsed(id, parsed.transactionAt(), parsed.merchant(),
                        parsed.amount(), description, parsed.suggestedBudget(), parsed.suggestedCategory(),
                        parsed.parseMethod(), EmailImportStatus.PENDING_REVIEW.value(), null);
            }
        } catch (NotExpenseException e) {
            emailImportRepository.updateParsed(id, null, null, null, null, null, null, "AI",
                    EmailImportStatus.DISCARDED.value(), e.getMessage());
        } catch (AiParseException e) {
            emailImportRepository.updateParsed(id, null, null, null, null, null, null, "AI",
                    EmailImportStatus.FAILED.value(), e.getMessage());
        } catch (ValidationException e) {
            emailImportRepository.updateParsed(id, null, null, null, null, null, null, "REGEX",
                    EmailImportStatus.FAILED.value(), e.getMessage());
        }
    }

    /** Subjek + catatan konversi (mis. "… · USD 0,45 @ 15.888 = Rp7.150") bila ada. */
    private static String descriptionWithNote(String subject, ParsedTransaction parsed) {
        String note = parsed.conversionNote();
        return note == null ? subject : subject + " · " + note;
    }

    private EmailImportData requirePending(String id) {
        EmailImportData data = require(id);
        if (!EmailImportStatus.PENDING_REVIEW.value().equals(data.status())) {
            throw new ValidationException("Transaksi ini sudah diproses");
        }
        return data;
    }

    private EmailImportData require(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Id transaksi email diperlukan");
        }
        EmailImportData data = emailImportRepository.findById(id);
        if (data == null) {
            throw new ValidationException("Transaksi email tidak ditemukan");
        }
        return data;
    }
}
