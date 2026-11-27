package com.expensetracker.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class EmailImportRepository {

    private static final String COLUMNS =
            "id, message_id, sender, subject, received_at, transaction_at, merchant, amount, description, "
                    + "suggested_budget, suggested_category, parse_method, status, error_message, expense_id, created_at";

    private final JdbcTemplate jdbcTemplate;

    public EmailImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String messageId, String sender, String subject,
                       LocalDateTime receivedAt, LocalDateTime transactionAt, String merchant,
                       Long amount, String description, String suggestedBudget, String suggestedCategory,
                       String parseMethod, String status, String errorMessage,
                       String body, String bodyFormat) {
        jdbcTemplate.update(
                "INSERT INTO email_imports (id, message_id, sender, subject, received_at, transaction_at, "
                        + "merchant, amount, description, suggested_budget, suggested_category, parse_method, status, error_message, "
                        + "expense_id, created_at, body, body_format) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)",
                id, messageId, truncate(sender, 320), truncate(subject, 512),
                Timestamp.valueOf(receivedAt), transactionAt,
                truncate(merchant, 255), amount, truncate(description, 10000),
                truncate(suggestedBudget, 255), truncate(suggestedCategory, 255), parseMethod, status,
                truncate(errorMessage, 500),
                Timestamp.valueOf(LocalDateTime.now()), body, bodyFormat);
    }

    public EmailBodyData findBodyById(String id) {
        List<EmailBodyData> rows = jdbcTemplate.query(
                "SELECT body, body_format FROM email_imports WHERE id = ?",
                (rs, rowNum) -> new EmailBodyData(rs.getString("body"), rs.getString("body_format")),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateBody(String id, String body, String format) {
        jdbcTemplate.update(
                "UPDATE email_imports SET body = ?, body_format = ? WHERE id = ?",
                body, format, id);
    }

    public boolean existsByMessageId(String messageId) {
        List<Integer> rows = jdbcTemplate.query(
                "SELECT 1 FROM email_imports WHERE message_id = ? LIMIT 1",
                (rs, rowNum) -> 1,
                messageId);
        return !rows.isEmpty();
    }

    public List<EmailImportData> findByStatus(String status) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM email_imports WHERE status = ? ORDER BY received_at DESC, id",
                this::mapRow, status);
    }

    public List<EmailImportData> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM email_imports "
                        + "ORDER BY (status = 'PENDING_REVIEW') DESC, received_at DESC, id",
                this::mapRow);
    }

    public EmailImportData findById(String id) {
        List<EmailImportData> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM email_imports WHERE id = ?",
                this::mapRow, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Kunci baris agar dua request import tidak memproses email yang sama bersamaan. */
    public EmailImportData findByIdForUpdate(String id) {
        List<EmailImportData> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM email_imports WHERE id = ? FOR UPDATE",
                this::mapRow, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Tandai IMPORTED; merchant/amount/tanggal ikut nilai yang diimport (bisa diedit user). */
    public void markImported(String id, String expenseId, String merchant, Long amount,
                             LocalDateTime transactionAt) {
        jdbcTemplate.update(
                "UPDATE email_imports SET status = 'IMPORTED', expense_id = ?, error_message = NULL, "
                        + "merchant = ?, amount = ?, transaction_at = ? WHERE id = ?",
                expenseId, merchant, amount,
                transactionAt, id);
    }

    public void markDiscarded(String id) {
        markDiscarded(id, null);
    }

    public void markDiscarded(String id, String reason) {
        jdbcTemplate.update(
                "UPDATE email_imports SET status = 'DISCARDED', error_message = ? WHERE id = ?",
                reason, id);
    }

    /** Isi ulang hasil parse (dipakai saat retry baris FAILED). */
    public void updateParsed(String id, LocalDateTime transactionAt, String merchant, Long amount,
                             String description, String suggestedBudget, String suggestedCategory,
                             String parseMethod, String status, String errorMessage) {
        String safe = errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 500));
        jdbcTemplate.update(
                "UPDATE email_imports SET transaction_at = ?, merchant = ?, amount = ?, description = ?, "
                        + "suggested_budget = ?, suggested_category = ?, parse_method = ?, status = ?, "
                        + "error_message = ? WHERE id = ?",
                transactionAt,
                truncate(merchant, 255), amount, truncate(description, 10000),
                truncate(suggestedBudget, 255), truncate(suggestedCategory, 255), parseMethod, status, safe, id);
    }

    public void markFailed(String id, String message) {
        String safe = message == null || message.isBlank() ? "Gagal memproses email" : message;
        jdbcTemplate.update(
                "UPDATE email_imports SET status = 'FAILED', error_message = ? WHERE id = ?",
                safe.substring(0, Math.min(safe.length(), 500)), id);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private EmailImportData mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new EmailImportData(
                rs.getString("id"),
                rs.getString("message_id"),
                rs.getString("sender"),
                rs.getString("subject"),
                toLocalDateTimeString(rs.getTimestamp("received_at")),
                toLocalDateTimeString(rs.getObject("transaction_at", LocalDateTime.class)),
                rs.getString("merchant"),
                rs.getObject("amount") == null ? null : rs.getLong("amount"),
                rs.getString("description"),
                rs.getString("suggested_budget"),
                rs.getString("suggested_category"),
                rs.getString("parse_method"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getString("expense_id"),
                toLocalDateTimeString(rs.getTimestamp("created_at")));
    }

    private static String toLocalDateTimeString(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }

    private static String toLocalDateTimeString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString();
    }
}
