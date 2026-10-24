package com.expensetracker.service;

import com.expensetracker.data.ExpenseData;
import com.expensetracker.data.ExpenseRepository;
import com.expensetracker.data.PaydayOverrideRepository;
import com.expensetracker.data.VoiceNoteAnalysis;
import com.expensetracker.data.VoiceNoteData;
import com.expensetracker.data.VoiceNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceNoteServiceTest {

    @Mock
    private VoiceNoteRepository voiceNoteRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private PaydayOverrideRepository paydayOverrideRepository;

    private VoiceNoteService voiceNoteService;

    @BeforeEach
    void setUp() {
        voiceNoteService = new VoiceNoteService(voiceNoteRepository, expenseRepository, new ObjectMapper(),
                new PeriodService(paydayOverrideRepository));
        ReflectionTestUtils.setField(voiceNoteService, "uploadDir", "/tmp");
    }

    @Test
    void getDetail_submitted_shouldReturnLinkedExpenses() {
        when(voiceNoteRepository.findById("vn-sub")).thenReturn(note("SUBMITTED"));
        when(voiceNoteRepository.findAnalysis("vn-sub")).thenReturn(
                new VoiceNoteAnalysis("vn-sub", "SUBMITTED", "{}", null, 0, 0, "GEMINI"));
        when(expenseRepository.findByVoiceNoteId("vn-sub")).thenReturn(List.of(
                new ExpenseData("e1", "2026-SEP-OCT", "2026-10-03 10:00", "Belanja sayur", "Daily", 50000L,
                        "tukang sayur komplek", false, false, null, null, null, null, "vn-sub")));

        var detail = voiceNoteService.getDetail("vn-sub");

        assertEquals(1, detail.expenses().size());
        assertEquals("Belanja sayur", detail.expenses().get(0).name());
        assertEquals(50000L, detail.expenses().get(0).amount());
        assertEquals("vn-sub", detail.expenses().get(0).voiceNoteId());
    }

    @Test
    void getDetail_toReview_shouldNotQueryExpenses() {
        when(voiceNoteRepository.findById("vn-1")).thenReturn(note("TO_REVIEW"));
        when(voiceNoteRepository.findAnalysis("vn-1")).thenReturn(
                new VoiceNoteAnalysis("vn-1", "TO_REVIEW", "{}", null, 0, 0, "GEMINI"));

        var detail = voiceNoteService.getDetail("vn-1");

        assertEquals(null, detail.expenses());
        verify(expenseRepository, never()).findByVoiceNoteId("vn-1");
    }

    @Test
    void createForAi_webmFile_shouldStoreAndInsertAnalyzingNote() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "voice.webm", "audio/webm;codecs=opus", new byte[]{1, 2, 3});

        String id = voiceNoteService.createForAi(file, "2026-10-21 14:30", 4200);

        assertEquals(true, Files.exists(Path.of("/tmp", id + ".webm")));
        verify(voiceNoteRepository).insert(eq(id), eq("2026-SEP-OCT"), eq(LocalDate.of(2026, 9, 25)),
                eq(LocalDateTime.of(2026, 10, 21, 14, 30)), eq(id + ".webm"), eq("audio/webm"), eq(4200));
        try {
            Files.deleteIfExists(Path.of("/tmp", id + ".webm"));
        } catch (Exception ignored) {
        }
    }

    @Test
    void createForAi_nonAudioFile_shouldReject() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1});

        ValidationException ex = assertThrows(ValidationException.class,
                () -> voiceNoteService.createForAi(file, "2026-10-21 14:30", 1000));
        assertEquals("Only audio files are allowed", ex.getMessage());
        verify(voiceNoteRepository, never()).insert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void delete_reviewNoteUsedByExpense_shouldReject() {
        when(voiceNoteRepository.findById("vn-1"))
                .thenReturn(note("TO_REVIEW"));
        when(voiceNoteRepository.countActiveExpensesUsing("vn-1")).thenReturn(1);

        ConflictException ex = assertThrows(ConflictException.class, () -> voiceNoteService.delete("vn-1"));
        assertEquals("Rekaman dipakai oleh expense dan tidak dapat dihapus", ex.getMessage());
        verify(voiceNoteRepository, never()).delete("vn-1");
    }

    @Test
    void delete_submittedNoteWithActiveExpense_shouldReject() {
        when(voiceNoteRepository.findById("vn-1")).thenReturn(note("SUBMITTED"));
        when(voiceNoteRepository.countActiveExpensesUsing("vn-1")).thenReturn(1);

        assertThrows(ConflictException.class, () -> voiceNoteService.delete("vn-1"));
        verify(voiceNoteRepository, never()).delete("vn-1");
    }

    @Test
    void delete_submittedNoteWithoutActiveExpense_shouldDeleteAndClearReferences() {
        when(voiceNoteRepository.findById("vn-1")).thenReturn(note("SUBMITTED"));
        when(voiceNoteRepository.countActiveExpensesUsing("vn-1")).thenReturn(0);

        voiceNoteService.delete("vn-1");

        verify(expenseRepository).clearVoiceNoteReferences("vn-1");
        verify(voiceNoteRepository).delete("vn-1");
    }

    @Test
    void deleteIfUnused_noActiveExpense_shouldDeleteAndClearReferences() {
        when(voiceNoteRepository.findById("vn-1")).thenReturn(note("SUBMITTED"));
        when(voiceNoteRepository.countActiveExpensesUsing("vn-1")).thenReturn(0);

        voiceNoteService.deleteIfUnused("vn-1");

        verify(expenseRepository).clearVoiceNoteReferences("vn-1");
        verify(voiceNoteRepository).delete("vn-1");
    }

    @Test
    void deleteIfUnused_stillUsedByActiveExpense_shouldKeepNote() {
        when(voiceNoteRepository.findById("vn-1")).thenReturn(note("SUBMITTED"));
        when(voiceNoteRepository.countActiveExpensesUsing("vn-1")).thenReturn(1);

        voiceNoteService.deleteIfUnused("vn-1");

        verify(voiceNoteRepository, never()).delete("vn-1");
        verify(expenseRepository, never()).clearVoiceNoteReferences("vn-1");
    }

    @Test
    void prepareRetry_notExpenseNote_shouldReject() {
        when(voiceNoteRepository.findById("vn-1")).thenReturn(note("NOT_EXPENSE"));

        ConflictException ex = assertThrows(ConflictException.class, () -> voiceNoteService.prepareRetry("vn-1"));
        assertEquals("Rekaman ini bukan catatan pengeluaran dan tidak dapat dianalisis ulang", ex.getMessage());
        verify(voiceNoteRepository, never()).updateStatus("vn-1", "ANALYZING");
    }

    @Test
    void delete_notExpenseNote_shouldBeAllowed() {
        when(voiceNoteRepository.findById("vn-1")).thenReturn(note("NOT_EXPENSE"));
        when(voiceNoteRepository.countActiveExpensesUsing("vn-1")).thenReturn(0);

        voiceNoteService.delete("vn-1");

        verify(voiceNoteRepository).delete("vn-1");
    }

    @Test
    void prepareRetry_submittedNote_shouldReject() {
        when(voiceNoteRepository.findById("vn-1")).thenReturn(note("SUBMITTED"));

        assertThrows(ConflictException.class, () -> voiceNoteService.prepareRetry("vn-1"));
        verify(voiceNoteRepository, never()).updateStatus("vn-1", "ANALYZING");
    }

    @Test
    void markSubmitted_shouldUpdatePeriodAndStatus() {
        voiceNoteService.markSubmitted("vn-1", LocalDate.of(2026, 10, 3));

        verify(voiceNoteRepository).updatePeriod("vn-1", "2026-SEP-OCT", LocalDate.of(2026, 9, 25));
        verify(voiceNoteRepository).updateStatus("vn-1", "SUBMITTED");
    }

    @Test
    void createForAi_tooLargeFile_shouldReject() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "voice.webm", "audio/webm", new byte[10 * 1024 * 1024 + 1]);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> voiceNoteService.createForAi(file, "2026-10-21 14:30", 1000));
        assertTrue(ex.getMessage().contains("10MB"));
    }

    private static VoiceNoteData note(String status) {
        return new VoiceNoteData("vn-1", "2026-SEP-OCT", "2026-10-03 10:00", "vn-1.webm", "audio/webm",
                5000, "2026-10-03T10:01:00", status, 0, 0, "GEMINI");
    }
}
