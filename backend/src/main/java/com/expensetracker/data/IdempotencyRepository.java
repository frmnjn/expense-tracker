package com.expensetracker.data;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class IdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String find(String key) {
        List<String> rows = jdbcTemplate.query(
                "SELECT response_json FROM idempotency_keys WHERE id_key = ?",
                (rs, rowNum) -> rs.getString("response_json"),
                key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Menyimpan key baru. Mengembalikan false jika key sudah ada (menangani race
     * dua request bersamaan dengan key yang sama) atau gagal disimpan.
     */
    public boolean save(String key, String responseJson) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO idempotency_keys (id_key, response_json, created_at) VALUES (?, ?, ?)",
                    key, responseJson, Timestamp.valueOf(LocalDateTime.now()));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public void deleteOlderThan(LocalDateTime cutoff) {
        jdbcTemplate.update("DELETE FROM idempotency_keys WHERE created_at < ?", Timestamp.valueOf(cutoff));
    }
}
