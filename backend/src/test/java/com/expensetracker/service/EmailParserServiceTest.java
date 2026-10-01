package com.expensetracker.service;

import com.expensetracker.data.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class EmailParserServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    private EmailParserService parser() {
        return new EmailParserService(budgetRepository, new ObjectMapper(), new ExchangeRateService(new ObjectMapper(), "", 8000),
                "", "gemini-3.5-flash-lite", 60, 2, 0,
                "", "deepseek-flash", "https://api.deepseek.com", 120000);
    }

    /** Subclass yang menimpa callGemini/callDeepSeekText agar tidak memanggil HTTP sungguhan. */
    private static class TestService extends EmailParserService {
        String[] responses;
        int call = 0;
        String deepseekResponse = null;

        TestService(String... responses) {
            super(null, new ObjectMapper(), new ExchangeRateService(new ObjectMapper(), "", 8000),
                    "test-key", "gemini-3.5-flash-lite", 60, 2, 0,
                    "dsk-key", "deepseek-flash", "https://api.deepseek.com", 120000);
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

        @Override
        protected String callDeepSeekText(String text) throws Exception {
            if (deepseekResponse == null) {
                throw new RetryableException("DeepSeek returned HTTP 503");
            }
            return deepseekResponse;
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
    void parsesBcaCreditCardMultilineFromNewSender() {
        String html = """
                <html><body><table>
                <tr><td>Nomor Kartu</td>
                <td>:</td>
                <td>455633XXXX1552</td>
                </tr>
                <tr><td>Merchant / ATM</td>
                <td>:&nbsp;</td>
                <td><span>SHOPEE.CO.ID</span></td>
                </tr>
                <tr><td>Pada Tanggal</td>
                <td>:</td>
                <td><span>25-09-2026 13:09:49 WIB</span></td></tr>
                <tr><td>Sejumlah</td>
                <td>:</td>
                <td><span>Rp94.074,00</span></td></tr>
                </table></body></html>
                """;
        ParsedTransaction result = parser().parse(
                "kartukreditbca@bca.co.id", "Credit Card Transaction Notification", html);

        assertEquals("SHOPEE.CO.ID", result.merchant());
        assertEquals(94_074L, result.amount());
        assertEquals(LocalDateTime.of(2026, 9, 25, 13, 9, 49), result.transactionAt());
        assertEquals("REGEX", result.parseMethod());
    }

    @Test
    void parsesBcaJournalTransferVaMultiline() {
        String html = """
                <html><body><table>
                <tr><td>Status</td>
                <td>:</td>
                <td>Berhasil</td></tr>
                <tr><td>Tanggal Transaksi</td>
                <td>:</td>
                <td>25 Sep 2026 12:19:32</td></tr>
                <tr><td>Jenis Transaksi</td>
                <td>:</td>
                <td>Transfer ke BCA Virtual Account</td></tr>
                <tr><td>Nama Perusahaan/Produk</td>
                <td>:</td>
                <td>PT FINNET INDONESIA / BY.U</td></tr>
                <tr><td>Total Tagihan</td>
                <td>:</td>
                <td>IDR 100,000.00</td></tr>
                <tr><td>Total Bayar</td>
                <td>:</td>
                <td>IDR 100,000.00</td></tr>
                </table></body></html>
                """;
        ParsedTransaction result = parser().parse(
                "bca@bca.co.id", "Internet Transaction Journal", html);

        assertEquals("PT FINNET INDONESIA / BY.U", result.merchant());
        assertEquals(100_000L, result.amount());
        assertEquals(LocalDateTime.of(2026, 9, 25, 12, 19, 32), result.transactionAt());
        assertEquals("REGEX", result.parseMethod());
    }

    @Test
    void parsesBcaJournalBifastViaNominal() {
        String html = """
                <html><body><table>
                <tr><td>Status</td>
                <td>:</td>
                <td>Berhasil</td></tr>
                <tr><td>Tanggal Transaksi</td>
                <td>:</td>
                <td>25 Sep 2026 12:16:44</td></tr>
                <tr><td>Nama Penerima</td>
                <td>:</td>
                <td>FIRMAN BUDI SAFRIZAL</td></tr>
                <tr><td>Nominal</td>
                <td>:</td>
                <td>IDR 500,000.00</td></tr>
                </table></body></html>
                """;
        ParsedTransaction result = parser().parse(
                "bca@bca.co.id", "Internet Transaction Journal", html);

        assertEquals("FIRMAN BUDI SAFRIZAL", result.merchant());
        assertEquals(500_000L, result.amount());
        assertEquals("REGEX", result.parseMethod());
    }

    private static final String JAGO_PAYMENT = """
            <html><body><div>Hello Rene,</div>
            <div>Transaction Summary</div>
            <div>From</div>
            <div>106335389859</div>
            <div>To</div>
            <div>KETOPRAK BANG JACKK</div>
            <div>9360000801838616977</div>
            <div>Amount</div>
            <div>Rp 45.000</div>
            <div>Transaction Date</div>
            <div>26 September 2026, 07:34 WIB</div>
            <div>Transaction Status</div>
            <div>Successful</div>
            <div>Tip Amount</div>
            <div>Rp 0</div>
            </body></html>
            """;

    @Test
    void parsesJagoPayment() {
        ParsedTransaction result = parser().parse(
                "noreply@jago.com", "You have made a payment to KETOPRAK BANG JACKK?", JAGO_PAYMENT);

        assertEquals("KETOPRAK BANG JACKK", result.merchant());
        assertEquals(45_000L, result.amount());
        assertEquals(LocalDateTime.of(2026, 9, 26, 7, 34), result.transactionAt());
        assertEquals("REGEX", result.parseMethod());
    }

    @Test
    void jagoPocketTransferIsNotExpense() {
        assertThrows(NotExpenseException.class, () -> parser().parse(
                "noreply@jago.com", "Money moved between your Pockets", JAGO_PAYMENT));
    }

    @Test
    void parsesDateTimeWithComma() {
        assertEquals(LocalDateTime.of(2026, 9, 26, 7, 34),
                EmailParserService.parseDateTime("26 September 2026, 07:34 WIB"));
    }

    @Test
    void rejectsUnknownFormatWithoutAi() {
        assertThrows(ValidationException.class, () -> parser().parse("someone@example.com", "<p>hello</p>"));
    }

    /** ExchangeRateService palsu: 1 unit mata uang = Rp15.888. */
    private static class StubRate extends ExchangeRateService {
        StubRate() {
            super(new ObjectMapper(), "", 8000);
        }

        @Override
        public Double rateToIdr(String base, String date) {
            return 15_888.0;
        }
    }

    private static class RateTestService extends EmailParserService {
        RateTestService() {
            super(null, new ObjectMapper(), new StubRate(), "", "gemini-3.5-flash-lite", 60, 2, 0,
                    "", "deepseek-flash", "https://api.deepseek.com", 120000);
        }
    }

    @Test
    void parsesMoneyWithCurrency() {
        EmailParserService.Money idr = EmailParserService.parseMoney("Rp240.390,00");
        assertEquals("IDR", idr.currency());
        assertEquals(240_390.0, idr.amount().doubleValue());

        EmailParserService.Money usd = EmailParserService.parseMoney("USD 0,45");
        assertEquals("USD", usd.currency());
        assertEquals(0.45, usd.amount().doubleValue());

        EmailParserService.Money apple = EmailParserService.parseMoney("IDR 59.000");
        assertEquals("IDR", apple.currency());
        assertEquals(59_000.0, apple.amount().doubleValue());
    }

    @Test
    void parsesBcaCreditCardUsdAndConvertsToIdr() {
        String html = """
                <html><body><table>
                <tr><td>Merchant / ATM</td>
                <td>:&nbsp;</td>
                <td><span>LINODE . AKAMAI</span></td>
                </tr>
                <tr><td>Pada Tanggal</td>
                <td>:</td>
                <td><span>01-10-2026 12:45:28 WIB</span></td></tr>
                <tr><td>Sejumlah</td>
                <td>:</td>
                <td><span>USD 0,45</span></td></tr>
                </table></body></html>
                """;
        ParsedTransaction result = new RateTestService().parse(
                "kartukreditbca@bca.co.id", "Credit Card Transaction Notification", html);

        assertEquals("LINODE . AKAMAI", result.merchant());
        assertEquals(7_150L, result.amount()); // 0.45 * 15888 = 7149.6 -> 7150
        assertEquals("USD", result.currency());
        assertEquals("2026-10-01", result.exchangeDate());
        assertNotNull(result.conversionNote());
        assertEquals("REGEX", result.parseMethod());
    }

    @Test
    void parsesBcaCreditCardUsdWithoutRateFails() {
        // parser() memakai ExchangeRateService tanpa fx-api -> rate null -> FAILED.
        String html = """
                <html><body><table>
                <tr><td>Merchant / ATM</td>
                <td>:&nbsp;</td>
                <td><span>LINODE . AKAMAI</span></td>
                </tr>
                <tr><td>Sejumlah</td>
                <td>:</td>
                <td><span>USD 0,45</span></td></tr>
                </table></body></html>
                """;
        assertThrows(ValidationException.class, () ->
                parser().parse("kartukreditbca@bca.co.id", "Credit Card Transaction Notification", html));
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

    @Test
    void aiFallsBackToDeepSeekAfterGeminiExhausted() {
        TestService service = new TestService("THROW:Gemini returned HTTP 503");
        service.deepseekResponse = AI_SUCCESS;

        ParsedTransaction result = service.parse("unknown@example.com", "<p>transfer</p>");

        assertEquals("TOTAL BUAH SEGAR", result.merchant());
        assertEquals(57_500L, result.amount());
        assertEquals("AI", result.parseMethod());
        assertEquals(2, service.call);
    }
}
