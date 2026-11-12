package com.salarytracker.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.tools.ToolsModels.QuitSmokingCraving;
import com.salarytracker.tools.ToolsModels.QuitSmokingCravingCreate;
import com.salarytracker.tools.ToolsModels.QuitSmokingDay;
import com.salarytracker.tools.ToolsModels.QuitSmokingDayUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingSettings;
import com.salarytracker.tools.ToolsModels.QuitSmokingSettingsUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuitSmokingIntegrationTest extends MySqlIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void keepsSettingsAndDaysIsolatedPerUser() {
        long first = createUser();
        long second = createUser();

        QuitSmokingService firstService = service(first);
        QuitSmokingService secondService = service(second);

        firstService.updateSettings(new QuitSmokingSettingsUpdate("2026-11-01T00:00:00Z", "Asia/Shanghai",
                20, new BigDecimal("25.00"), 20, "CNY"), null);
        String today = LocalDate.now(ZONE).toString();
        firstService.putDay(today, new QuitSmokingDayUpdate(2, ""), null);

        QuitSmokingSettings secondSettings = secondService.settings();
        assertFalse(secondSettings.configured(), "第二个用户不该看到第一个用户的设置");
        assertTrue(secondService.days(null, null).isEmpty(), "第二个用户不该看到第一个用户的打卡");
    }

    @Test
    void putDayIsIdempotentAndBumpsRevision() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);
        String today = LocalDate.now(ZONE).toString();

        QuitSmokingDay created = svc.putDay(today, new QuitSmokingDayUpdate(1, "应酬"), null);
        assertEquals(0, created.revision());

        QuitSmokingDay updated = svc.putDay(today, new QuitSmokingDayUpdate(5, "复吸"), null);
        assertEquals(1, updated.revision());
        assertEquals(5, updated.smokedCount());

        List<QuitSmokingDay> rows = svc.days(today, today);
        assertEquals(1, rows.size(), "同一天只能有一行打卡");
        assertEquals(5, rows.get(0).smokedCount());
    }

    @Test
    void rejectsStaleRevision() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);
        String today = LocalDate.now(ZONE).toString();
        svc.putDay(today, new QuitSmokingDayUpdate(1, ""), null);
        svc.putDay(today, new QuitSmokingDayUpdate(1, ""), null);

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> svc.putDay(today, new QuitSmokingDayUpdate(2, ""), "0"));
        assertEquals(1L, conflict.getServerRevision());
    }

    @Test
    void summaryAggregatesDaysAndCravings() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);
        LocalDate today = LocalDate.now(ZONE);
        svc.updateSettings(new QuitSmokingSettingsUpdate(today.minusDays(4) + "T00:00:00Z",
                "Asia/Shanghai", 20, new BigDecimal("20.00"), 20, "CNY"), null);

        svc.putDay(today.minusDays(1).toString(), new QuitSmokingDayUpdate(4, ""), null);
        svc.createCraving(new QuitSmokingCravingCreate(null, 4, true, "饭后", ""), null);
        svc.createCraving(new QuitSmokingCravingCreate(null, 2, false, "压力", ""), null);

        QuitSmokingSummary summary = svc.summary();
        assertEquals(today.toString(), summary.todayDate());
        assertEquals(5, summary.elapsedDays());
        assertEquals(4, summary.cleanDays());
        assertEquals(1, summary.currentStreak());
        // 起点后两天 + 昨天断签后一天，最长连续为起点后三天
        assertEquals(3, summary.longestStreak());
        assertEquals(4, summary.totalSmoked());
        // 基线 20 × 5 = 100 支，实际 4 支 → 96 支 × 1 元
        assertEquals(0, summary.savedAmount().compareTo(new BigDecimal("96.00")));
        assertEquals(2, summary.todayCravings());
        assertEquals(1, summary.todayResisted());
    }

    @Test
    void creatingCravingTwiceWithSameKeyReturnsTheSameRow() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);
        QuitSmokingCravingCreate command = new QuitSmokingCravingCreate(null, 3, true, "饭后", "");

        QuitSmokingCraving first = svc.createCraving(command, "retry-1");
        QuitSmokingCraving second = svc.createCraving(command, "retry-1");

        assertEquals(first.id(), second.id());
        assertEquals(1, svc.cravings(null, null).size());
    }

    @Test
    void deleteCravingHidesItFromLists() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);
        QuitSmokingCraving created = svc.createCraving(
                new QuitSmokingCravingCreate(null, 3, true, "", ""), null);

        svc.deleteCraving(created.id(), null);

        assertTrue(svc.cravings(null, null).isEmpty());
        assertEquals(0, svc.summary().todayCravings());
    }

    private QuitSmokingService service(long userId) {
        CurrentUserResolver current = mock(CurrentUserResolver.class);
        when(current.id()).thenReturn(userId);
        return new QuitSmokingService(jdbc, current, new ObjectMapper().findAndRegisterModules());
    }

    private long createUser() {
        String username = "quit-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?,'!','戒烟测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
