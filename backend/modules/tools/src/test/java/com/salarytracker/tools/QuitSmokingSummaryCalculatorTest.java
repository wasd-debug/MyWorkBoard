package com.salarytracker.tools;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuitSmokingSummaryCalculatorTest {
    private static final LocalDate START = LocalDate.of(2026, 11, 1);

    @Test
    void countsUnrecordedDaysAsCleanDays() {
        // 11-01 起戒烟，今天是 11-05：只有 11-03 有记录且为 0，其余日期没有记录。
        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(START, LocalDate.of(2026, 11, 5), Map.of(LocalDate.of(2026, 11, 3), 0));

        assertEquals(5, totals.elapsedDays());
        assertEquals(5, totals.cleanDays());
        assertEquals(5, totals.currentStreak());
        assertEquals(5, totals.longestStreak());
        assertEquals(0, totals.totalSmoked());
    }

    @Test
    void smokingTodayBreaksTheStreakButKeepsElapsedDays() {
        Map<LocalDate, Integer> smoked = new HashMap<>();
        smoked.put(LocalDate.of(2026, 11, 5), 2);

        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(START, LocalDate.of(2026, 11, 5), smoked);

        assertEquals(5, totals.elapsedDays());
        assertEquals(4, totals.cleanDays());
        assertEquals(0, totals.currentStreak());
        assertEquals(4, totals.longestStreak());
        assertEquals(2, totals.totalSmoked());
    }

    @Test
    void recordsBeforeStartDateAreIgnored() {
        Map<LocalDate, Integer> smoked = new HashMap<>();
        smoked.put(LocalDate.of(2026, 10, 20), 5);
        smoked.put(LocalDate.of(2026, 11, 6), 5);

        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(START, LocalDate.of(2026, 11, 6), smoked);

        assertEquals(6, totals.elapsedDays());
        assertEquals(5, totals.cleanDays());
        assertEquals(0, totals.currentStreak());
        assertEquals(5, totals.totalSmoked());
    }

    @Test
    void findsLongestStreakAcrossTheWholePeriod() {
        Map<LocalDate, Integer> smoked = new HashMap<>();
        smoked.put(LocalDate.of(2026, 11, 3), 1);
        smoked.put(LocalDate.of(2026, 11, 7), 1);

        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(START, LocalDate.of(2026, 11, 8), smoked);

        // 11-01..11-02 = 2 天，11-04..11-06 = 3 天，11-08 = 1 天
        assertEquals(3, totals.longestStreak());
        assertEquals(1, totals.currentStreak());
        assertEquals(2, totals.totalSmoked());
        assertEquals(6, totals.cleanDays());
    }

    @Test
    void returnsZerosWhenStartIsInTheFuture() {
        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 8), Map.of());

        assertEquals(0, totals.elapsedDays());
        assertEquals(0, totals.cleanDays());
        assertEquals(0, totals.currentStreak());
        assertEquals(0, totals.longestStreak());
        assertEquals(0, totals.totalSmoked());
    }

    @Test
    void savedAmountIsPositiveWhenSmokingLessThanBaseline() {
        Map<LocalDate, Integer> smoked = Map.of(LocalDate.of(2026, 11, 2), 5);
        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(START, LocalDate.of(2026, 11, 5), smoked);

        // 基线 20 支/天 × 5 天 = 100 支，实际 5 支，省 95 支；每包 20 元 / 20 支 = 1 元/支
        BigDecimal saved = QuitSmokingSummaryCalculator.savedAmount(totals, 20, new BigDecimal("20.00"), 20);
        assertEquals(new BigDecimal("95.00"), saved);
    }

    @Test
    void savedAmountIsNegativeWhenSmokingMoreThanBaseline() {
        Map<LocalDate, Integer> smoked = Map.of(LocalDate.of(2026, 11, 5), 40);
        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(START, LocalDate.of(2026, 11, 5), smoked);

        BigDecimal saved = QuitSmokingSummaryCalculator.savedAmount(totals, 10, new BigDecimal("30.00"), 20);
        // 基线 50 支，实际 40 支 → 仍为正（10 支 × 1.5）
        assertTrue(saved.signum() > 0);

        BigDecimal overspent = QuitSmokingSummaryCalculator.savedAmount(totals, 5, new BigDecimal("30.00"), 20);
        // 基线 25 支，实际 40 支 → 多用 15 支 = 22.50
        assertEquals(new BigDecimal("-22.50"), overspent);
    }

    @Test
    void savedAmountIsZeroWithoutUsablePrice() {
        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(START, LocalDate.of(2026, 11, 5), Map.of());

        assertEquals(0, QuitSmokingSummaryCalculator
                .savedAmount(totals, 20, BigDecimal.ZERO, 20).signum());
        assertEquals(0, QuitSmokingSummaryCalculator
                .savedAmount(totals, 20, new BigDecimal("20.00"), 0).signum());
    }
}
