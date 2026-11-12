package com.salarytracker.tools;

import com.salarytracker.tools.ToolsModels.QuitSmokingMilestone;
import com.salarytracker.tools.ToolsModels.QuitSmokingRecovery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuitSmokingMilestonesTest {
    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void reachesNothingAtTheVeryBeginning() {
        QuitSmokingRecovery recovery = QuitSmokingMilestones.recovery(START, START);

        assertEquals(0, recovery.elapsedMinutes());
        assertEquals(0, recovery.reachedCount());
        assertEquals("vascular-20min", recovery.nextMilestoneKey());
        assertEquals(20, recovery.nextRemainingMinutes());
        assertTrue(recovery.milestones().stream().noneMatch(QuitSmokingMilestone::reached));
        assertTrue(recovery.encouragement().contains("下一个节点"), recovery.encouragement());
    }

    @Test
    void marksTwentyMinuteMilestoneAndCountsDownTheNextOne() {
        QuitSmokingRecovery recovery = QuitSmokingMilestones.recovery(START, START.plus(25, ChronoUnit.MINUTES));

        assertEquals(1, recovery.reachedCount());
        assertEquals("vascular-8h", recovery.nextMilestoneKey());
        // 8 小时 = 480 分钟，已过 25 分钟
        assertEquals(455, recovery.nextRemainingMinutes());
        QuitSmokingMilestone first = recovery.milestones().get(0);
        assertTrue(first.reached());
        assertEquals("2026-11-01T00:20:00Z", first.reachedAt());
    }

    @Test
    void reachedAtIsTheMomentTheMilestoneCompletedNotNow() {
        QuitSmokingRecovery recovery = QuitSmokingMilestones.recovery(START, START.plus(30, ChronoUnit.HOURS));

        QuitSmokingMilestone twelveHours = recovery.milestones().stream()
                .filter(item -> item.key().equals("vascular-12h")).findFirst().orElseThrow();
        assertTrue(twelveHours.reached());
        assertEquals("2026-11-01T12:00:00Z", twelveHours.reachedAt());
        assertEquals(0, twelveHours.remainingMinutes());
    }

    @Test
    void definitionsAreOrderedByElapsedMinutes() {
        long previous = -1;
        Set<String> keys = new LinkedHashSet<>();
        for (QuitSmokingMilestones.Definition definition : QuitSmokingMilestones.DEFINITIONS) {
            // 允许同一时刻有多个维度同时达成（例如 72 小时的生理与肺部节点），
            // 但不能错序，否则「下一个节点」会取到更晚的那个。
            assertTrue(definition.atMinutes() >= previous,
                    "里程碑必须按时间升序声明，否则「下一个节点」会取错：" + definition.key());
            previous = definition.atMinutes();
            assertTrue(keys.add(definition.key()), "里程碑 key 不能重复：" + definition.key());
            assertFalse(definition.label().isBlank());
            assertFalse(definition.encouragement().isBlank());
            assertFalse(definition.atLabel().isBlank());
        }
    }

    @Test
    void coversTheFourRecoveryDimensions() {
        Set<String> categories = new LinkedHashSet<>();
        for (QuitSmokingMilestones.Definition definition : QuitSmokingMilestones.DEFINITIONS) {
            categories.add(definition.category());
        }

        assertEquals(Set.of(QuitSmokingMilestones.CATEGORY_PHYSIOLOGICAL,
                QuitSmokingMilestones.CATEGORY_PSYCHOLOGICAL,
                QuitSmokingMilestones.CATEGORY_VASCULAR,
                QuitSmokingMilestones.CATEGORY_LUNG), categories);
    }

    @Test
    void everythingIsReachedAfterFifteenYears() {
        QuitSmokingRecovery recovery = QuitSmokingMilestones.recovery(START, START.plus(15 * 366L, ChronoUnit.DAYS));

        assertEquals(recovery.totalCount(), recovery.reachedCount());
        assertEquals("", recovery.nextMilestoneKey());
        assertEquals(0, recovery.nextRemainingMinutes());
        assertEquals(1.0, recovery.nextProgress());
        assertTrue(recovery.encouragement().contains("所有里程碑"), recovery.encouragement());
    }

    @Test
    void futureStartIsTreatedAsZeroElapsed() {
        QuitSmokingRecovery recovery = QuitSmokingMilestones.recovery(START.plus(1, ChronoUnit.DAYS), START);

        assertEquals(0, recovery.elapsedMinutes());
        assertEquals(0, recovery.reachedCount());
    }

    @Test
    void progressNeverExceedsOne() {
        QuitSmokingRecovery recovery = QuitSmokingMilestones.recovery(START, START.plus(10, ChronoUnit.DAYS));

        assertTrue(recovery.milestones().stream().allMatch(item -> item.progress() >= 0 && item.progress() <= 1));
        QuitSmokingMilestone threeDays = recovery.milestones().stream()
                .filter(item -> item.key().equals("physiological-72h")).findFirst().orElseThrow();
        assertEquals(1.0, threeDays.progress());
    }
}
