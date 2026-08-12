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
        jdbcTemplate.update(
                "INSERT INTO invoices (id, period, period_start, photo_path, deleted) VALUES (?, ?, ?, ?, FALSE)",
                id, period, periodStart, photoPath);
    }

    public List<InvoiceData> findByPeriod(String period) {
        return jdbcTemplate.query(
                "SELECT id, period, photo_path, created_at FROM invoices WHERE period = ? AND deleted = FALSE ORDER BY created_at DESC, id",
                this::mapRow, period);
    }

    public InvoiceData findById(String id) {
        List<InvoiceData> rows = jdbcTemplate.query(
                "SELECT id, period, photo_path, created_at FROM invoices WHERE id = ? AND deleted = FALSE",
                this::mapRow, id);
        return rows.isEmpty() ? null : rows.get(0);
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
                createdAt == null ? null : createdAt.toLocalDateTime().toString());
    }
}
