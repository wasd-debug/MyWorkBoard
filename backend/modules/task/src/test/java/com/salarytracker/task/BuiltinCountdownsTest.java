package com.salarytracker.task;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 内置发薪日：每月 7 日，遇非工作日提前到之前最近的工作日。 */
class BuiltinCountdownsTest {

    /** 2026 国庆：10/1-10/7 放假，10/10 补班 */
    private static Map<LocalDate, Boolean> octoberTable() {
        Map<LocalDate, Boolean> table = new HashMap<>();
        for (int day = 1; day <= 7; day++) table.put(LocalDate.of(2026, 10, day), true);
        table.put(LocalDate.of(2026, 10, 10), false);
        return table;
    }

    @Test
    void rollsBackToPreviousWorkdayWhenSeventhFallsOnHoliday() {
        // 10/7 在国庆假期里 → 9/30（周三，工作日）发
        assertEquals(LocalDate.of(2026, 9, 30),
                BuiltinCountdowns.paydayOf(2026, 10, octoberTable()));
    }

    @Test
    void rollsBackOverWeekend() {
        // 2026-03-07 是周六 → 3/6 周五
        assertEquals(LocalDate.of(2026, 3, 6), BuiltinCountdowns.paydayOf(2026, 3, Map.of()));
        // 2026-02-07 是周六 → 2/6 周五
        assertEquals(LocalDate.of(2026, 2, 6), BuiltinCountdowns.paydayOf(2026, 2, Map.of()));
    }

    @Test
    void keepsSeventhWhenItIsWorkday() {
        // 2026-11-07 是周六 → 11/6；2026-12-07 是周一 → 12/7
        assertEquals(LocalDate.of(2026, 11, 6), BuiltinCountdowns.paydayOf(2026, 11, Map.of()));
        assertEquals(LocalDate.of(2026, 12, 7), BuiltinCountdowns.paydayOf(2026, 12, Map.of()));
    }

    @Test
    void makeUpWorkdayCountsAsWorkday() {
        LocalDate saturday = LocalDate.of(2026, 10, 10);
        assertTrue(BuiltinCountdowns.isNonWorkingDay(saturday, Map.of()));
        assertFalse(BuiltinCountdowns.isNonWorkingDay(saturday, octoberTable()));
    }

    @Test
    void nextPaydayRollsToNextMonthAfterPaydayPassed() {
        // 9/30 已经发过 → 下一个是 11/7（周六）→ 11/6
        assertEquals(LocalDate.of(2026, 11, 6),
                BuiltinCountdowns.nextPayday(LocalDate.of(2026, 10, 1), octoberTable()));
        assertEquals(LocalDate.of(2026, 9, 30),
                BuiltinCountdowns.nextPayday(LocalDate.of(2026, 9, 20), octoberTable()));
        assertEquals(LocalDate.of(2026, 12, 7),
                BuiltinCountdowns.nextPayday(LocalDate.of(2026, 11, 9), Map.of()));
    }

    @Test
    void paydayIsRegisteredAsBuiltinItem() {
        List<BuiltinCountdowns.BuiltinCountdown> items =
                BuiltinCountdowns.all(LocalDate.of(2026, 11, 9), Map.of());
        BuiltinCountdowns.BuiltinCountdown payday = items.stream()
                .filter(item -> item.key().equals("payday"))
                .findFirst().orElseThrow();
        assertEquals("距离发薪日", payday.title());
        assertEquals(LocalDate.of(2026, 12, 7), payday.targetDate());
    }
}
