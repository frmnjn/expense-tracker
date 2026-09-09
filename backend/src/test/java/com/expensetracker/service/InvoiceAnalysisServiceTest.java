package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import com.expensetracker.data.InvoiceData;
import com.expensetracker.data.InvoiceRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceAnalysisServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private BudgetRepository budgetRepository;

    /**
     * Subclass yang menimpa callGemini agar tidak memanggil HTTP sungguhan.
     * Array yang di-set lewat ReflectionTestUtils dipakai sebagai antrean respons.
     */
    private static class TestService extends InvoiceAnalysisService {
        String[] responses = new String[0];
        int call = 0;

        TestService(InvoiceRepository repo, BudgetRepository budgetRepo) {
            super(repo, budgetRepo, new ObjectMapper(), "key", "gemini-test", 600L, 50, 0L);
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
            if ("io".equals(r)) {
                throw new IOException("connection reset");
            }
            return r;
        }
    }

    private TestService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new TestService(invoiceRepository, budgetRepository);
        ReflectionTestUtils.setField(service, "uploadDir", "/tmp");
        Files.write(Path.of("/tmp", "inv.jpg"), new byte[]{1, 2, 3});
    }

    private void stubInvoice(String id) {
        when(invoiceRepository.findById(id))
                .thenReturn(new InvoiceData(id, "2026-AGU-SEP", "inv.jpg", "2026-09-05T13:31:00", "ANALYZING", null));
        when(invoiceRepository.getPhotoPath(id)).thenReturn("inv.jpg");
    }

    @Test
    void analyze_succeedsAfterOne503_shouldRetryAndMarkToReview() throws Exception {
        String id = "inv-1";
        stubInvoice(id);
        service.responses = new String[]{
                "503",
                "{\"storeName\":\"HokBen\",\"total\":1,\"dateTime\":\"2026-09-05 13:31:00\","
                        + "\"items\":[{\"name\":\"A\",\"amount\":1000,\"suggestedBudget\":\"Makan\"}]}"};

        service.analyzeForTest(id);

        verify(invoiceRepository).initRetry(eq(id), eq(50));
        verify(invoiceRepository).incrementRetry(id);
        verify(invoiceRepository).resetRetry(id);
        verify(invoiceRepository).updateAnalysis(eq(id), eq(InvoiceStatus.TO_REVIEW.value()), anyString());
    }

    @Test
    void analyze_exhaustsMaxAttempts_shouldNotUpdateAnalysis() throws Exception {
        String id = "inv-2";
        stubInvoice(id);
        // maxAttemps pada TestService = 50
        service.responses = new String[50];
        for (int i = 0; i < 50; i++) {
            service.responses[i] = "503";
        }

        service.analyzeForTest(id);

        verify(invoiceRepository).initRetry(eq(id), eq(50));
        verify(invoiceRepository, atLeastOnce()).incrementRetry(id);
        verify(invoiceRepository, never()).updateAnalysis(anyString(), anyString(), anyString());
        verify(invoiceRepository).updateError(eq(id), anyString());
    }

    @Test
    void analyze_permanent400_shouldNotRetry() throws Exception {
        String id = "inv-3";
        stubInvoice(id);
        service.responses = new String[]{"400"};

        service.analyzeForTest(id);

        // Gagal permanen -> tidak boleh increment retry
        verify(invoiceRepository, never()).incrementRetry(id);
        verify(invoiceRepository).updateError(eq(id), anyString());
    }
}
