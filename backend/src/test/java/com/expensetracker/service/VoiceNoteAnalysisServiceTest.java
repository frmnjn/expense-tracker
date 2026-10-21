package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.VoiceNoteData;
import com.expensetracker.data.VoiceNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceNoteAnalysisServiceTest {

    @Mock
    private VoiceNoteRepository voiceNoteRepository;
    @Mock
    private BudgetRepository budgetRepository;

    private static class TestService extends VoiceNoteAnalysisService {
        String[] responses = new String[0];
        int call = 0;

        TestService(VoiceNoteRepository repo, BudgetRepository budgetRepo) {
            super(repo, budgetRepo, new ObjectMapper(), "key", "gemini-test", 600L, 3, 0L, 120000L);
        }

        @Override
        protected String callGemini(byte[] bytes, String mime, String prompt) throws Exception {
            String r = responses[call];
            call++;
            if ("503".equals(r)) {
                throw new RetryableException("Gemini returned HTTP 503");
            }
            if ("400".equals(r)) {
                throw new IllegalStateException("Gemini returned HTTP 400");
            }
            return r;
        }
    }

    private TestService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new TestService(voiceNoteRepository, budgetRepository);
        ReflectionTestUtils.setField(service, "uploadDir", "/tmp");
        Files.write(Path.of("/tmp", "voice-test.webm"), new byte[]{1, 2, 3});
    }

    private void stubNote(String id) {
        when(voiceNoteRepository.findById(id))
                .thenReturn(new VoiceNoteData(id, "2026-OCT-NOV", "2026-10-21 14:30", "voice-test.webm",
                        "audio/webm", 4000, "2026-10-21T14:31:00", "ANALYZING", 0, 0, null));
    }

    @Test
    void analyze_validExpense_shouldMarkToReview() {
        String id = "vn-1";
        stubNote(id);
        service.responses = new String[]{
                "{\"transcript\":\"belanja sayur lima puluh ribu\",\"isExpense\":true,"
                        + "\"storeName\":\"Tukang Sayur Komplek\","
                        + "\"dateTime\":\"2026-10-21 15:00\",\"items\":["
                        + "{\"name\":\"Sayur\",\"amount\":20000,\"suggestedBudget\":\"Daily\",\"suggestedCategory\":\"\"},"
                        + "{\"name\":\"Telur\",\"amount\":30000,\"suggestedBudget\":\"Daily\",\"suggestedCategory\":\"\"}]}"};

        service.analyzeForTest(id);

        verify(voiceNoteRepository).setProvider(id, "GEMINI");
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(voiceNoteRepository).updateAnalysis(eq(id), eq(VoiceNoteStatus.TO_REVIEW.value()),
                captor.capture(), eq("belanja sayur lima puluh ribu"));
        assertTrue(captor.getValue().contains("\"storeName\":\"Tukang Sayur Komplek\""));
        assertTrue(captor.getValue().contains("\"items\""));
        assertTrue(captor.getValue().contains("Sayur"));
    }

    @Test
    void analyze_notExpense_shouldMarkError() {
        String id = "vn-2";
        stubNote(id);
        service.responses = new String[]{
                "{\"transcript\":\"halo apa kabar\",\"isExpense\":false,\"dateTime\":\"\",\"items\":[]}"};

        service.analyzeForTest(id);

        verify(voiceNoteRepository, never()).updateAnalysis(anyString(), anyString(), anyString(), anyString());
        verify(voiceNoteRepository).markNotExpense(id, "Rekaman bukan catatan pengeluaran");
        verify(voiceNoteRepository, never()).updateError(eq(id), anyString());
    }

    @Test
    void analyze_isExpenseButNoItems_shouldMarkNotExpense() {
        String id = "vn-empty";
        stubNote(id);
        service.responses = new String[]{
                "{\"transcript\":\"suara burung\",\"isExpense\":true,\"storeName\":\"\","
                        + "\"dateTime\":\"\",\"items\":[]}"};

        service.analyzeForTest(id);

        verify(voiceNoteRepository).markNotExpense(id, "Rekaman bukan catatan pengeluaran");
        verify(voiceNoteRepository, never()).updateAnalysis(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void analyze_transientError_shouldRetryThenSucceed() {
        String id = "vn-3";
        stubNote(id);
        service.responses = new String[]{
                "503",
                "{\"transcript\":\"kopi sepuluh ribu\",\"isExpense\":true,"
                        + "\"dateTime\":\"\",\"items\":["
                        + "{\"name\":\"Kopi\",\"amount\":10000,\"suggestedBudget\":\"Daily\",\"suggestedCategory\":\"\"}]}"};

        service.analyzeForTest(id);

        verify(voiceNoteRepository).incrementRetry(id);
        verify(voiceNoteRepository).updateAnalysis(eq(id), eq(VoiceNoteStatus.TO_REVIEW.value()),
                anyString(), eq("kopi sepuluh ribu"));
    }

    @Test
    void analyze_permanentError_shouldNotRetry() {
        String id = "vn-4";
        stubNote(id);
        service.responses = new String[]{"400"};

        service.analyzeForTest(id);

        verify(voiceNoteRepository, never()).incrementRetry(id);
        verify(voiceNoteRepository).updateError(eq(id), anyString());
    }

    @Test
    void analyze_missingFile_shouldMarkError() {
        String id = "vn-5";
        when(voiceNoteRepository.findById(id))
                .thenReturn(new VoiceNoteData(id, "2026-OCT-NOV", "2026-10-21 14:30", "missing.webm",
                        "audio/webm", 4000, "2026-10-21T14:31:00", "ANALYZING", 0, 0, null));

        service.analyzeForTest(id);

        verify(voiceNoteRepository).updateError(id, "Voice note file not found");
    }

    @Test
    void analyze_legacySingleExpenseShape_shouldStillSucceed() {
        String id = "vn-legacy";
        stubNote(id);
        service.responses = new String[]{
                "{\"transcript\":\"kopi sepuluh ribu\",\"isExpense\":true,\"name\":\"Kopi\",\"amount\":10000,"
                        + "\"dateTime\":\"\",\"budget\":\"Daily\",\"category\":\"\",\"description\":\"\"}"};

        service.analyzeForTest(id);

        verify(voiceNoteRepository).updateAnalysis(eq(id), eq(VoiceNoteStatus.TO_REVIEW.value()),
                anyString(), eq("kopi sepuluh ribu"));
    }

    @Test
    void cleanDate_futureDate_shouldBecomeEmpty() {
        // analyze membersihkan tanggal yang tidak masuk akal (masa depan).
        String id = "vn-6";
        stubNote(id);
        service.responses = new String[]{
                "{\"transcript\":\"belanja\",\"isExpense\":true,"
                        + "\"dateTime\":\"2999-01-01 10:00\",\"items\":["
                        + "{\"name\":\"Belanja\",\"amount\":10000,\"suggestedBudget\":\"Daily\",\"suggestedCategory\":\"\"}]}"};

        service.analyzeForTest(id);

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(voiceNoteRepository).updateAnalysis(eq(id), eq(VoiceNoteStatus.TO_REVIEW.value()),
                captor.capture(), eq("belanja"));
        assertEquals(-1, captor.getValue().indexOf("2999"));
    }
}
