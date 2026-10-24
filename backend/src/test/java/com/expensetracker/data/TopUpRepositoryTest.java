package com.expensetracker.data;

import com.expensetracker.model.TopUpResponse;
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
class TopUpRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ResultSet resultSet;

    @Test
    void getTopUps_shouldReadDateTimeAsLocalWallClockWithoutTimezoneShift() throws Exception {
        LocalDateTime stored = LocalDateTime.of(2026, 9, 25, 6, 11);
        when(resultSet.getString(anyString())).thenReturn(null);
        when(resultSet.getString("id")).thenReturn("t1");
        when(resultSet.getString("budget_name")).thenReturn("Daily");
        when(resultSet.getLong("amount")).thenReturn(50_000L);
        when(resultSet.getObject("date_time", LocalDateTime.class)).thenReturn(stored);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<TopUpResponse> mapper = (RowMapper<TopUpResponse>) invocation.getArgument(1);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        List<TopUpResponse> topUps = new TopUpRepository(jdbcTemplate).getTopUps();

        assertEquals("2026-09-25 06:11", topUps.get(0).dateTime());
        verify(resultSet).getObject("date_time", LocalDateTime.class);
    }
}
