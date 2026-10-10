package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskCalendarIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void listsOnlyCurrentUsersRangeAndReschedulesWithRevision() {
        TaskService owner = service(createUser("calendar-owner-"));
        TaskModels.TaskItem task = owner.create(new TaskModels.TaskCommand(null, "日历任务", null, null,
                null, "2026-10-10T10:00:00Z", false, "Asia/Shanghai", null), "calendar-create");
        TaskService outsider = service(createUser("calendar-outsider-"));

        assertEquals(1, owner.calendarItems(Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z")).size());
        assertEquals(0, outsider.calendarItems(Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z")).size());

        TaskModels.TaskItem changed = owner.reschedule(task.publicId(),
                new TaskModels.RescheduleCommand("2026-10-11T09:00:00Z", "2026-10-11T10:00:00Z", false, 60), "1");
        assertEquals("2026-10-11T10:00:00Z", changed.dueAt());
        assertEquals(2, changed.revision());
        assertThrows(ConflictException.class, () -> owner.reschedule(task.publicId(),
                new TaskModels.RescheduleCommand(null, "2026-10-12T10:00:00Z", false, null), "1"));
    }

    private TaskService service(long userId) {
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        return new TaskService(jdbc, currentUser, new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules()));
    }

    private long createUser(String prefix) {
        String username = prefix + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '日历测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }
}
