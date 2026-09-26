package com.expensetracker.service;

import com.expensetracker.config.TraceIdFilter;
import com.expensetracker.data.EmailImportRepository;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.MessageIDTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Membaca email notifikasi bank dari inbox via IMAP secara berkala (polling),
 * lalu menyimpannya sebagai antrian review. Folder dibuka READ_ONLY sehingga
 * status baca email di Gmail tidak berubah; dedup memakai Message-ID di DB.
 */
@Service
public class EmailInboxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailInboxService.class);

    private final EmailImportRepository emailImportRepository;
    private final EmailParserService emailParserService;
    private final MerchantDiscardRule merchantDiscardRule;

    private final boolean enabled;
    private final String host;
    private final int port;
    private final String user;
    private final String appPassword;
    private final String folderName;
    private final List<String> allowedSenders;
    private final int lookbackDays;
    private final int maxPerPoll;

    public EmailInboxService(EmailImportRepository emailImportRepository,
                             EmailParserService emailParserService,
                             MerchantDiscardRule merchantDiscardRule,
                             @Value("${inbox.enabled:false}") boolean enabled,
                             @Value("${inbox.host:imap.gmail.com}") String host,
                             @Value("${inbox.port:993}") int port,
                             @Value("${inbox.user:}") String user,
                             @Value("${inbox.app-password:}") String appPassword,
                             @Value("${inbox.folder:INBOX}") String folderName,
                             @Value("${inbox.senders:}") String senders,
                             @Value("${inbox.lookback-days:3}") int lookbackDays,
                             @Value("${inbox.max-per-poll:50}") int maxPerPoll) {
        this.emailImportRepository = emailImportRepository;
        this.emailParserService = emailParserService;
        this.merchantDiscardRule = merchantDiscardRule;
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.user = user == null ? "" : user.trim();
        this.appPassword = appPassword == null ? "" : appPassword.trim();
        this.folderName = folderName == null || folderName.isBlank() ? "INBOX" : folderName;
        this.allowedSenders = Arrays.stream(senders == null ? new String[0] : senders.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .toList();
        this.lookbackDays = Math.max(1, lookbackDays);
        this.maxPerPoll = Math.max(1, maxPerPoll);
    }

    public boolean isEnabled() {
        return enabled && !user.isBlank() && !appPassword.isBlank();
    }

    @Scheduled(fixedDelayString = "${inbox.poll-ms:300000}", initialDelayString = "${inbox.poll-ms:300000}")
    public void scheduledPoll() {
        if (!isEnabled()) {
            return;
        }
        poll();
    }

    /** Ambil email baru dari inbox; mengembalikan jumlah email yang diproses. */
    public int poll() {
        if (!isEnabled()) {
            return 0;
        }
        String traceId = UUID.randomUUID().toString();
        MDC.put(TraceIdFilter.MDC_TRACE_ID, traceId);
        try {
            return fetchAndStore();
        } finally {
            MDC.remove(TraceIdFilter.MDC_TRACE_ID);
        }
    }

    /** Ambil ulang satu email dari IMAP berdasarkan Message-ID (untuk retry). */
    public FetchedEmail fetchByMessageId(String messageId) {
        if (!isEnabled() || messageId == null || messageId.isBlank()) {
            return null;
        }
        Store store = null;
        Folder folder = null;
        try {
            Session session = buildSession();
            store = session.getStore("imaps");
            store.connect(host, port, user, appPassword);
            folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);
            Message[] found = folder.search(new MessageIDTerm(messageId));
            if (found.length == 0) {
                return null;
            }
            Message message = found[0];
            return new FetchedEmail(senderOf(message), extractBody(message));
        } catch (Exception e) {
            LOGGER.warn("fetch by message-id failed: {}", e.getMessage());
            return null;
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private int fetchAndStore() {
        Store store = null;
        Folder folder = null;
        try {
            Session session = buildSession();
            store = session.getStore("imaps");
            store.connect(host, port, user, appPassword);
            folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);

            Date since = Date.from(LocalDateTime.now().minusDays(lookbackDays)
                    .atZone(ZoneId.systemDefault()).toInstant());
            Message[] messages = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, since));

            int processed = 0;
            for (int i = messages.length - 1; i >= 0 && processed < maxPerPoll; i--) {
                if (storeMessage(messages[i])) {
                    processed++;
                }
            }
            LOGGER.info("inbox poll finished: {} email(s) processed, folder={}", processed, folderName);
            return processed;
        } catch (Exception e) {
            LOGGER.error("inbox poll failed: {}", e.getMessage());
            return 0;
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private boolean storeMessage(Message message) {
        try {
            String sender = senderOf(message);
            if (!isAllowedSender(sender)) {
                return false;
            }
            String messageId = messageIdOf(message, sender);
            if (emailImportRepository.existsByMessageId(messageId)) {
                return false;
            }
            String subject = message.getSubject() == null ? "(tanpa subjek)" : message.getSubject();
            LocalDateTime receivedAt = receivedAtOf(message);
            String body = extractBody(message);

            String id = UUID.randomUUID().toString();
            try {
                ParsedTransaction parsed = emailParserService.parse(sender, subject, body);
                if (merchantDiscardRule.shouldDiscard(parsed.merchant())) {
                    emailImportRepository.insert(id, messageId, sender, subject, receivedAt,
                            parsed.transactionAt(), parsed.merchant(), parsed.amount(),
                            subject, parsed.suggestedBudget(), parsed.parseMethod(),
                            EmailImportStatus.DISCARDED.value(),
                            "Auto-discard: merchant " + parsed.merchant());
                    LOGGER.info("email auto-discarded: sender={} merchant={}", sender, parsed.merchant());
                } else {
                    emailImportRepository.insert(id, messageId, sender, subject, receivedAt,
                            parsed.transactionAt(), parsed.merchant(), parsed.amount(),
                            subject, parsed.suggestedBudget(), parsed.parseMethod(),
                            EmailImportStatus.PENDING_REVIEW.value(), null);
                    LOGGER.info("email imported for review: sender={} merchant={}", sender, parsed.merchant());
                }
            } catch (NotExpenseException e) {
                emailImportRepository.insert(id, messageId, sender, subject, receivedAt,
                        null, null, null, subject, null, "AI",
                        EmailImportStatus.DISCARDED.value(), e.getMessage());
                LOGGER.info("email skipped (not expense): sender={}", sender);
            } catch (ValidationException e) {
                emailImportRepository.insert(id, messageId, sender, subject, receivedAt,
                        null, null, null, subject, null, "REGEX",
                        EmailImportStatus.FAILED.value(), e.getMessage());
                LOGGER.warn("email parse failed: sender={} reason={}", sender, e.getMessage());
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("failed to process email: {}", e.getMessage());
            return false;
        }
    }

    private boolean isAllowedSender(String sender) {
        if (allowedSenders.isEmpty() || sender == null || sender.isBlank()) {
            return false;
        }
        String address = sender.toLowerCase(Locale.ROOT);
        String domain = address.contains("@") ? address.substring(address.lastIndexOf('@') + 1) : address;
        for (String allowed : allowedSenders) {
            if (allowed.contains("@")) {
                if (address.equals(allowed)) {
                    return true;
                }
            } else if (domain.equals(allowed) || domain.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private static String senderOf(Message message) throws Exception {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0) {
            return "";
        }
        if (from[0] instanceof InternetAddress address) {
            return address.getAddress() == null ? "" : address.getAddress();
        }
        return from[0].toString();
    }

    private static String messageIdOf(Message message, String sender) throws Exception {
        String[] header = message.getHeader("Message-ID");
        if (header != null && header.length > 0 && header[0] != null && !header[0].isBlank()) {
            return header[0].trim();
        }
        String key = sender + "|" + message.getSubject() + "|" + receivedAtOf(message);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static LocalDateTime receivedAtOf(Message message) throws Exception {
        Date received = message.getReceivedDate();
        Date date = received != null ? received : message.getSentDate();
        return date == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static String extractBody(Part part) throws Exception {
        String html = findPart(part, "text/html");
        if (html != null) {
            return html;
        }
        String plain = findPart(part, "text/plain");
        return plain == null ? "" : plain;
    }

    private static String findPart(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            Object content = part.getContent();
            return content == null ? null : content.toString();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = findPart(multipart.getBodyPart(i), mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Session buildSession() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(port));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "15000");
        return Session.getInstance(props);
    }

    private static void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (Exception ignored) {
                // abaikan
            }
        }
    }

    private static void closeQuietly(Store store) {
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (Exception ignored) {
                // abaikan
            }
        }
    }
}
