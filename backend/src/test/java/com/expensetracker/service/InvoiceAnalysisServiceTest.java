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
        Double rate = null;

        TestService(InvoiceRepository repo, BudgetRepository budgetRepo) {
            super(repo, budgetRepo, new ObjectMapper(), "key", "gemini-test", 600L, 50, 0L, "", 8000L);
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

        @Override
        Double fetchRate(String base, String date) {
            return rate;
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

    @Test
    void analyze_convertsNonIdrToIdrUsingRate() throws Exception {
        String id = "inv-4";
        stubInvoice(id);
        String base = "USD";
        service.responses = new String[]{
                "{\"storeName\":\"Boutique\",\"total\":10,\"dateTime\":\"2026-09-05 13:31:00\","
                        + "\"currency\":\"" + base + "\",\"originalTotal\":10,"
                        + "\"items\":[{\"name\":\"Tshirt\",\"amount\":10,\"suggestedBudget\":\"Makan\"}]}"};
        service.rate = 17730.42601401;

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        service.analyzeForTest(id);

        verify(invoiceRepository).updateAnalysis(eq(id), eq(InvoiceStatus.TO_REVIEW.value()), captor.capture());
        com.expensetracker.model.AiAnalysisResponse out =
                new tools.jackson.databind.ObjectMapper().readValue(captor.getValue(), com.expensetracker.model.AiAnalysisResponse.class);
        assertEquals("USD", out.currency());
        assertEquals(177304L, out.total());
        assertEquals(177304L, out.items().get(0).amount());
        assertEquals(Double.valueOf(17730.42601401), out.exchangeRate());
        assertEquals("2026-09-05", out.exchangeDate());
        assertEquals(Double.valueOf(10), out.originalTotal());
    }

    @Test
    void analyze_keepsIdrAmountsWhenCurrencyIsIdr() throws Exception {
        String id = "inv-5";
        stubInvoice(id);
        service.responses = new String[]{
                "{\"storeName\":\"Warteg\",\"total\":10000,\"dateTime\":\"2026-09-05 13:31:00\","
                        + "\"currency\":\"IDR\",\"originalTotal\":null,"
                        + "\"items\":[{\"name\":\"Nasi\",\"amount\":10000,\"suggestedBudget\":\"Makan\"}]}"};

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        service.analyzeForTest(id);

        verify(invoiceRepository).updateAnalysis(eq(id), eq(InvoiceStatus.TO_REVIEW.value()), captor.capture());
        com.expensetracker.model.AiAnalysisResponse out =
                new tools.jackson.databind.ObjectMapper().readValue(captor.getValue(), com.expensetracker.model.AiAnalysisResponse.class);
        assertEquals(10000L, out.total());
        assertEquals(10000L, out.items().get(0).amount());
        assertEquals(null, out.exchangeRate());
    }
}
