package com.expensetracker.service;

import com.expensetracker.data.EmailImportRepository;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailInboxServiceTest {

    @Mock
    private EmailImportRepository emailImportRepository;
    @Mock
    private EmailParserService emailParserService;
    @Mock
    private MerchantDiscardRule merchantDiscardRule;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private InvoiceAnalysisService invoiceAnalysisService;

    private EmailInboxService service;

    @BeforeEach
    void setUp() {
        service = new EmailInboxService(emailImportRepository, emailParserService, merchantDiscardRule,
                invoiceService, invoiceAnalysisService, true, "imap.gmail.com", 993, "user", "pass", "INBOX",
                "klikbca.com,bca.co.id", "superindo.co.id", 3, 50);
    }

    private static MimeMessage messageWithAttachment(String from, boolean withAttachment) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setSubject("E-Receipt");
        message.setHeader("Message-ID", "<msg-" + from + "@test>");
        message.setSentDate(new Date());
        MimeMultipart mp = new MimeMultipart();
        MimeBodyPart body = new MimeBodyPart();
        body.setText("<p>Terima kasih</p>", "UTF-8", "html");
        mp.addBodyPart(body);
        if (withAttachment) {
            MimeBodyPart att = new MimeBodyPart();
            att.setDataHandler(new DataHandler(
                    new ByteArrayDataSource("%PDF-1.4 dummy".getBytes(StandardCharsets.UTF_8), "application/pdf")));
            att.setFileName("E-Receipt_260929195901.pdf");
            att.setDisposition(jakarta.mail.Part.ATTACHMENT);
            mp.addBodyPart(att);
        }
        message.setContent(mp, "multipart/mixed");
        message.saveChanges();
        return message;
    }

    @Test
    void superindoWithAttachment_shouldCreateScanInvoice() throws Exception {
        when(invoiceService.createInvoiceForAi(anyString(), any(), any(byte[].class), anyString()))
                .thenReturn("inv-1");

        boolean stored = service.storeMessage(messageWithAttachment("e-receipt@superindo.co.id", true));

        verify(invoiceService).createInvoiceForAi(anyString(), any(), any(byte[].class),
                eq("E-Receipt_260929195901.pdf"));
        verify(invoiceAnalysisService).trigger("inv-1");
        verify(emailImportRepository).insert(anyString(), anyString(), anyString(), anyString(), any(),
                isNull(), isNull(), isNull(), anyString(), isNull(), isNull(), eq("SCAN"),
                eq(EmailImportStatus.DISCARDED.value()), anyString());
        verify(emailParserService, never()).parse(anyString(), anyString(), anyString());
        org.junit.jupiter.api.Assertions.assertTrue(stored);
    }

    @Test
    void superindoWithoutAttachment_shouldFallBackToNormalFlow() throws Exception {
        when(emailParserService.parse(anyString(), anyString(), anyString()))
                .thenReturn(new ParsedTransaction("SUPERINDO", 50000L, LocalDateTime.now(), null, null, "AI"));
        when(merchantDiscardRule.shouldDiscard("SUPERINDO")).thenReturn(false);

        service.storeMessage(messageWithAttachment("e-receipt@superindo.co.id", false));

        verify(invoiceService, never()).createInvoiceForAi(anyString(), any(), any(byte[].class), anyString());
        verify(emailParserService).parse(anyString(), anyString(), anyString());
    }

    @Test
    void bankSenderWithAttachment_shouldNotScan() throws Exception {
        when(emailParserService.parse(anyString(), anyString(), anyString()))
                .thenReturn(new ParsedTransaction("BELANJA", 50000L, LocalDateTime.now(), null, null, "AI"));
        when(merchantDiscardRule.shouldDiscard("BELANJA")).thenReturn(false);

        service.storeMessage(messageWithAttachment("notif@klikbca.com", true));

        verify(invoiceService, never()).createInvoiceForAi(anyString(), any(), any(byte[].class), anyString());
        verify(emailParserService).parse(anyString(), anyString(), anyString());
    }

    @Test
    void notAllowedSender_shouldBeSkipped() throws Exception {
        Message message = messageWithAttachment("random@example.com", true);

        boolean stored = service.storeMessage(message);

        org.junit.jupiter.api.Assertions.assertFalse(stored);
        verify(invoiceService, never()).createInvoiceForAi(anyString(), any(), any(byte[].class), anyString());
        verify(emailImportRepository, never()).insert(anyString(), anyString(), anyString(), anyString(), any(),
                any(), any(), any(), anyString(), any(), any(), anyString(), anyString(), anyString());
    }
}
