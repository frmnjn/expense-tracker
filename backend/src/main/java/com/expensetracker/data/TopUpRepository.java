package com.expensetracker.data;

import com.expensetracker.model.TopUpResponse;
import com.expensetracker.service.PeriodSheetName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TopUpRepository {

    private final JdbcTemplate jdbcTemplate;

    public TopUpRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, LocalDateTime dateTime, long budgetId, long amount, String description) {
        jdbcTemplate.update(
                "INSERT INTO top_ups (id, date_time, budget_id, amount, description) VALUES (?, ?, ?, ?, ?)",
                id, dateTime, budgetId, amount, description == null ? "" : description);
    }

    public List<TopUpResponse> getTopUps() {
        return jdbcTemplate.query(
                "SELECT t.id, t.date_time, b.name AS budget_name, t.amount, t.description "
                        + "FROM top_ups t JOIN budgets b ON b.id = t.budget_id ORDER BY t.date_time DESC",
                this::mapRow);
    }

    public Optional<TopUpData> findById(String id) {
        return jdbcTemplate.query(
                "SELECT t.id, b.name AS budget_name, t.amount, t.date_time FROM top_ups t "
                        + "JOIN budgets b ON b.id = t.budget_id WHERE t.id = ?",
                (rs, rowNum) -> new TopUpData(
                        rs.getString("id"),
                        rs.getString("budget_name"),
                        rs.getLong("amount"),
                        rs.getObject("date_time", LocalDateTime.class)),
                id).stream().findFirst();
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM top_ups WHERE id = ?", id);
    }

    public List<TopUpResponse> findByDateRange(LocalDateTime from, LocalDateTime toExclusive) {
        return jdbcTemplate.query(
                "SELECT t.id, t.date_time, b.name AS budget_name, t.amount, t.description "
                        + "FROM top_ups t JOIN budgets b ON b.id = t.budget_id "
                        + "WHERE t.date_time >= ? AND t.date_time < ? ORDER BY t.date_time ASC",
                this::mapRow,
                from, toExclusive);
    }

    private TopUpResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        LocalDateTime dateTime = rs.getObject("date_time", LocalDateTime.class);
        return new TopUpResponse(
                rs.getString("id"),
                dateTime == null ? "" : dateTime.format(PeriodSheetName.FORMATTER),
                rs.getString("budget_name"),
                rs.getLong("amount"),
                rs.getString("description"));
    }
}
