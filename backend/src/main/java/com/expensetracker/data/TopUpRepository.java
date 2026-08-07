package com.expensetracker.data;

import com.expensetracker.model.TopUpResponse;
import com.expensetracker.service.PeriodSheetName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TopUpRepository {

    private final JdbcTemplate jdbcTemplate;

    public TopUpRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, LocalDateTime dateTime, long budgetId, long amount, String description) {
        jdbcTemplate.update(
                "INSERT INTO top_ups (id, date_time, budget_id, amount, description) VALUES (?, ?, ?, ?, ?)",
                id, Timestamp.valueOf(dateTime), budgetId, amount, description == null ? "" : description);
    }

    public List<TopUpResponse> getTopUps() {
        return jdbcTemplate.query(
                "SELECT t.id, t.date_time, b.name AS budget_name, t.amount, t.description "
                        + "FROM top_ups t JOIN budgets b ON b.id = t.budget_id ORDER BY t.date_time DESC",
                this::mapRow);
    }

    private TopUpResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TopUpResponse(
                rs.getString("id"),
                rs.getTimestamp("date_time") == null ? "" : rs.getTimestamp("date_time").toLocalDateTime()
                        .format(PeriodSheetName.FORMATTER),
                rs.getString("budget_name"),
                rs.getLong("amount"),
                rs.getString("description"));
    }
}
