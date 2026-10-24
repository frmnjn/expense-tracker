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
class EmailImportRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ResultSet resultSet;

    @Test
    void findById_shouldReadTransactionAtAsLocalWallClock() throws Exception {
        LocalDateTime transactionAt = LocalDateTime.of(2026, 10, 23, 22, 30, 59);
        when(resultSet.getString(anyString())).thenReturn(null);
        when(resultSet.getString("id")).thenReturn("e1");
        when(resultSet.getObject("transaction_at", LocalDateTime.class)).thenReturn(transactionAt);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<EmailImportData> mapper = (RowMapper<EmailImportData>) invocation.getArgument(1);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        EmailImportData data = new EmailImportRepository(jdbcTemplate).findById("e1");

        assertEquals("2026-10-23T22:30:59", data.transactionAt());
        verify(resultSet).getObject("transaction_at", LocalDateTime.class);
    }
}
