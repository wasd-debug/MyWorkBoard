package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskSettingsIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void settingsUseRevisionAndRejectAnotherUsersDefaultList() {
        long firstUser = createUser();
        long secondUser = createUser();
        AtomicLong activeUser = new AtomicLong(firstUser);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenAnswer(ignored -> activeUser.get());
        TaskService tasks = new TaskService(jdbc, resolver,
                new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules()));

        var first = tasks.settings();
        activeUser.set(secondUser);
        var second = tasks.settings();
        activeUser.set(firstUser);

        assertThrows(NotFoundException.class, () -> tasks.updateSettings(command(second.defaultListId()),
                Long.toString(first.revision())));
        var updated = tasks.updateSettings(command(first.defaultListId()), Long.toString(first.revision()));
        assertTrue(updated.revision() > first.revision());
        assertEquals(30, updated.defaultReminderMinutes());
        assertEquals("TODAY", updated.defaultDueDate());
        assertEquals("NEXT_HOUR", updated.defaultDueTime());
        assertTrue(updated.feedIncludeDescription());
        assertThrows(ConflictException.class, () -> tasks.updateSettings(command(first.defaultListId()),
                Long.toString(first.revision())));
    }

    @Test
    void savesQuickAddDateAndTimeDefaults() {
        long userId = createUser();
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(userId);
        TaskService tasks = new TaskService(jdbc, resolver,
                new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules()));
        var before = tasks.settings();
        var saved = tasks.updateSettings(new TaskModels.SettingsCommand(before.defaultListId(), "TODAY", 30,
                "NONE", "18:30", 1, true, true, true, true, true, true, true,
                "08:30", "22:00", "07:00", true, "Asia/Shanghai"), Long.toString(before.revision()));
        assertEquals("NONE", saved.defaultDueDate());
        assertEquals("18:30", saved.defaultDueTime());
    }

    private TaskModels.SettingsCommand command(String defaultListId) {
        return new TaskModels.SettingsCommand(defaultListId, "TODAY", 30, 1,
                true, true, true, true, true, true, true,
                "08:30", "22:00", "07:00", true, "Asia/Shanghai");
    }

    private long createUser() {
        String username = "task-settings-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '设置测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
