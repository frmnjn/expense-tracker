package com.expensetracker.data;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class CategoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public CategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CategoryData> findByBudgetId(long budgetId) {
        return jdbcTemplate.query(
                "SELECT id, budget_id, name, description, is_active FROM categories "
                        + "WHERE budget_id = ? AND is_active = TRUE ORDER BY name",
                this::mapRow, budgetId);
    }

    public CategoryData findById(long id) {
        List<CategoryData> rows = jdbcTemplate.query(
                "SELECT id, budget_id, name, description, is_active FROM categories WHERE id = ?",
                this::mapRow, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Long findIdByBudgetAndName(long budgetId, String name) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM categories WHERE budget_id = ? AND name = ? AND is_active = TRUE",
                (rs, rowNum) -> rs.getLong("id"), budgetId, name);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public void create(long budgetId, String name, String description) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO categories (budget_id, name, description, is_active) VALUES (?, ?, ?, TRUE)",
                    budgetId, name, description);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Category already exists: " + name, e);
        }
    }

    public boolean update(long id, String name, String description) {
        try {
            return jdbcTemplate.update(
                    "UPDATE categories SET name = ?, description = ? WHERE id = ? AND is_active = TRUE",
                    name, description, id) > 0;
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Category already exists: " + name, e);
        }
    }

    public void softDelete(long id) {
        jdbcTemplate.update("UPDATE categories SET is_active = FALSE WHERE id = ?", id);
    }

    public boolean hasActiveByBudgetId(long budgetId) {
        List<Integer> counts = jdbcTemplate.query(
                "SELECT COUNT(*) FROM categories WHERE budget_id = ? AND is_active = TRUE",
                (rs, rowNum) -> rs.getInt(1), budgetId);
        return !counts.isEmpty() && counts.get(0) > 0;
    }

    private CategoryData mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CategoryData(
                rs.getLong("id"),
                rs.getLong("budget_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("is_active"));
    }
}
