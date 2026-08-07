package com.expensetracker.data;

import com.expensetracker.model.BudgetOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BudgetRepository {

    private final JdbcTemplate jdbcTemplate;

    public BudgetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BudgetOption> getOptions() {
        return jdbcTemplate.query(
                "SELECT name, balance FROM budgets ORDER BY name",
                (rs, rowNum) -> new BudgetOption(rs.getString("name"), rs.getLong("balance")));
    }

    public Long findIdByName(String name) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM budgets WHERE name = ?",
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

    public void adjustBalance(String name, long delta) {
        int updated = jdbcTemplate.update(
                "UPDATE budgets SET balance = balance + ? WHERE name = ?",
                delta, name);
        if (updated == 0) {
            throw new IllegalStateException("Budget not found: " + name);
        }
    }

    public void create(String name, long balance) {
        jdbcTemplate.update(
                "INSERT INTO budgets (name, balance) VALUES (?, ?)",
                name, balance);
    }
}
