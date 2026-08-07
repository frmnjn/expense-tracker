package com.expensetracker.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.expensetracker.service.PeriodSheetName;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ExpenseRepository {

    private static final String SELECT_COLS =
            "SELECT e.id, e.period, e.date_time, e.name, b.name AS budget_name, e.amount, e.description, e.deleted "
                    + "FROM expenses e JOIN budgets b ON b.id = e.budget_id ";

    private final JdbcTemplate jdbcTemplate;

    public ExpenseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String id, String period, LocalDate periodStart, LocalDateTime dateTime,
                       long budgetId, String name, long amount, String description) {
        jdbcTemplate.update(
                "INSERT INTO expenses (id, period, period_start, date_time, budget_id, name, amount, description, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)",
                id, period, periodStart, dateTime, budgetId, name, amount, description == null ? "" : description);
    }

    public List<ExpenseData> getExpenses(String period) {
        return jdbcTemplate.query(
                SELECT_COLS + "WHERE e.period = ? AND e.deleted = FALSE ORDER BY e.date_time DESC",
                this::mapRow,
                period);
    }

    public List<String> getPeriods() {
        return jdbcTemplate.query(
                "SELECT period FROM expenses GROUP BY period ORDER BY MAX(period_start) DESC",
                (rs, rowNum) -> rs.getString("period"));
    }

    public ExpenseData findById(String id) {
        List<ExpenseData> rows = jdbcTemplate.query(
                SELECT_COLS + "WHERE e.id = ?",
                this::mapRow,
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void update(String id, LocalDate periodStart, LocalDateTime dateTime,
                       long budgetId, String name, long amount, String description) {
        jdbcTemplate.update(
                "UPDATE expenses SET period = ?, period_start = ?, date_time = ?, budget_id = ?, name = ?, amount = ?, description = ? "
                        + "WHERE id = ?",
                PeriodSheetName.forDate(dateTime.toLocalDate()), periodStart, dateTime, budgetId, name, amount,
                description == null ? "" : description, id);
    }

    public void softDelete(String id) {
        jdbcTemplate.update("UPDATE expenses SET deleted = TRUE WHERE id = ?", id);
    }

    public long totalForPeriod(String period) {
        List<Long> totals = jdbcTemplate.query(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE period = ? AND deleted = FALSE",
                (rs, rowNum) -> rs.getLong(1),
                period);
        return totals.isEmpty() ? 0 : totals.get(0);
    }

    public int countForPeriod(String period) {
        List<Integer> counts = jdbcTemplate.query(
                "SELECT COUNT(*) FROM expenses WHERE period = ? AND deleted = FALSE",
                (rs, rowNum) -> rs.getInt(1),
                period);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    private ExpenseData mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ExpenseData(
                rs.getString("id"),
                rs.getString("period"),
                rs.getTimestamp("date_time") == null ? "" : rs.getTimestamp("date_time").toLocalDateTime()
                        .format(PeriodSheetName.FORMATTER),
                rs.getString("name"),
                rs.getString("budget_name"),
                rs.getLong("amount"),
                rs.getString("description"),
                rs.getBoolean("deleted"));
    }
}
