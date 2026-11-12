package com.salarytracker.tools;

import com.salarytracker.tools.ToolsModels.QuitSmokingCraving;
import com.salarytracker.tools.ToolsModels.QuitSmokingCravingCreate;
import com.salarytracker.tools.ToolsModels.QuitSmokingDay;
import com.salarytracker.tools.ToolsModels.QuitSmokingDayUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingSettings;
import com.salarytracker.tools.ToolsModels.QuitSmokingSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ToolsController.class)
@Import(QuitSmokingResourceContractTest.MethodSecurity.class)
@ContextConfiguration(classes = {ToolsController.class, QuitSmokingResourceContractTest.MethodSecurity.class})
class QuitSmokingResourceContractTest {
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuitSmokingService quitSmokingService;

    @MockBean
    private DouyinLinkResolver douyinLinkResolver;

    @Test
    void rejectsAnonymousReads() throws Exception {
        mockMvc.perform(get("/api/v1/tools/quit-smoking/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "tools:read")
    void exposesSummaryForReaders() throws Exception {
        when(quitSmokingService.summary()).thenReturn(new QuitSmokingSummary(
                "2026-11-01T00:00:00Z", 5, 4, 0, 4, 2, new BigDecimal("95.00"), "CNY",
                "2026-11-05", 2, 1, 1, true, 3L, 7L, List.of()));

        mockMvc.perform(get("/api/v1/tools/quit-smoking/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreak").value(0))
                .andExpect(jsonPath("$.data.savedAmount").value(95.00))
                .andExpect(jsonPath("$.data.todayDate").value("2026-11-05"));
    }

    @Test
    @WithMockUser(authorities = "tools:read")
    void rejectsWritesWithoutWriteAuthority() throws Exception {
        mockMvc.perform(put("/api/v1/tools/quit-smoking/days/2026-11-05")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smokedCount\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"tools:read", "tools:write"})
    void passesIfMatchAndReturnsDay() throws Exception {
        when(quitSmokingService.putDay(eq("2026-11-05"), any(), eq("3")))
                .thenReturn(new QuitSmokingDay("2026-11-05", 1, "", 4L));

        mockMvc.perform(put("/api/v1/tools/quit-smoking/days/2026-11-05")
                        .with(csrf())
                        .header("If-Match", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smokedCount\":1,\"note\":\"应酬\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-05"))
                .andExpect(jsonPath("$.data.smokedCount").value(1))
                .andExpect(jsonPath("$.data.revision").value(4));
    }

    @Test
    @WithMockUser(authorities = {"tools:read", "tools:write"})
    void forwardsIdempotencyKeyWhenCreatingCraving() throws Exception {
        when(quitSmokingService.createCraving(any(QuitSmokingCravingCreate.class), eq("key-1")))
                .thenReturn(new QuitSmokingCraving("c-1", "2026-11-05T02:00:00Z", "2026-11-05", 3,
                        true, "饭后", "", 1L));

        mockMvc.perform(post("/api/v1/tools/quit-smoking/cravings")
                        .with(csrf())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intensity\":3,\"resisted\":true,\"trigger\":\"饭后\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trigger").value("饭后"))
                .andExpect(jsonPath("$.data.resisted").value(true));

        verify(quitSmokingService).createCraving(any(QuitSmokingCravingCreate.class), eq("key-1"));
    }

    @Test
    @WithMockUser(authorities = "tools:read")
    void exposesSettingsIncludingUnconfiguredState() throws Exception {
        when(quitSmokingService.settings()).thenReturn(new QuitSmokingSettings("", "2026-11-01T00:00:00Z",
                "Asia/Shanghai", 20, BigDecimal.ZERO, 20, "CNY", false, 0L));

        mockMvc.perform(get("/api/v1/tools/quit-smoking/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.baselinePerDay").value(20))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"));
    }

    @Test
    void rejectsAnonymousDouyinResolve() throws Exception {
        mockMvc.perform(post("/api/v1/tools/douyin/resolve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://v.douyin.com/abcd123/\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "tools:read")
    void exposesDouyinResolutionForReaders() throws Exception {
        when(douyinLinkResolver.resolve("https://v.douyin.com/abcd123/"))
                .thenReturn(new ToolsModels.DouyinResolveResult("https://v.douyin.com/abcd123/",
                        "https://www.douyin.com/video/7400", "7400",
                        List.of("https://v.douyin.com/abcd123/", "https://www.douyin.com/video/7400"),
                        "www.douyin.com", true, true, ""));

        mockMvc.perform(post("/api/v1/tools/douyin/resolve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://v.douyin.com/abcd123/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoId").value("7400"))
                .andExpect(jsonPath("$.data.resolved").value(true))
                .andExpect(jsonPath("$.data.hops.length()").value(2));
    }
}
