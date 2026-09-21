package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class EmailParserServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    private EmailParserService parser() {
        return new EmailParserService(budgetRepository, new ObjectMapper(), "", "gemini-3.5-flash-lite", 60, 2, 0);
    }

    /** Subclass yang menimpa callGemini agar tidak memanggil HTTP sungguhan. */
    private static class TestService extends EmailParserService {
        String[] responses;
        int call = 0;

        TestService(String... responses) {
            super(null, new ObjectMapper(), "test-key", "gemini-3.5-flash-lite", 60, 2, 0);
            this.responses = responses;
        }

        @Override
        protected String callGemini(String text) throws Exception {
            String response = responses[Math.min(call, responses.length - 1)];
            call++;
            if (response.startsWith("THROW:")) {
                throw new RetryableException(response.substring(6));
            }
            return response;
        }
    }

    private static final String AI_SUCCESS = """
            {"isExpense":true,"merchant":"TOTAL BUAH SEGAR","amount":57500,
             "dateTime":"2026-09-20 19:56:00","suggestedBudget":"Belanja"}
            """;

    private static final String BCA_CREDIT_CARD = """
            <html><body><table>
            <tr><td>Nomor Kartu</td><td>:</td><td>188980XXXX9170</td></tr>
            <tr><td>Merchant / ATM</td><td>:&nbsp;</td><td><span>A369 AZKO LP CINERE</span></td></tr>
            <tr><td>Pada Tanggal</td><td>:</td><td><span>20-09-2026 12:51:36 WIB</span></td></tr>
            <tr><td>Sejumlah</td><td>:</td><td><span>Rp1.035.600,00</span></td></tr>
            </table></body></html>
            """;

    private static final String BCA_INTERNET_JOURNAL = """
            <html><body><table>
            <tr><td>Status</td><td>: </td><td>Berhasil</td></tr>
            <tr><td>Tanggal Transaksi</td><td>: </td><td>20 Sep 2026 07:28:28</td></tr>
            <tr><td>Jenis Transaksi</td><td>: </td><td>Pembayaran QRIS</td></tr>
            <tr><td>Pembayaran Ke</td><td>: </td><td>KETOPRAK BANG JACKK</td></tr>
            <tr><td>Total Bayar</td><td>: </td><td>IDR 25,000.00</td></tr>
            </table></body></html>
            """;

    private static final String DBANK_QRIS = """
            <html><body><h2>Detail Nominal</h2><table>
            <tr><td>Merchant Tujuan</td><td align="right"><b> TOTAL BUAH SEGAR </b></td></tr>
            <tr><td>Tanggal Pembayaran</td><td align="right"><b> 20 September 2026 19:56 </b></td></tr>
            <tr><td>Nominal</td><td>Rp.57.500,00</td></tr>
            <tr><td>Jumlah</td><td>Rp.57.500,00</td></tr>
            </table></body></html>
            """;

    @Test
    void parsesBcaCreditCard() {
        ParsedTransaction result = parser().parse("KartuKreditBCA@klikbca.com", BCA_CREDIT_CARD);

        assertEquals("A369 AZKO LP CINERE", result.merchant());
        assertEquals(1_035_600L, result.amount());
        assertEquals(LocalDateTime.of(2026, 9, 20, 12, 51, 36), result.transactionAt());
        assertEquals("REGEX", result.parseMethod());
    }

    @Test
    void parsesBcaInternetJournal() {
        ParsedTransaction result = parser().parse("bca@bca.co.id", BCA_INTERNET_JOURNAL);

        assertEquals("KETOPRAK BANG JACKK", result.merchant());
        assertEquals(25_000L, result.amount());
        assertEquals(LocalDateTime.of(2026, 9, 20, 7, 28, 28), result.transactionAt());
    }

    @Test
    void parsesDbankQris() {
        ParsedTransaction result = parser().parse("dbank.app@danamon.co.id", DBANK_QRIS);

        assertEquals("TOTAL BUAH SEGAR", result.merchant());
        assertEquals(57_500L, result.amount());
        assertEquals(LocalDateTime.of(2026, 9, 20, 19, 56), result.transactionAt());
    }

    @Test
    void rejectsUnknownFormatWithoutAi() {
        assertThrows(ValidationException.class, () -> parser().parse("someone@example.com", "<p>hello</p>"));
    }

    @Test
    void parsesIndonesianAmount() {
        assertEquals(1_035_600L, EmailParserService.parseAmount("Rp1.035.600,00"));
        assertEquals(57_500L, EmailParserService.parseAmount("Rp.57.500,00"));
    }

    @Test
    void parsesUsAmount() {
        assertEquals(25_000L, EmailParserService.parseAmount("IDR 25,000.00"));
    }

    @Test
    void parsesAmountWithoutSeparators() {
        assertEquals(50_000L, EmailParserService.parseAmount("50000"));
    }

    @Test
    void returnsNullForMissingAmount() {
        assertNull(EmailParserService.parseAmount("tidak ada angka"));
    }

    @Test
    void parsesDateTimes() {
        assertEquals(LocalDateTime.of(2026, 9, 20, 12, 51, 36),
                EmailParserService.parseDateTime("20-09-2026 12:51:36 WIB"));
        assertEquals(LocalDateTime.of(2026, 9, 20, 7, 28, 28),
                EmailParserService.parseDateTime("20 Sep 2026 07:28:28"));
        assertEquals(LocalDateTime.of(2026, 9, 20, 19, 56),
                EmailParserService.parseDateTime("20 September 2026 19:56"));
        assertNull(EmailParserService.parseDateTime("bukan tanggal"));
    }

    @Test
    void aiRetriesTransientThenSucceeds() {
        TestService service = new TestService("THROW:Gemini returned HTTP 503", AI_SUCCESS);

        ParsedTransaction result = service.parse("unknown@example.com", "<p>transfer</p>");

        assertEquals("TOTAL BUAH SEGAR", result.merchant());
        assertEquals(57_500L, result.amount());
        assertEquals("AI", result.parseMethod());
        assertEquals(2, service.call);
    }

    @Test
    void aiNotExpenseThrowsNotExpenseException() {
        TestService service = new TestService("{\"isExpense\":false}");

        assertThrows(NotExpenseException.class, () -> service.parse("unknown@example.com", "<p>promo</p>"));
    }

    @Test
    void aiExhaustsRetriesThrowsValidationException() {
        TestService service = new TestService("THROW:Gemini returned HTTP 503");

        assertThrows(ValidationException.class, () -> service.parse("unknown@example.com", "<p>x</p>"));
        assertEquals(2, service.call);
    }
}
