package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskLifecycleIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void createsDefaultsReplaysCreateAndCompletesReopensAndSoftDeletes() {
        long userId = createUser("task-owner-");
        TaskService service = service(userId);

        assertEquals("INBOX", service.listLists().get(0).systemKey());
        assertEquals(1L, service.listLists().size());
        assertEquals(service.listLists().get(0).publicId(), service.settings().defaultListId());

        TaskCommand command = new TaskCommand(null, "整理收件箱", "第一项任务", "HIGH",
                null, "2026-10-10T10:00:00Z", false, "Asia/Shanghai", 30);
        TaskItem created = service.create(command, "create-1");
        TaskItem replayed = service.create(command, "create-1");
        assertEquals(created.publicId(), replayed.publicId());
        assertEquals(1L, service.listTasks(null, null, 0, 50).total());

        TaskItem completed = service.complete(created.publicId(), "1");
        assertEquals("COMPLETED", completed.status());
        assertThrows(ConflictException.class, () -> service.reopen(created.publicId(), "1"));
        TaskItem reopened = service.reopen(created.publicId(), "2");
        assertEquals("OPEN", reopened.status());
        assertEquals(3L, reopened.revision());

        assertTrue(service.delete(created.publicId(), "3").deleted());
        assertEquals(0L, service.listTasks(null, null, 0, 50).total());
        assertThrows(NotFoundException.class, () -> service.get(created.publicId()));
    }

    @Test
    void hidesTasksAndListsOwnedByAnotherUser() {
        long ownerId = createUser("task-owner-");
        long outsiderId = createUser("task-outsider-");
        TaskItem task = service(ownerId).create(new TaskCommand(null, "私有任务", null, null,
                null, null, null, null, null), "private-1");

        TaskService outsider = service(outsiderId);
        assertThrows(NotFoundException.class, () -> outsider.get(task.publicId()));
        assertThrows(NotFoundException.class, () -> outsider.create(new TaskCommand(task.listId(), "越权", null,
                null, null, null, null, null, null), "private-2"));
    }

    private TaskService service(long userId) {
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        return new TaskService(jdbc, currentUser, new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules()));
    }

    private long createUser(String prefix) {
        String username = prefix + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '任务测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }
}
