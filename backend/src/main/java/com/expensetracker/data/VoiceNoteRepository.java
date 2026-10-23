package com.expensetracker.data;

import com.expensetracker.service.PeriodSheetName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class VoiceNoteRepository {

    private static final String SELECT_COLS =
            "SELECT id, period, reference_date, file_path, mime_type, duration_ms, created_at, "
                    + "status, retry_count, retry_max, ai_provider FROM voice_notes ";

    private final JdbcTemplate jdbcTemplate;

    public VoiceNoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String period, LocalDate periodStart, LocalDateTime referenceDate,
                       String filePath, String mimeType, Integer durationMs) {
        jdbcTemplate.update(
                "INSERT INTO voice_notes (id, period, period_start, reference_date, file_path, mime_type, "
                        + "duration_ms, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'ANALYZING')",
                id, period, periodStart, referenceDate, filePath, mimeType, durationMs);
    }

    public List<VoiceNoteData> findByPeriod(String period) {
        return jdbcTemplate.query(
                SELECT_COLS + "WHERE period = ? ORDER BY created_at DESC, id",
                this::mapRow, period);
    }

    public VoiceNoteData findById(String id) {
        List<VoiceNoteData> rows = jdbcTemplate.query(
                SELECT_COLS + "WHERE id = ?",
                this::mapRow, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<String> findByStatus(String status) {
        return jdbcTemplate.query(
                "SELECT id FROM voice_notes WHERE status = ?",
                (rs, rowNum) -> rs.getString("id"),
                status);
    }

    public VoiceNoteAnalysis findAnalysis(String id) {
        List<VoiceNoteAnalysis> rows = jdbcTemplate.query(
                "SELECT id, status, analysis_json, error_message, retry_count, retry_max, ai_provider "
                        + "FROM voice_notes WHERE id = ?",
                (rs, rowNum) -> new VoiceNoteAnalysis(
                        rs.getString("id"),
                        rs.getString("status"),
                        rs.getString("analysis_json"),
                        rs.getString("error_message"),
                        rs.getInt("retry_count"),
                        rs.getInt("retry_max"),
                        rs.getString("ai_provider")),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void initRetry(String id, int retryMax) {
        jdbcTemplate.update("UPDATE voice_notes SET retry_count = 0, retry_max = ? WHERE id = ?", retryMax, id);
    }

    public void setProvider(String id, String provider) {
        jdbcTemplate.update("UPDATE voice_notes SET ai_provider = ? WHERE id = ?", provider, id);
    }

    public void incrementRetry(String id) {
        jdbcTemplate.update("UPDATE voice_notes SET retry_count = retry_count + 1 WHERE id = ?", id);
    }

    public void updateStatus(String id, String status) {
        jdbcTemplate.update("UPDATE voice_notes SET status = ? WHERE id = ?", status, id);
    }

    public void updatePeriod(String id, String period, LocalDate periodStart) {
        jdbcTemplate.update("UPDATE voice_notes SET period = ?, period_start = ? WHERE id = ?",
                period, periodStart, id);
    }

    public List<PeriodDateRow> findAllPeriodDates() {
        return jdbcTemplate.query(
                "SELECT id, DATE(reference_date) AS period_date FROM voice_notes",
                (rs, rowNum) -> new PeriodDateRow(rs.getString("id"), rs.getDate("period_date").toLocalDate()));
    }

    public void updateAnalysis(String id, String status, String analysisJson, String transcript) {
        jdbcTemplate.update(
                "UPDATE voice_notes SET status = ?, analysis_json = ?, transcript = ?, error_message = NULL "
                        + "WHERE id = ?",
                status, analysisJson, transcript, id);
    }

    public void updateError(String id, String message) {
        String safe = message == null || message.isBlank() ? "Analysis failed" : message;
        jdbcTemplate.update(
                "UPDATE voice_notes SET status = 'ERROR', error_message = ? WHERE id = ?",
                safe.substring(0, Math.min(safe.length(), 255)), id);
    }

    /** Status terminal untuk rekaman yang bukan catatan pengeluaran — tidak bisa di-retry. */
    public void markNotExpense(String id, String message) {
        String safe = message == null || message.isBlank() ? "Rekaman bukan catatan pengeluaran" : message;
        jdbcTemplate.update(
                "UPDATE voice_notes SET status = 'NOT_EXPENSE', error_message = ? WHERE id = ?",
                safe.substring(0, Math.min(safe.length(), 255)), id);
    }

    /** Jumlah expense yang masih aktif dan memakai rekaman ini. */
    public int countActiveExpensesUsing(String id) {
        List<Integer> counts = jdbcTemplate.query(
                "SELECT COUNT(*) FROM expenses WHERE voice_note_id = ? AND deleted = FALSE",
                (rs, rowNum) -> rs.getInt(1),
                id);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM voice_notes WHERE id = ?", id);
    }

    private VoiceNoteData mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        LocalDateTime referenceDate = rs.getObject("reference_date", LocalDateTime.class);
        int rawDuration = rs.getInt("duration_ms");
        Integer duration = rs.wasNull() ? null : rawDuration;
        return new VoiceNoteData(
                rs.getString("id"),
                rs.getString("period"),
                referenceDate == null ? "" : referenceDate.format(PeriodSheetName.FORMATTER),
                rs.getString("file_path"),
                rs.getString("mime_type"),
                duration,
                createdAt == null ? null : createdAt.toLocalDateTime().toString(),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getInt("retry_max"),
                rs.getString("ai_provider"));
    }
}
