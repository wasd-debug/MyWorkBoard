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
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
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
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("id", 17L),
                        Map.entry("date", "2026-09-17"),
                        Map.entry("start", "09:00"),
                        Map.entry("end", "18:30"),
                        Map.entry("rest", 90),
                        Map.entry("overtimeMin", 30),
                        Map.entry("realHourlyWage", 42.50),
                        Map.entry("calcVersion", 1),
                        Map.entry("timezone", "Asia/Shanghai"),
                        Map.entry("revision", 2L))));

        mockMvc.perform(get("/api/v1/worktime/records")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31")
                        .param("limit", "200")
                        .param("offset", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(17))
                .andExpect(jsonPath("$.data[0].overtimeMin").value(30))
                .andExpect(jsonPath("$.data[0].realHourlyWage").value(42.5))
                .andExpect(jsonPath("$.data[0].calcVersion").value(1))
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
        when(worktimeService.writeSettings(anyMap(), org.mockito.ArgumentMatchers.eq("4")))
                .thenReturn(Map.of("workStart", "09:00", "revision", 5L));
        when(worktimeService.createRecord(anyMap(), org.mockito.ArgumentMatchers.eq("create-1")))
                .thenReturn(Map.ofEntries(
                        Map.entry("id", 18L),
                        Map.entry("date", "2026-09-18"),
                        Map.entry("overtimeMin", 0),
                        Map.entry("realHourlyWage", 35),
                        Map.entry("calcVersion", 1),
                        Map.entry("timezone", "Asia/Shanghai"),
                        Map.entry("revision", 1L)));
        when(worktimeService.updateRecord(org.mockito.ArgumentMatchers.eq(18L), anyMap(),
                org.mockito.ArgumentMatchers.eq("1")))
                .thenReturn(Map.of("id", 18L, "date", "2026-09-18", "revision", 2L));

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
                .andExpect(jsonPath("$.data.calcVersion").value(1))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"));
        mockMvc.perform(patch("/api/v1/worktime/records/18")
                        .with(csrf())
                        .header("If-Match", "1")
                        .contentType("application/json")
                        .content("{\"end\":\"18:30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2));

        verify(worktimeService).writeSettings(anyMap(), org.mockito.ArgumentMatchers.eq("4"));
        verify(worktimeService).createRecord(anyMap(), org.mockito.ArgumentMatchers.eq("create-1"));
        verify(worktimeService).updateRecord(org.mockito.ArgumentMatchers.eq(18L), anyMap(),
                org.mockito.ArgumentMatchers.eq("1"));
    }
}
