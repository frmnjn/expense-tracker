package com.expensetracker.data;

import com.expensetracker.model.BudgetOption;
import com.expensetracker.model.CategoryOption;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class BudgetRepository {

    private final JdbcTemplate jdbcTemplate;

    public BudgetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BudgetOption> getOptions() {
        Map<String, List<CategoryOption>> categoriesByBudget = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT b.name AS budget_name, c.id AS category_id, c.name AS category_name, c.description AS category_description "
                        + "FROM categories c JOIN budgets b ON b.id = c.budget_id "
                        + "WHERE c.is_active = TRUE AND b.is_active = TRUE ORDER BY b.name, c.name",
                rs -> {
                    String budgetName = rs.getString("budget_name");
                    categoriesByBudget.computeIfAbsent(budgetName, k -> new ArrayList<>())
                            .add(new CategoryOption(rs.getLong("category_id"), rs.getString("category_name"),
                                    rs.getString("category_description")));
                });
        return jdbcTemplate.query(
                "SELECT name, balance, alert_threshold, description FROM budgets WHERE is_active = TRUE ORDER BY name",
                (rs, rowNum) -> new BudgetOption(rs.getString("name"), rs.getLong("balance"),
                        rs.getLong("alert_threshold"), rs.getString("description"),
                        categoriesByBudget.getOrDefault(rs.getString("name"), List.of())));
    }

    public Long findIdByName(String name) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM budgets WHERE name = ? AND is_active = TRUE",
                (rs, rowNum) -> rs.getLong("id"),
                name);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long getBalance(String name) {
        List<Long> balances = jdbcTemplate.query(
                "SELECT balance FROM budgets WHERE name = ?",
                (rs, rowNum) -> rs.getLong("balance"),
                name);
        return balances.isEmpty() ? 0 : balances.get(0);
    }

    public long getAlertThreshold(String name) {
        List<Long> thresholds = jdbcTemplate.query(
                "SELECT alert_threshold FROM budgets WHERE name = ?",
                (rs, rowNum) -> rs.getLong("alert_threshold"),
                name);
        return thresholds.isEmpty() ? 0 : thresholds.get(0);
    }

    public void adjustBalance(String name, long delta) {
        int updated = jdbcTemplate.update(
                "UPDATE budgets SET balance = balance + ? WHERE name = ? AND is_active = TRUE",
                delta, name);
        if (updated == 0) {
            throw new IllegalStateException("Budget not found: " + name);
        }
    }

    public void create(String name, long balance, long alertThreshold, String description) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO budgets (name, balance, alert_threshold, description, is_active) "
                            + "VALUES (?, ?, ?, ?, TRUE)",
                    name, balance, alertThreshold, description);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Budget already exists: " + name, e);
        }
    }

    public boolean softDelete(String name) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM budgets WHERE name = ? AND is_active = TRUE",
                (rs, rowNum) -> rs.getLong("id"),
                name);
        if (ids.isEmpty()) {
            return false;
        }
        Long id = ids.get(0);
        jdbcTemplate.update("UPDATE expenses SET deleted = TRUE WHERE budget_id = ?", id);
        jdbcTemplate.update(
                "UPDATE budgets SET name = CONCAT('DELETED_', LEFT(name, 200), '_', id), is_active = FALSE "
                        + "WHERE id = ?",
                id);
        return true;
    }

    public boolean update(String oldName, String newName, Long balance, Long alertThreshold, String description) {
        try {
            return jdbcTemplate.update(
                    "UPDATE budgets SET name = ?, balance = COALESCE(?, balance), "
                            + "alert_threshold = COALESCE(?, alert_threshold), "
                            + "description = COALESCE(?, description) "
                            + "WHERE name = ? AND is_active = TRUE",
                    newName, balance, alertThreshold, description, oldName) > 0;
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Budget already exists: " + newName, e);
        }
    }
}
