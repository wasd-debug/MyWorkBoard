package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.task.TaskModels.ChecklistCommand;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskListCommand;
import com.salarytracker.task.TaskModels.TaskTagCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskOrganizationIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void managesListsTagsSubtasksChecklistAndTrash() {
        TaskService service = service(createUser());
        var inbox = service.listLists().get(0);
        assertThrows(ConflictException.class, () -> service.deleteList(inbox.publicId(), "1"));

        var work = service.createList(new TaskListCommand("工作", "#2563eb", "briefcase", 1, false), "list-1");
        assertEquals(work.publicId(), service.createList(new TaskListCommand("重试", null, null, null, false), "list-1").publicId());
        assertThrows(ConflictException.class, () -> service.updateList(inbox.publicId(),
                new TaskListCommand(null, null, null, null, true), "1"));
        var tag = service.createTag(new TaskTagCommand(null, "项目A", "#16a34a", 0));
        assertThrows(ConflictException.class, () -> service.createTag(new TaskTagCommand(null, "项目a", null, 1)));

        var parent = service.create(new TaskCommand(work.publicId(), "父任务", "", "HIGH", null,
                "2026-10-10T12:00:00Z", false, "Asia/Shanghai", null, null, List.of(tag.publicId()),
                List.of(new ChecklistCommand(null, "确认范围", false, 0))), "parent");
        var child = service.create(new TaskCommand(work.publicId(), "子任务", "", "NONE", null,
                null, false, "Asia/Shanghai", null, parent.publicId(), List.of(), List.of()), "child");
        var grandchild = service.create(new TaskCommand(work.publicId(), "孙任务", "", "NONE", null,
                null, false, "Asia/Shanghai", null, child.publicId(), List.of(), List.of()), "grandchild");
        assertThrows(IllegalArgumentException.class, () -> service.create(new TaskCommand(work.publicId(), "过深", "",
                "NONE", null, null, false, "Asia/Shanghai", null, grandchild.publicId(),
                List.of(), List.of()), "too-deep"));
        assertThrows(IllegalArgumentException.class, () -> service.update(parent.publicId(),
                new TaskCommand(work.publicId(), "循环", "", "NONE", null, null, false, "Asia/Shanghai", null,
                        grandchild.publicId(), List.of(), List.of()), "1"));
        assertThrows(IllegalArgumentException.class, () -> service.update(child.publicId(),
                new TaskCommand(work.publicId(), "移动过深", "", "NONE", null, null, false, "Asia/Shanghai", null,
                        grandchild.publicId(), List.of(), List.of()), "1"));

        var hydrated = service.get(parent.publicId());
        assertEquals(List.of(tag.publicId()), hydrated.tagIds());
        assertEquals(1, hydrated.checklist().size());
        assertEquals(1, hydrated.totalSubtasks());
        assertEquals(1, service.listTasks(null, null, "TODAY", null, 0, 20).total());

        var movedList = service.createList(new TaskListCommand("移动目标", null, null, 2, false), "move-list");
        var moved = service.update(parent.publicId(), new TaskCommand(movedList.publicId(), null, null, null,
                null, null, null, null, null), String.valueOf(hydrated.revision()));
        assertEquals(movedList.publicId(), service.get(grandchild.publicId()).listId());
        hydrated = service.update(parent.publicId(), new TaskCommand(work.publicId(), null, null, null,
                null, null, null, null, null), String.valueOf(moved.revision()));

        service.delete(parent.publicId(), String.valueOf(hydrated.revision()));
        var trash = service.listTasks(null, null, "TRASH", null, 0, 20);
        assertEquals(3, trash.total());
        var deletedParent = trash.items().stream().filter(item -> item.publicId().equals(parent.publicId())).findFirst().orElseThrow();
        var restored = service.restore(parent.publicId(), String.valueOf(deletedParent.revision()));
        assertTrue(!restored.deleted());
        assertEquals(3, service.listTasks(null, null, "ALL", null, 0, 20).total());
        assertEquals(0, service.listTasks(null, null, "TRASH", null, 0, 20).total());

        var alreadyDeleted = service.delete(child.publicId(), String.valueOf(service.get(child.publicId()).revision()));
        service.deleteList(work.publicId(), String.valueOf(work.revision()));
        assertEquals(1, service.listDeletedLists().size());
        assertEquals(0, service.listTasks(null, null, "ALL", null, 0, 20).total());
        var restoredList = service.restoreList(work.publicId(), "2");
        assertEquals("工作", restoredList.name());
        assertEquals(1, service.listTasks(null, null, "ALL", null, 0, 20).total());
        assertEquals(2, service.listTasks(null, null, "TRASH", null, 0, 20).total());

        assertThrows(IllegalArgumentException.class,
                () -> service.purge(child.publicId(), "DELETE:wrong"));
        var purged = service.purge(child.publicId(), "DELETE:" + child.publicId());
        assertEquals(alreadyDeleted.revision(), purged.revision());
        assertEquals(1, service.listTasks(null, null, "TRASH", null, 0, 20).total());
    }

    @Test
    void acceptsExpandedTextAndRejectsForeignChecklistIds() {
        TaskService alice = service(createUser());
        TaskService bob = service(createUser());
        var original = alice.create(new TaskCommand(null, "长".repeat(500), "描".repeat(20000), "NONE", null,
                null, false, "Asia/Shanghai", null, null, List.of(),
                List.of(new ChecklistCommand(null, "私有检查项", false, 0))), "long-text");
        assertEquals(20000, alice.get(original.publicId()).description().length());
        assertThrows(IllegalArgumentException.class, () -> bob.create(new TaskCommand(null, "越权", "", "NONE", null,
                null, false, "Asia/Shanghai", null, null, List.of(),
                List.of(new ChecklistCommand(original.checklist().get(0).publicId(), "改写", true, 0))), "foreign-item"));
        assertEquals("私有检查项", alice.get(original.publicId()).checklist().get(0).title());
    }

    @Test
    void restoringTreeDoesNotResurrectAnEarlierIndividuallyDeletedChild() {
        TaskService service = service(createUser());
        var parent = service.create(new TaskCommand(null, "根", null, null, null, null, null, null, null), "tree-root");
        var child = service.create(new TaskCommand(null, "子", null, null, null, null, null, null, null,
                parent.publicId(), List.of(), List.of()), "tree-child");
        service.delete(child.publicId(), "1");
        var deleted = service.delete(parent.publicId(), "1");
        service.restore(parent.publicId(), String.valueOf(deleted.revision()));
        assertEquals(1, service.listTasks(null, null, "ALL", null, 0, 20).total());
        assertEquals(child.publicId(), service.listTasks(null, null, "TRASH", null, 0, 20).items().get(0).publicId());
    }

    private TaskService service(long userId) {
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        return new TaskService(jdbc, currentUser, new TaskChangeLog(jdbc, new ObjectMapper().findAndRegisterModules()));
    }

    private long createUser() {
        String username = "task-org-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '组织测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
