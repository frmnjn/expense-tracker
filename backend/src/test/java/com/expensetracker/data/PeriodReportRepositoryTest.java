package com.expensetracker.data;

import com.expensetracker.model.PeriodReportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodReportRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ResultSet resultSet;

    @Test
    void findAll_shouldMapPeriodReport() throws Exception {
        when(resultSet.getString("period")).thenReturn("2026-SEP-OCT");
        when(resultSet.getString("file_name")).thenReturn("report-2026-SEP-OCT.pdf");
        when(resultSet.getLong("size_bytes")).thenReturn(50_149L);
        when(resultSet.getTimestamp("generated_at"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 23, 23, 51)));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<PeriodReportResponse> mapper =
                            (RowMapper<PeriodReportResponse>) invocation.getArgument(1);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        List<PeriodReportResponse> reports = new PeriodReportRepository(jdbcTemplate).findAll();

        assertEquals(1, reports.size());
        assertEquals("2026-SEP-OCT", reports.get(0).period());
        assertEquals("report-2026-SEP-OCT.pdf", reports.get(0).fileName());
        assertEquals(50_149L, reports.get(0).sizeBytes());
        assertEquals("2026-10-23 23:51", reports.get(0).generatedAt());
    }

    @Test
    void updateFile_shouldRefreshSizeAndGenerationTime() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        boolean updated = new PeriodReportRepository(jdbcTemplate).updateFile(
                "2026-SEP-OCT", "report-2026-SEP-OCT.pdf", 60_000L,
                LocalDateTime.of(2026, 10, 24, 10, 15));

        assertTrue(updated);
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
