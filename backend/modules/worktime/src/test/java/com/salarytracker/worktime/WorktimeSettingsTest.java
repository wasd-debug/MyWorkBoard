package com.salarytracker.worktime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import com.salarytracker.worktime.WorktimeModels.SettingsUpdate;

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
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.query(
                eq("SELECT revision FROM work_setting WHERE user_id = ?"), any(RowMapper.class), eq(7L)))
                .thenReturn(List.of(4L));
        WorktimeService service = new WorktimeService(jdbcTemplate, new ObjectMapper(), currentUser);

        service.writeSettings(new SettingsUpdate(null, null, null, null, null, null, null, null, Map.of()), "4");

        verify(jdbcTemplate).update("DELETE FROM salary_monthly WHERE user_id = ?", 7L);
    }
}
