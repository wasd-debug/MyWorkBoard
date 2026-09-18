package com.salarytracker.worktime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorktimePaginationTest {
    @Test
    void listRecordsAppliesTheRequestedOffset() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(7L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        WorktimeService service = new WorktimeService(jdbcTemplate, new ObjectMapper(), currentUser);

        Method method = WorktimeService.class.getMethod(
                "listRecords", String.class, String.class, int.class, int.class);
        Object result = method.invoke(service, null, null, 200, 200);

        assertTrue(result instanceof List<?>);
        verify(jdbcTemplate).query(
                eq("SELECT id, date, TIME_FORMAT(start_time, '%H:%i') start_time, IFNULL(TIME_FORMAT(end_time, '%H:%i'), '') end_time, rest_min, overtime_min, real_hourly_wage, note, calc_version, timezone, revision FROM work_record WHERE user_id = ? AND deleted = FALSE ORDER BY date DESC LIMIT ? OFFSET ?"),
                any(RowMapper.class),
                eq(7L), eq(200), eq(200));
    }
}
