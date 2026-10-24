package com.expensetracker.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class PaydayOverrideRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaydayOverrideRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, LocalDate> findAll() {
        return jdbcTemplate.query(
                "SELECT period, payday_date FROM payday_overrides",
                rs -> {
                    Map<String, LocalDate> overrides = new LinkedHashMap<>();
                    while (rs.next()) {
                        overrides.put(rs.getString("period"), rs.getDate("payday_date").toLocalDate());
                    }
                    return overrides;
                });
    }

    public void upsert(String period, LocalDate paydayDate) {
        jdbcTemplate.update(
                "INSERT INTO payday_overrides (period, payday_date) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE payday_date = VALUES(payday_date)",
                period, paydayDate);
    }

    public void delete(String period) {
        jdbcTemplate.update("DELETE FROM payday_overrides WHERE period = ?", period);
    }
}
