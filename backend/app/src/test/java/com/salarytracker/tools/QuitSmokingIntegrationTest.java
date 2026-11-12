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
                20, new BigDecimal("25.00"), 20, "CNY", "regular", new BigDecimal("11.0"),
                new BigDecimal("12.0")), null);
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
                "Asia/Shanghai", 20, new BigDecimal("20.00"), 20, "CNY", "regular",
                new BigDecimal("11.0"), new BigDecimal("12.0")), null);

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
    void settingsPersistCigaretteTypeAndFeedTheSubstanceNumbers() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);
        LocalDate today = LocalDate.now(ZONE);
        svc.updateSettings(new QuitSmokingSettingsUpdate(today.minusDays(2) + "T00:00:00Z", "Asia/Shanghai",
                20, new BigDecimal("20.00"), 20, "CNY", "fine", new BigDecimal("8.0"),
                new BigDecimal("8.0")), null);
        svc.putDay(today.toString(), new QuitSmokingDayUpdate(3, ""), null);

        QuitSmokingSettings settings = svc.settings();
        assertEquals("fine", settings.cigaretteType());
        assertEquals(0, settings.tarPerCigarette().compareTo(new BigDecimal("8.0")));
        assertEquals(0, settings.coPerCigarette().compareTo(new BigDecimal("8.0")));

        QuitSmokingSummary summary = svc.summary();
        assertEquals("fine", summary.substance().cigaretteType());
        // 今天抽 3 支 × 8 mg
        assertEquals(0, summary.substance().todayTarMg().compareTo(new BigDecimal("24.0")));
        assertEquals(0, summary.substance().todayCoMg().compareTo(new BigDecimal("24.0")));
        // 基线 20 × 3 天 = 60 支，实际 3 支 → 少吸 57 支 × 8 mg
        assertEquals(0, summary.substance().avoidedTarMg().compareTo(new BigDecimal("456.0")));
    }

    @Test
    void switchingCigaretteTypeFallsBackToPresetMagnitudes() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);
        String start = LocalDate.now(ZONE).minusDays(1) + "T00:00:00Z";
        svc.updateSettings(new QuitSmokingSettingsUpdate(start, "Asia/Shanghai", 20, new BigDecimal("20.00"),
                20, "CNY", "regular", new BigDecimal("11.0"), new BigDecimal("12.0")), null);

        // 只改种类、不传毫克数：按该种类预设回填，而不是沿用旧值
        QuitSmokingSettings switched = svc.updateSettings(new QuitSmokingSettingsUpdate(null, null, null,
                null, null, null, "thick", null, null), null);

        assertEquals("thick", switched.cigaretteType());
        assertEquals(0, switched.tarPerCigarette().compareTo(new BigDecimal("13.0")));
        assertEquals(0, switched.coPerCigarette().compareTo(new BigDecimal("14.0")));
    }

    @Test
    void rejectsUnknownCigaretteTypeAndOutOfRangeMagnitudes() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);

        assertThrows(IllegalArgumentException.class, () -> svc.updateSettings(
                new QuitSmokingSettingsUpdate(null, null, null, null, null, null, "hookah", null, null), null));
        assertThrows(IllegalArgumentException.class, () -> svc.updateSettings(
                new QuitSmokingSettingsUpdate(null, null, null, null, null, null, "regular",
                        new BigDecimal("101"), null), null));
    }

    @Test
    void summaryCarriesRecoveryMilestones() {
        long userId = createUser();
        QuitSmokingService svc = service(userId);
        LocalDate today = LocalDate.now(ZONE);
        svc.updateSettings(new QuitSmokingSettingsUpdate(today.minusDays(3) + "T00:00:00Z", "Asia/Shanghai",
                20, new BigDecimal("20.00"), 20, "CNY", "regular", new BigDecimal("11.0"),
                new BigDecimal("12.0")), null);

        QuitSmokingSummary summary = svc.summary();

        assertTrue(summary.recovery().totalCount() >= 10);
        assertTrue(summary.recovery().reachedCount() >= 1, "已戒烟 4 天，至少应先跨过 20 分钟与 12 小时节点");
        assertFalse(summary.recovery().milestones().isEmpty());
        assertTrue(summary.recovery().milestones().stream().anyMatch(item -> item.reached()));
        assertFalse(summary.recovery().encouragement().isBlank());
    }

    @Test
    void deleteCravingHidesItFromLists() {        long userId = createUser();
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
