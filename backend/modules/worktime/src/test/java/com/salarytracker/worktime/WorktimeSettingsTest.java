package com.salarytracker.worktime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorktimeSettingsTest {
    @Test
    void replacingSettingsRemovesMonthlySalariesOmittedFromTheSubmittedCollection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(7L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                eq("SELECT revision FROM work_setting WHERE user_id = ?"), eq(7L)))
                .thenReturn(List.of(Map.of("revision", 4L)));
        WorktimeService service = new WorktimeService(jdbcTemplate, new ObjectMapper(), currentUser);

        service.writeSettings(Map.of("salaries", Map.of()), "4");

        verify(jdbcTemplate).update("DELETE FROM salary_monthly WHERE user_id = ?", 7L);
    }
}
