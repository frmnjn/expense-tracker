package com.expensetracker.data;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ResultSet resultSet;

    @Test
    void findById_shouldReadDateTimeAsLocalWallClockWithoutTimezoneShift() throws Exception {
        LocalDateTime stored = LocalDateTime.of(2026, 10, 22, 18, 9);
        when(resultSet.getString(anyString())).thenReturn(null);
        when(resultSet.getString("id")).thenReturn("e1");
        when(resultSet.getString("period")).thenReturn("2026-SEP-OCT");
        when(resultSet.getString("name")).thenReturn("Makan Siang");
        when(resultSet.getString("budget_name")).thenReturn("Daily");
        when(resultSet.getString("category_name")).thenReturn("Eating Out");
        when(resultSet.getLong("amount")).thenReturn(35_000L);
        when(resultSet.getString("description")).thenReturn("");
        when(resultSet.getObject("date_time", LocalDateTime.class)).thenReturn(stored);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<ExpenseData> mapper = (RowMapper<ExpenseData>) invocation.getArgument(1);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        ExpenseData expense = new ExpenseRepository(jdbcTemplate).findById("e1");

        assertEquals("2026-10-22 18:09", expense.dateTime());
        verify(resultSet).getObject("date_time", LocalDateTime.class);
    }
}
