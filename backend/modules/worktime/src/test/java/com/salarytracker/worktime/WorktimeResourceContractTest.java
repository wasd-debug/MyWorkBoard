package com.salarytracker.worktime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.math.BigDecimal;
import java.util.Map;

import com.salarytracker.worktime.WorktimeModels.Basis;
import com.salarytracker.worktime.WorktimeModels.RecordCommand;
import com.salarytracker.worktime.WorktimeModels.Settings;
import com.salarytracker.worktime.WorktimeModels.SettingsUpdate;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(WorktimeController.class)
@Import(WorktimeResourceContractTest.MethodSecurity.class)
@ContextConfiguration(classes = {WorktimeController.class, WorktimeResourceContractTest.MethodSecurity.class})
class WorktimeResourceContractTest {
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorktimeService worktimeService;

    @Test
    void rejectsAnonymousResourceReads() throws Exception {
        mockMvc.perform(get("/api/v1/worktime/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "worktime:read")
    void listsRecordsWithPaginationAndStableCalculatedFields() throws Exception {
        when(worktimeService.listRecords("2026-01-01", "2026-12-31", 200, 200))
                .thenReturn(List.of(new WorkRecord(17L, "2026-09-17", "09:00", "18:30", 90,
                        30, new BigDecimal("42.50"), "", "phase0-v1", "Asia/Shanghai", 2L)));

        mockMvc.perform(get("/api/v1/worktime/records")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31")
                        .param("limit", "200")
                        .param("offset", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(17))
                .andExpect(jsonPath("$.data[0].overtimeMin").value(30))
                .andExpect(jsonPath("$.data[0].realHourlyWage").value(42.5))
                .andExpect(jsonPath("$.data[0].calcVersion").value("phase0-v1"))
                .andExpect(jsonPath("$.data[0].timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data[0].revision").value(2));
    }

    @Test
    @WithMockUser(authorities = "worktime:read")
    void rejectsWritesWithoutWorktimeWriteAuthority() throws Exception {
        mockMvc.perform(put("/api/v1/worktime/settings")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"workStart\":\"09:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "worktime:write")
    void forwardsRevisionAndIdempotencyHeadersForWrites() throws Exception {
        when(worktimeService.writeSettings(any(SettingsUpdate.class), org.mockito.ArgumentMatchers.eq("4")))
                .thenReturn(new Settings(BigDecimal.ZERO, BigDecimal.ZERO, Basis.POST, "09:00", "18:00",
                        90, new BigDecimal("21.75"), true, Map.of(), 5L));
        when(worktimeService.createRecord(any(RecordCommand.class), org.mockito.ArgumentMatchers.eq("create-1")))
                .thenReturn(new WorkRecord(18L, "2026-09-18", "09:00", "", 0, 0,
                        new BigDecimal("35"), "", "phase0-v1", "Asia/Shanghai", 1L));
        when(worktimeService.updateRecord(org.mockito.ArgumentMatchers.eq(18L), any(RecordCommand.class),
                org.mockito.ArgumentMatchers.eq("1")))
                .thenReturn(new WorkRecord(18L, "2026-09-18", "09:00", "18:30", 0, 0,
                        new BigDecimal("35"), "", "phase0-v1", "Asia/Shanghai", 2L));

        mockMvc.perform(put("/api/v1/worktime/settings")
                        .with(csrf())
                        .header("If-Match", "4")
                        .contentType("application/json")
                        .content("{\"workStart\":\"09:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(5));
        mockMvc.perform(post("/api/v1/worktime/records")
                        .with(csrf())
                        .header("Idempotency-Key", "create-1")
                        .contentType("application/json")
                        .content("{\"date\":\"2026-09-18\",\"start\":\"09:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calcVersion").value("phase0-v1"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"));
        mockMvc.perform(patch("/api/v1/worktime/records/18")
                        .with(csrf())
                        .header("If-Match", "1")
                        .contentType("application/json")
                        .content("{\"end\":\"18:30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2));

        verify(worktimeService).writeSettings(any(SettingsUpdate.class), org.mockito.ArgumentMatchers.eq("4"));
        verify(worktimeService).createRecord(any(RecordCommand.class), org.mockito.ArgumentMatchers.eq("create-1"));
        verify(worktimeService).updateRecord(org.mockito.ArgumentMatchers.eq(18L), any(RecordCommand.class),
                org.mockito.ArgumentMatchers.eq("1"));
    }
}
