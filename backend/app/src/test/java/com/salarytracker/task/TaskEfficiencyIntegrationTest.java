package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.task.TaskEfficiencyModels.CountdownCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusFinishCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusStartCommand;
import com.salarytracker.task.TaskEfficiencyModels.HabitCheckinCommand;
import com.salarytracker.task.TaskEfficiencyModels.HabitCommand;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskEfficiencyIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void restoresOneRunningFocusAndKeepsWorktimeIsolated() {
        long userId = createUser();
        TaskService tasks = tasks(userId);
        String taskId = tasks.create(new TaskCommand(null, "专注测试", "", "NONE", null, null,
                false, "Asia/Shanghai", null), "task-focus").publicId();
        TaskEfficiencyService service = efficiency(userId);

        var started = service.startFocus(new FocusStartCommand(taskId, 25, "test", null), "focus-1");
        assertEquals(started.publicId(), service.currentFocus().publicId());
        assertThrows(ConflictException.class,
                () -> service.startFocus(new FocusStartCommand(null, 25, "test", null), "focus-2"));
        jdbc.update("UPDATE focus_session SET started_at=DATE_SUB(started_at, INTERVAL 5 MINUTE) WHERE public_id=?",
                started.publicId());
        var completed = service.finishFocus(started.publicId(), new FocusFinishCommand("COMPLETED"), "1");
        assertEquals("COMPLETED", completed.status());
        assertEquals(5, completed.actualMinutes());
        assertNull(service.currentFocus());
        assertEquals(5, jdbc.queryForObject("SELECT focus_minutes FROM task WHERE public_id=?", Integer.class, taskId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM work_record WHERE user_id=?", Integer.class, userId));
        assertThrows(ConflictException.class,
                () -> service.finishFocus(started.publicId(), new FocusFinishCommand("ABORTED"), "1"));
    }

    @Test
    void recalculatesHabitStreakAndIsolatesUsersAndCountdowns() {
        long owner = createUser();
        long outsider = createUser();
        TaskEfficiencyService service = efficiency(owner);
        LocalDate today = LocalDate.now();
        var habit = service.createHabit(new HabitCommand("阅读", "book", "#2f746f", "DAILY", 1,
                List.of(), null, today.minusDays(3).toString(), false, 0));
        service.checkin(habit.publicId(), new HabitCheckinCommand(today.minusDays(2).toString(), 1, "DONE"));
        service.checkin(habit.publicId(), new HabitCheckinCommand(today.minusDays(1).toString(), 0, "SKIP"));
        service.checkin(habit.publicId(), new HabitCheckinCommand(today.toString(), 1, "DONE"));
        service.checkin(habit.publicId(), new HabitCheckinCommand(today.toString(), 1, "DONE"));
        var stats = service.habitStats(habit.publicId(), null, null);
        assertEquals(2, stats.currentStreak());
        assertEquals(3, stats.checkins().size());
        assertThrows(RuntimeException.class, () -> efficiency(outsider).habitStats(habit.publicId(), null, null));

        var countdown = service.createCountdown(new CountdownCommand("纪念日", today.plusDays(10).toString(),
                "ANNIVERSARY", true, true, "#2f746f", ""));
        assertEquals(1, service.countdowns().size());
        assertEquals(0, efficiency(outsider).countdowns().size());
        assertThrows(ConflictException.class, () -> service.updateCountdown(countdown.publicId(),
                new CountdownCommand("改名", countdown.targetDate(), countdown.kind(), true, true, countdown.color(), ""), "0"));
    }

    private TaskEfficiencyService efficiency(long userId) {
        CurrentUserResolver current = mock(CurrentUserResolver.class);
        when(current.id()).thenReturn(userId);
        TaskChangeLog changeLog = new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules());
        return new TaskEfficiencyService(jdbc, current, new TaskService(jdbc, current, changeLog), changeLog);
    }

    private TaskService tasks(long userId) {
        CurrentUserResolver current = mock(CurrentUserResolver.class);
        when(current.id()).thenReturn(userId);
        return new TaskService(jdbc, current, new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules()));
    }

    private long createUser() {
        String username = "efficiency-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?,'!','效率测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
