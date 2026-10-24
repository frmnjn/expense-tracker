package com.expensetracker.service;

import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.VoiceNoteAnalysis;
import com.expensetracker.data.VoiceNoteData;
import com.expensetracker.data.VoiceNoteRepository;
import com.expensetracker.model.ExpenseResponse;
import com.expensetracker.model.VoiceAnalysisResponse;
import com.expensetracker.model.VoiceNoteDetailResponse;
import com.expensetracker.model.VoiceNoteResponse;
import com.expensetracker.model.VoiceNotesResponse;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class VoiceNoteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceNoteService.class);

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> DELETE_ALLOWED_STATUSES = Set.of(
            VoiceNoteStatus.TO_REVIEW.value(),
            VoiceNoteStatus.ERROR.value(),
            VoiceNoteStatus.NOT_EXPENSE.value());

    private static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "audio/webm", "webm",
            "audio/ogg", "ogg",
            "audio/mp4", "mp4",
            "audio/mpeg", "mp3",
            "audio/wav", "wav",
            "audio/x-wav", "wav",
            "audio/wave", "wav",
            "audio/aac", "aac");

    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            "webm", "audio/webm",
            "ogg", "audio/ogg",
            "mp4", "audio/mp4",
            "m4a", "audio/mp4",
            "mp3", "audio/mpeg",
            "wav", "audio/wav",
            "aac", "audio/aac");

    private final VoiceNoteRepository voiceNoteRepository;
    private final ExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper;
    private final PeriodService periodService;

    @Value("${upload.dir:/app/uploads}")
    private String uploadDir;

    public VoiceNoteService(VoiceNoteRepository voiceNoteRepository, ExpenseRepository expenseRepository,
                            ObjectMapper objectMapper, PeriodService periodService) {
        this.voiceNoteRepository = voiceNoteRepository;
        this.expenseRepository = expenseRepository;
        this.objectMapper = objectMapper;
        this.periodService = periodService;
    }

    public String createForAi(MultipartFile file, String date, Integer durationMs) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Voice note file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ValidationException("Voice note must be at most 10MB");
        }
        LocalDateTime referenceDate;
        try {
            referenceDate = PeriodSheetName.parseLenient(date);
        } catch (Exception e) {
            throw new ValidationException("Date must be in yyyy-MM-dd HH:mm format");
        }

        String normalizedMime = normalizeMime(file.getContentType());
        String extension = extensionFor(normalizedMime, file.getOriginalFilename());
        if (extension == null) {
            throw new ValidationException("Only audio files are allowed");
        }
        String mimeType = MIME_TO_EXTENSION.containsKey(normalizedMime)
                ? normalizedMime
                : EXTENSION_TO_MIME.get(extension);

        String id = UUID.randomUUID().toString();
        String filename = id + "." + extension;
        try {
            Path target = Path.of(uploadDir).resolve(filename).normalize();
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save voice note", e);
        }

        Integer safeDuration = durationMs == null || durationMs <= 0 ? null : durationMs;
        LocalDate dateValue = referenceDate.toLocalDate();
        voiceNoteRepository.insert(id, periodService.forDate(dateValue), periodService.periodStart(dateValue),
                referenceDate, filename, mimeType, safeDuration);
        return id;
    }

    public VoiceNotesResponse listByPeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new ValidationException("Period is required");
        }
        List<VoiceNoteResponse> notes = voiceNoteRepository.findByPeriod(period).stream()
                .map(this::toResponse)
                .toList();
        return new VoiceNotesResponse(notes);
    }

    public VoiceNoteDetailResponse getDetail(String id) {
        VoiceNoteData note = requireVoiceNote(id);
        VoiceNoteAnalysis analysis = voiceNoteRepository.findAnalysis(id);
        VoiceAnalysisResponse parsed = null;
        if (analysis != null && analysis.analysisJson() != null && !analysis.analysisJson().isBlank()) {
            try {
                parsed = objectMapper.readValue(analysis.analysisJson(), VoiceAnalysisResponse.class);
            } catch (Exception e) {
                LOGGER.warn("failed to parse voice analysis for note {}: {}", id, e.getMessage());
            }
        }
        String status = analysis == null ? note.status() : analysis.status();
        return new VoiceNoteDetailResponse(
                note.id(),
                status,
                analysis == null ? null : analysis.errorMessage(),
                parsed,
                analysis == null ? note.retryCount() : analysis.retryCount(),
                analysis == null ? note.retryMax() : analysis.retryMax(),
                analysis == null ? note.aiProvider() : analysis.aiProvider(),
                note.durationMs(),
                VoiceNoteStatus.SUBMITTED.value().equals(status)
                        ? expenseRepository.findByVoiceNoteId(id).stream().map(this::toExpenseResponse).toList()
                        : null);
    }

    private ExpenseResponse toExpenseResponse(ExpenseData expense) {
        return new ExpenseResponse(
                expense.id(),
                expense.dateTime(),
                expense.name(),
                expense.budgetName(),
                expense.categoryName(),
                expense.amount(),
                expense.description(),
                expense.hasPhoto(),
                expense.photoType(),
                expense.photoName(),
                expense.voiceNoteId());
    }

    public VoiceNoteData requireVoiceNote(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Voice note id is required");
        }
        VoiceNoteData note = voiceNoteRepository.findById(id);
        if (note == null) {
            throw new NotFoundException("Voice note not found");
        }
        return note;
    }

    public Path audioPath(VoiceNoteData note) {
        return Path.of(uploadDir).resolve(note.filePath()).normalize();
    }

    public void prepareRetry(String id) {
        VoiceNoteData note = requireVoiceNote(id);
        if (VoiceNoteStatus.SUBMITTED.value().equals(note.status())) {
            throw new ConflictException("Rekaman yang sudah menjadi expense tidak dapat dianalisis ulang");
        }
        if (VoiceNoteStatus.NOT_EXPENSE.value().equals(note.status())) {
            throw new ConflictException("Rekaman ini bukan catatan pengeluaran dan tidak dapat dianalisis ulang");
        }
        voiceNoteRepository.updateStatus(id, VoiceNoteStatus.ANALYZING.value());
    }

    public void delete(String id) {
        VoiceNoteData note = requireVoiceNote(id);
        boolean submitted = VoiceNoteStatus.SUBMITTED.value().equals(note.status());
        if (!submitted && !DELETE_ALLOWED_STATUSES.contains(note.status())) {
            throw new ConflictException("Rekaman yang sedang diproses tidak dapat dihapus");
        }
        if (voiceNoteRepository.countActiveExpensesUsing(id) > 0) {
            throw new ConflictException("Rekaman dipakai oleh expense dan tidak dapat dihapus");
        }
        deleteNoteAndFile(note);
    }

    /**
     * Dipanggil setelah expense dihapus: hapus rekaman hanya jika tidak ada lagi
     * expense aktif yang memakainya.
     */
    public void deleteIfUnused(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        VoiceNoteData note = voiceNoteRepository.findById(id);
        if (note == null) {
            return;
        }
        if (voiceNoteRepository.countActiveExpensesUsing(id) > 0) {
            return;
        }
        deleteNoteAndFile(note);
    }

    private void deleteNoteAndFile(VoiceNoteData note) {
        expenseRepository.clearVoiceNoteReferences(note.id());
        try {
            Files.deleteIfExists(audioPath(note));
        } catch (IOException e) {
            LOGGER.warn("failed to delete voice note file {}: {}", note.filePath(), e.getMessage());
        }
        voiceNoteRepository.delete(note.id());
    }

    /** Tandai rekaman selesai dan samakan periodenya dengan tanggal expense. */
    public void markSubmitted(String id, LocalDate date) {
        voiceNoteRepository.updatePeriod(id, periodService.forDate(date), periodService.periodStart(date));
        voiceNoteRepository.updateStatus(id, VoiceNoteStatus.SUBMITTED.value());
    }

    private VoiceNoteResponse toResponse(VoiceNoteData note) {
        return new VoiceNoteResponse(note.id(), note.createdAt(), note.status(), note.durationMs(),
                note.retryCount(), note.retryMax(), note.aiProvider());
    }

    private static String normalizeMime(String raw) {
        if (raw == null) {
            return "";
        }
        int semicolon = raw.indexOf(';');
        String value = semicolon >= 0 ? raw.substring(0, semicolon) : raw;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String extensionFor(String normalizedMime, String originalName) {
        if (MIME_TO_EXTENSION.containsKey(normalizedMime)) {
            return MIME_TO_EXTENSION.get(normalizedMime);
        }
        if (!normalizedMime.isEmpty() && !normalizedMime.startsWith("audio/")) {
            return null;
        }
        String extension = extensionOf(originalName);
        return EXTENSION_TO_MIME.containsKey(extension) ? extension : null;
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
