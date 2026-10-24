package com.expensetracker.data;

import com.expensetracker.model.PeriodReportResponse;
import com.expensetracker.service.PeriodSheetName;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PeriodReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public PeriodReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean exists(String period) {
        List<Integer> rows = jdbcTemplate.query(
                "SELECT 1 FROM period_reports WHERE period = ?",
                (rs, rowNum) -> rs.getInt(1),
                period);
        return !rows.isEmpty();
    }

    public List<PeriodReportResponse> findAll() {
        return jdbcTemplate.query(
                "SELECT period, file_name, size_bytes, generated_at FROM period_reports "
                        + "ORDER BY generated_at DESC, period DESC",
                (rs, rowNum) -> {
                    Timestamp generatedAt = rs.getTimestamp("generated_at");
                    return new PeriodReportResponse(
                            rs.getString("period"),
                            rs.getString("file_name"),
                            rs.getLong("size_bytes"),
                            generatedAt == null ? "" : generatedAt.toLocalDateTime()
                                    .format(PeriodSheetName.FORMATTER));
                });
    }

    public Optional<String> findFileName(String period) {
        List<String> rows = jdbcTemplate.query(
                "SELECT file_name FROM period_reports WHERE period = ?",
                (rs, rowNum) -> rs.getString(1),
                period);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** @return false bila report periode tersebut sudah pernah dicatat. */
    public boolean insert(String period, String fileName, long sizeBytes) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO period_reports (period, file_name, size_bytes) VALUES (?, ?, ?)",
                    period, fileName, sizeBytes);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public boolean updateFile(String period, String fileName, long sizeBytes,
                              LocalDateTime generatedAt) {
        return jdbcTemplate.update(
                "UPDATE period_reports SET file_name = ?, size_bytes = ?, generated_at = ? WHERE period = ?",
                fileName, sizeBytes, Timestamp.valueOf(generatedAt), period) > 0;
    }
}
