package com.expensetracker.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Repository
public class InvoiceRepository {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String period, LocalDate periodStart, String photoPath) {
        insert(id, period, periodStart, photoPath, null);
    }

    public void insert(String id, String period, LocalDate periodStart, String photoPath, String originalName) {
        jdbcTemplate.update(
                "INSERT INTO invoices (id, period, period_start, photo_path, original_name, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, FALSE)",
                id, period, periodStart, photoPath, originalName);
    }

    public List<InvoiceData> findByPeriod(String period) {
        return jdbcTemplate.query(
                "SELECT id, period, photo_path, created_at, status, original_name FROM invoices "
                        + "WHERE period = ? AND deleted = FALSE ORDER BY created_at DESC, id",
                this::mapRow, period);
    }

    public List<InvoiceData> findByPeriodScanOnly(String period) {
        return jdbcTemplate.query(
                "SELECT id, period, photo_path, created_at, status, original_name FROM invoices "
                        + "WHERE period = ? AND deleted = FALSE AND scan_flow = TRUE "
                        + "ORDER BY created_at DESC, id",
                this::mapRow, period);
    }

    /** Semua invoice alur scan (tanpa filter periode), terbaru dulu. */
    public List<InvoiceData> findAllScan() {
        return jdbcTemplate.query(
                "SELECT id, period, photo_path, created_at, status, original_name FROM invoices "
                        + "WHERE deleted = FALSE AND scan_flow = TRUE ORDER BY created_at DESC, id",
                this::mapRow);
    }

    public void setScanFlow(String id) {
        jdbcTemplate.update("UPDATE invoices SET scan_flow = TRUE WHERE id = ?", id);
    }

    public InvoiceData findById(String id) {
        List<InvoiceData> rows = jdbcTemplate.query(
                "SELECT id, period, photo_path, created_at, status, original_name FROM invoices "
                        + "WHERE id = ? AND deleted = FALSE",
                this::mapRow, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<String> findByStatus(String status) {
        return jdbcTemplate.query(
                "SELECT id FROM invoices WHERE status = ? AND deleted = FALSE",
                (rs, rowNum) -> rs.getString("id"),
                status);
    }

    public InvoiceAnalysis findAnalysis(String id) {
        List<InvoiceAnalysis> rows = jdbcTemplate.query(
                "SELECT id, status, analysis_json, error_message FROM invoices WHERE id = ? AND deleted = FALSE",
                (rs, rowNum) -> new InvoiceAnalysis(
                        rs.getString("id"),
                        rs.getString("status"),
                        rs.getString("analysis_json"),
                        rs.getString("error_message")),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateStatus(String id, String status) {
        jdbcTemplate.update("UPDATE invoices SET status = ? WHERE id = ?", status, id);
    }

    public void updatePeriod(String id, String period, LocalDate periodStart) {
        jdbcTemplate.update("UPDATE invoices SET period = ?, period_start = ? WHERE id = ?", period, periodStart, id);
    }

    public void updateAnalysis(String id, String status, String analysisJson) {
        jdbcTemplate.update(
                "UPDATE invoices SET status = ?, analysis_json = ?, error_message = NULL WHERE id = ?",
                status, analysisJson, id);
    }

    public void updateError(String id, String message) {
        String safe = message == null || message.isBlank() ? "Analysis failed" : message;
        jdbcTemplate.update(
                "UPDATE invoices SET status = 'ERROR', error_message = ? WHERE id = ?",
                safe.substring(0, Math.min(safe.length(), 255)), id);
    }

    /** Status terminal untuk file yang bukan struk/invoice — tidak bisa di-retry. */
    public void markNotInvoice(String id, String message) {
        String safe = message == null || message.isBlank() ? "Bukan struk invoice" : message;
        jdbcTemplate.update(
                "UPDATE invoices SET status = 'NOT_INVOICE', error_message = ? WHERE id = ?",
                safe.substring(0, Math.min(safe.length(), 255)), id);
    }

    public String getPhotoPath(String id) {
        List<String> paths = jdbcTemplate.query(
                "SELECT photo_path FROM invoices WHERE id = ?",
                (rs, rowNum) -> rs.getString("photo_path"),
                id);
        return paths.isEmpty() ? null : paths.get(0);
    }

    public int countExpensesUsing(String id) {
        List<Integer> counts = jdbcTemplate.query(
                "SELECT COUNT(*) FROM expenses WHERE invoice_id = ?",
                (rs, rowNum) -> rs.getInt(1),
                id);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", id);
    }

    private InvoiceData mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        return new InvoiceData(
                rs.getString("id"),
                rs.getString("period"),
                rs.getString("photo_path"),
                createdAt == null ? null : createdAt.toLocalDateTime().toString(),
                rs.getString("status"),
                rs.getString("original_name"));
    }
}
