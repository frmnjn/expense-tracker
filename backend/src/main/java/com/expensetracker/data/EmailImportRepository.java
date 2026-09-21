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
                    + "suggested_budget, parse_method, status, error_message, expense_id, created_at";

    private final JdbcTemplate jdbcTemplate;

    public EmailImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String messageId, String sender, String subject,
                       LocalDateTime receivedAt, LocalDateTime transactionAt, String merchant,
                       Long amount, String description, String suggestedBudget, String parseMethod,
                       String status, String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO email_imports (id, message_id, sender, subject, received_at, transaction_at, "
                        + "merchant, amount, description, suggested_budget, parse_method, status, error_message, "
                        + "expense_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)",
                id, messageId, sender, subject,
                Timestamp.valueOf(receivedAt), transactionAt == null ? null : Timestamp.valueOf(transactionAt),
                merchant, amount, description, suggestedBudget, parseMethod, status, errorMessage,
                Timestamp.valueOf(LocalDateTime.now()));
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
                "SELECT " + COLUMNS + " FROM email_imports WHERE status = ? ORDER BY created_at DESC, id",
                this::mapRow, status);
    }

    public List<EmailImportData> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM email_imports ORDER BY created_at DESC, id",
                this::mapRow);
    }

    public EmailImportData findById(String id) {
        List<EmailImportData> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM email_imports WHERE id = ?",
                this::mapRow, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void markImported(String id, String expenseId) {
        jdbcTemplate.update(
                "UPDATE email_imports SET status = 'IMPORTED', expense_id = ?, error_message = NULL WHERE id = ?",
                expenseId, id);
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
                             String suggestedBudget, String parseMethod, String status, String errorMessage) {
        String safe = errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 500));
        jdbcTemplate.update(
                "UPDATE email_imports SET transaction_at = ?, merchant = ?, amount = ?, suggested_budget = ?, "
                        + "parse_method = ?, status = ?, error_message = ? WHERE id = ?",
                transactionAt == null ? null : Timestamp.valueOf(transactionAt),
                merchant, amount, suggestedBudget, parseMethod, status, safe, id);
    }

    public void markFailed(String id, String message) {
        String safe = message == null || message.isBlank() ? "Gagal memproses email" : message;
        jdbcTemplate.update(
                "UPDATE email_imports SET status = 'FAILED', error_message = ? WHERE id = ?",
                safe.substring(0, Math.min(safe.length(), 500)), id);
    }

    private EmailImportData mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new EmailImportData(
                rs.getString("id"),
                rs.getString("message_id"),
                rs.getString("sender"),
                rs.getString("subject"),
                toLocalDateTimeString(rs.getTimestamp("received_at")),
                toLocalDateTimeString(rs.getTimestamp("transaction_at")),
                rs.getString("merchant"),
                rs.getObject("amount") == null ? null : rs.getLong("amount"),
                rs.getString("description"),
                rs.getString("suggested_budget"),
                rs.getString("parse_method"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getString("expense_id"),
                toLocalDateTimeString(rs.getTimestamp("created_at")));
    }

    private static String toLocalDateTimeString(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }
}
