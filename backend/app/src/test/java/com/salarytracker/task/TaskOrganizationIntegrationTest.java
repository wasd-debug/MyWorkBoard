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
    void batchesAtomicallyAndPersistsManualSort() {
        TaskService service = service(createUser());
        var targetList = service.createList(new TaskListCommand("批量目标", null, null, 1, false), "batch-list");
        var first = service.create(new TaskCommand(null, "第一项", null, null, null, null, null, null, null), "batch-first");
        var second = service.create(new TaskCommand(null, "第二项", null, null, null, null, null, null, null), "batch-second");
        var moved = service.batch(new TaskModels.BatchCommand(List.of(
                new TaskModels.BatchTarget(first.publicId(), first.revision()),
                new TaskModels.BatchTarget(second.publicId(), second.revision())), "MOVE", targetList.publicId(),
                null, null, null, null));
        assertEquals(2, moved.affected());
        assertTrue(moved.items().stream().allMatch(item -> targetList.publicId().equals(item.listId())));

        var sorted = service.sort(second.publicId(), new TaskModels.SortCommand(0),
                String.valueOf(service.get(second.publicId()).revision()));
        assertEquals(0, sorted.sortOrder());
        assertEquals(second.publicId(), service.listTasks(targetList.publicId(), null, "ALL", null, 0, 20).items().get(0).publicId());

        long firstRevision = service.get(first.publicId()).revision();
        assertThrows(ConflictException.class, () -> service.batch(new TaskModels.BatchCommand(List.of(
                new TaskModels.BatchTarget(first.publicId(), firstRevision),
                new TaskModels.BatchTarget(second.publicId(), 1)), "COMPLETE", null, null, null, null, null)));
        assertEquals("OPEN", service.get(first.publicId()).status());
    }

    @Test
    void groupsListsShowsTomorrowAndRecordsActivity() {
        long userId = createUser();
        TaskService service = service(userId);
        var group = service.createListGroup(new TaskModels.ListGroupCommand("工作", "#334155", "💼", 0));
        assertEquals("#334155", group.color());
        assertEquals("💼", group.icon());
        group = service.updateListGroup(group.publicId(), new TaskModels.ListGroupCommand("工作事项", "#2563eb", "📁", 1),
                String.valueOf(group.revision()));
        assertEquals("📁", group.icon());
        var list = service.createList(new TaskModels.TaskListCommand("项目", "#2563eb", "folder", 1, false), "list-group-create");
        var grouped = service.assignListGroup(list.publicId(), group.publicId(), 4_096L, String.valueOf(list.revision()));
        assertEquals(group.publicId(), grouped.groupId());
        assertEquals(4_096L, grouped.sortOrder());
        assertEquals(list.revision() + 1, grouped.revision());

        var tomorrow = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).plusDays(1)
                .atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toString();
        var task = service.create(new TaskModels.TaskCommand(list.publicId(), "明日任务", "", "NONE", null,
                tomorrow, false, java.time.ZoneId.systemDefault().getId(), null), "tomorrow-create");
        assertEquals(1, service.listTasks(null, null, "TOMORROW", null, 0, 20).total());
        service.update(task.publicId(), new TaskModels.TaskCommand(list.publicId(), "明日任务已编辑", "", "HIGH",
                null, tomorrow, false, java.time.ZoneId.systemDefault().getId(), null), String.valueOf(task.revision()));
        assertTrue(service.activity(task.publicId(), 0, 20).items().stream().anyMatch(item -> "CREATED".equals(item.eventType())));
        assertTrue(service.activity(task.publicId(), 0, 20).items().stream()
                .anyMatch(item -> "UPDATED".equals(item.eventType()) && item.detailJson().contains("priority")));

        service.deleteListGroup(group.publicId(), String.valueOf(group.revision()));
        assertEquals(null, service.listLists().stream().filter(item -> item.publicId().equals(list.publicId())).findFirst().orElseThrow().groupId());
    }

    @Test
    void persistsListAppearanceAndAccountScopedSidebarPreferences() {
        long userId = createUser();
        TaskService service = service(userId);
        var list = service.createList(new TaskModels.TaskListCommand("看板笔记", "#123456", "folder", 0,
                false, true, "KANBAN", "NOTE", false), "appearance-list");
        assertEquals("KANBAN", list.viewType());
        assertEquals("NOTE", list.objectType());
        assertTrue(list.pinned());
        assertTrue(!list.showInSmartLists());
        var group = service.createListGroup(new TaskModels.ListGroupCommand("置顶分组", null, "folder", 0, true));
        assertTrue(group.pinned());
        var tag = service.createTag(new TaskModels.TaskTagCommand(null, "带图标", null, 0, "tag"));
        assertEquals("tag", tag.icon());

        var before = service.sidebarPreferences();
        var updated = service.updateSidebarPreferences(new TaskModels.SidebarPreferencesCommand(List.of("TODAY"),
                List.of("INBOX"), List.of("today"), List.of(list.publicId()), List.of(), List.of(tag.publicId()), List.of(),
                List.of(), List.of(), java.util.Map.of("lists", true)), String.valueOf(before.revision()));
        assertEquals(List.of("TODAY"), updated.hiddenSystemViews());
        assertEquals(List.of(list.publicId()), updated.hiddenListIds());
        assertEquals(Boolean.TRUE, updated.collapsedSections().get("lists"));
        assertEquals(List.of("today"), updated.conditionalSystemViews());
        var systemViews = List.of("all", "today", "tomorrow", "next7", "assigned", "inbox", "summary",
                "this-week", "next-week", "no-date", "no-tag", "completed", "abandoned", "trash", "archived");
        var withSystemViews = service.updateSidebarPreferences(new TaskModels.SidebarPreferencesCommand(systemViews,
                List.of("inbox"), List.of("this-week", "no-date"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), java.util.Map.of()),
                String.valueOf(updated.revision()));
        assertEquals(systemViews, withSystemViews.hiddenSystemViews());
        assertEquals(List.of("inbox"), withSystemViews.pinnedSystemViews());
        assertEquals(List.of("this-week", "no-date"), withSystemViews.conditionalSystemViews());
        assertThrows(ConflictException.class, () -> service.updateSidebarPreferences(
                new TaskModels.SidebarPreferencesCommand(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), java.util.Map.of()), String.valueOf(before.revision())));
        assertThrows(IllegalArgumentException.class, () -> service.updateSidebarPreferences(
                new TaskModels.SidebarPreferencesCommand(List.of("NOT_A_VIEW"), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), java.util.Map.of()), String.valueOf(withSystemViews.revision())));
        assertThrows(IllegalArgumentException.class, () -> service.updateSidebarPreferences(
                new TaskModels.SidebarPreferencesCommand(List.of(), List.of(), List.of("summary"), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), java.util.Map.of()), String.valueOf(withSystemViews.revision())));
    }
    @Test
    void managesListsTagsSubtasksChecklistAndTrash() {
        TaskService service = service(createUser());
        var inbox = service.listLists().get(0);
        assertEquals("收集箱", inbox.name());
        assertThrows(ConflictException.class, () -> service.deleteList(inbox.publicId(), "1"));
        assertThrows(ConflictException.class, () -> service.updateList(inbox.publicId(),
                new TaskListCommand(null, null, null, null, true), String.valueOf(inbox.revision())));
        assertThrows(ConflictException.class, () -> service.updateList(inbox.publicId(),
                new TaskListCommand("改名收集箱", null, null, null, null), String.valueOf(inbox.revision())));
        var recoloredInbox = service.updateList(inbox.publicId(),
                new TaskListCommand(null, "#7259d9", null, null, null), String.valueOf(inbox.revision()));
        assertEquals("#7259d9", recoloredInbox.color());
        assertEquals(inbox.revision() + 1, recoloredInbox.revision());
        assertEquals("#7259d9", service.listLists().stream()
                .filter(item -> item.publicId().equals(inbox.publicId())).findFirst().orElseThrow().color());

        var sparse = service.createList(new TaskListCommand("稀疏排序", null, null, 3_000_000_000L,
                false, null, null, null, null), "sparse-list-rank");
        assertEquals(3_000_000_000L, sparse.sortOrder());
        var reorderedSparse = service.updateList(sparse.publicId(), new TaskListCommand(null, null, null,
                3_000_000_512L, null), String.valueOf(sparse.revision()));
        assertEquals(3_000_000_512L, reorderedSparse.sortOrder());

        var work = service.createList(new TaskListCommand("工作", "#2563eb", "briefcase", 1, false), "list-1");
        assertEquals(work.publicId(), service.createList(new TaskListCommand("重试", null, null, null, false), "list-1").publicId());
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

    @Test
    void mergesTagsAtomicallyAndKeepsTaskSyncStateConsistent() {
        long userId = createUser();
        TaskService service = service(userId);
        var source = service.createTag(new TaskTagCommand(null, "旧标签", "#16a34a", 0));
        var target = service.createTag(new TaskTagCommand(null, "目标标签", "#2563eb", 1));
        var child = service.createTag(new TaskTagCommand(source.publicId(), "子标签", null, 2));
        var task = service.create(new TaskCommand(null, "待迁移", "", "NONE", null, null, false,
                "Asia/Shanghai", null, null, List.of(source.publicId(), target.publicId()), List.of()), "tag-merge-task");

        var result = service.mergeTag(source.publicId(), target.publicId(), String.valueOf(source.revision()), target.revision());

        assertEquals(1, result.movedTasks());
        assertEquals(1, result.movedChildren());
        assertEquals(List.of(target.publicId()), service.get(task.publicId()).tagIds());
        assertEquals(target.publicId(), service.listTags().stream().filter(tag -> tag.publicId().equals(child.publicId()))
                .findFirst().orElseThrow().parentId());
        assertTrue(service.listTags().stream().noneMatch(tag -> tag.publicId().equals(source.publicId())));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM task_sync_oplog WHERE user_id=? AND entity_type='task-tag' " +
                "AND entity_id=? AND operation='DELETE'", Integer.class, userId, source.publicId()) > 0);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM task_sync_oplog WHERE user_id=? AND entity_id=? AND operation='UPSERT'",
                Integer.class, userId, task.publicId()) > 0);
    }

    @Test
    void rejectsStaleOrStructurallyInvalidTagMergeWithoutPartialChanges() {
        TaskService service = service(createUser());
        var source = service.createTag(new TaskTagCommand(null, "待合并", null, 0));
        var child = service.createTag(new TaskTagCommand(source.publicId(), "子级", null, 1));
        var root = service.createTag(new TaskTagCommand(null, "一级", null, 2));
        var target = service.createTag(new TaskTagCommand(root.publicId(), "二级", null, 3));
        assertThrows(ConflictException.class, () -> service.mergeTag(source.publicId(), target.publicId(),
                String.valueOf(source.revision() + 1), target.revision()));
        assertThrows(ConflictException.class, () -> service.mergeTag(source.publicId(), target.publicId(),
                String.valueOf(source.revision()), target.revision() + 1));
        assertThrows(IllegalArgumentException.class, () -> service.mergeTag(source.publicId(), target.publicId(),
                String.valueOf(source.revision()), target.revision()));
        assertTrue(service.listTags().stream().anyMatch(tag -> tag.publicId().equals(source.publicId())));
        assertEquals(source.publicId(), service.listTags().stream().filter(tag -> tag.publicId().equals(child.publicId()))
                .findFirst().orElseThrow().parentId());
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
