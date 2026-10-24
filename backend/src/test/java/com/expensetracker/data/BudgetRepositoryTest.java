package com.expensetracker.data;

import com.expensetracker.model.BudgetOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ResultSet resultSet;

    @Test
    void getOptionsAt_shouldMapReconstructedBalance() throws Exception {
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<BudgetOption> mapper = (RowMapper<BudgetOption>) invocation.getArgument(1);
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        when(resultSet.getString("name")).thenReturn("Daily");
        when(resultSet.getLong("balance")).thenReturn(-1_000L);
        when(resultSet.getLong("alert_threshold")).thenReturn(50_000L);
        when(resultSet.getString("description")).thenReturn("Makan harian");

        List<BudgetOption> options = new BudgetRepository(jdbcTemplate)
                .getOptionsAt(LocalDateTime.of(2026, 10, 23, 0, 0));

        assertEquals(1, options.size());
        assertEquals("Daily", options.get(0).name());
        assertEquals(-1_000L, options.get(0).balance());
        assertEquals(50_000L, options.get(0).alertThreshold());
        assertEquals("Makan harian", options.get(0).description());
    }
}
