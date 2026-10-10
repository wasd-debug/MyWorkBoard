package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.task.TaskModels.ReminderCommand;
import com.salarytracker.task.TaskModels.TaskCommand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRecurrenceReminderIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void completionKeepsHistoryCreatesOneNextInstanceAndCancelsPendingReminder() {
        long userId = createUser();
        CurrentUserResolver current = current(userId);
        TaskService tasks = new TaskService(jdbc, current, new TaskChangeLog(jdbc,
                new ObjectMapper().findAndRegisterModules()));
        TaskReminderService reminders = new TaskReminderService(jdbc, current, new TaskInboxStream());
        var task = tasks.create(new TaskCommand(null, "每日复盘", "", "NONE", null,
                "2026-10-10T09:00:00Z", false, "UTC", null, null, null, null,
                "FREQ=DAILY;COUNT=3", "DUE_DATE"), "repeat-create");
        var reminder = reminders.create(task.publicId(), new ReminderCommand("RELATIVE", 30, null, "IN_APP", false));

        tasks.scanDueRecurrences();
        tasks.scanDueRecurrences();
        var completed = tasks.complete(task.publicId(), "1");
        assertEquals("COMPLETED", completed.status());
        var all = tasks.listTasks(null, null, "ALL", null, 0, 20);
        assertEquals(2, all.total());
        var next = all.items().stream().filter(item -> item.status().equals("OPEN")).findFirst().orElseThrow();
        assertEquals(task.seriesId(), next.seriesId());
        assertEquals(2, next.seriesSequence());
        assertNotEquals(task.publicId(), next.publicId());
        assertTrue(reminders.list(task.publicId()).isEmpty());
        assertEquals(1, reminders.list(next.publicId()).size());
        assertEquals(reminder.offsetMinutes(), reminders.list(next.publicId()).get(0).offsetMinutes());

        assertEquals(2, tasks.listTasks(null, null, "ALL", null, 0, 20).total());
    }

    @Test
    void reminderFireIsIdempotentAndUnreadCountBalances() {
        long userId = createUser();
        CurrentUserResolver current = current(userId);
        TaskService tasks = new TaskService(jdbc, current, new TaskChangeLog(jdbc,
                new ObjectMapper().findAndRegisterModules()));
        TaskReminderService reminders = new TaskReminderService(jdbc, current, new TaskInboxStream());
        var task = tasks.create(new TaskCommand(null, "缴费", null, null, null, null, null, null, null), "remind-create");
        var reminder = reminders.create(task.publicId(), new ReminderCommand("ABSOLUTE", null,
                "2026-10-10T00:00:00Z", "IN_APP", false));
        long id = jdbc.queryForObject("SELECT id FROM task_reminder WHERE public_id=?", Long.class, reminder.publicId());
        reminders.fire(id);
        reminders.fire(id);
        assertEquals(1, reminders.inbox(false, 0, 20).total());
        assertEquals(1, reminders.unread().unread());
        reminders.read(new TaskModels.InboxReadCommand(null, true));
        assertEquals(0, reminders.unread().unread());
        assertEquals(1, reminders.clearRead());
    }

    private CurrentUserResolver current(long userId) {
        CurrentUserResolver current = mock(CurrentUserResolver.class);
        when(current.id()).thenReturn(userId);
        return current;
    }

    private long createUser() {
        String username = "task-reminder-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '提醒测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
