package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.JdbcPendingActionRepository;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.ai.tool.task.TaskToolsConfiguration;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3 管理类工具：回收站恢复、批量改动、评论、习惯增改删、倒数日增改删、专注记录改删、任务设置。
 * 重点是「prepare 不写数据 + 前置校验拦在 prepare」，commit 只做已校验过的事。
 */
class TaskManageToolsIntegrationTest extends MySqlIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TaskToolsConfiguration tools = new TaskToolsConfiguration();

    @Test
    void restoreOnlyWorksForDeletedTasks() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "误删的任务");

        var live = assertThrows(RuntimeException.class, () -> tools.taskRestorePrepareTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("taskId", task.publicId())), "未删除的任务不能恢复");

        tasks.delete(task.publicId(), String.valueOf(task.revision()));
        var prepared = tools.taskRestorePrepareTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("taskId", task.publicId()));
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("恢复任务：误删的任务", prepared.summary());
        assertEquals("task.restore", prepared.structuredContent().path("actionType").asText());
        assertTrue(prepared.structuredContent().path("previewRows").size() >= 1, "卡片必须自带可读行");
        assertEquals("标题", prepared.structuredContent().path("previewRows").get(0).path("label").asText());
        assertEquals(1, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId + " AND deleted=TRUE"), "prepare 不得恢复");

        actions.approve(prepared.actionId(), userId);
        var committed = tools.taskRestoreCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, committed.status(), committed.summary());
        assertEquals(0, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId + " AND deleted=TRUE"));
        assertEquals("OPEN", statusOf(tasks, task.publicId()));
    }

    @Test
    void batchMoveRequiresKnownListAndMovesAll() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var first = create(tasks, "任务一");
        var second = create(tasks, "任务二");
        var target = tasks.createList(new TaskModels.TaskListCommand("客户项目", "#3d8fce", null, 1L, false),
                "list-" + UUID.randomUUID());

        var ids = mapper.createArrayNode().add(first.publicId()).add(second.publicId());
        var missingInput = mapper.createObjectNode();
        missingInput.set("taskIds", ids);
        missingInput.put("action", "MOVE");
        var missing = assertThrows(IllegalArgumentException.class, () -> tools.taskBatchMovePrepareTool(tasks, actions, user, mapper)
                .execute(missingInput));
        assertTrue(missing.getMessage().contains("listId"));

        var unknownInput = mapper.createObjectNode();
        unknownInput.set("taskIds", ids);
        unknownInput.put("action", "MOVE").put("listId", UUID.randomUUID().toString());
        var unknown = assertThrows(IllegalArgumentException.class, () -> tools.taskBatchMovePrepareTool(tasks, actions, user, mapper)
                .execute(unknownInput));
        assertTrue(unknown.getMessage().contains("清单不存在"));

        var moveInput = mapper.createObjectNode();
        moveInput.set("taskIds", ids);
        moveInput.put("action", "MOVE").put("listId", target.publicId());
        var prepared = tools.taskBatchMovePrepareTool(tasks, actions, user, mapper).execute(moveInput);
        assertEquals("2 个任务", prepared.structuredContent().path("preview").path("count").asText());
        assertEquals("客户项目", prepared.structuredContent().path("preview").path("target").asText());

        actions.approve(prepared.actionId(), userId);
        var committed = tools.taskBatchMoveCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, committed.status(), committed.summary());
        assertEquals(target.publicId(), tasks.get(first.publicId()).listId());
        assertEquals(target.publicId(), tasks.get(second.publicId()).listId());
    }

    @Test
    void batchRescheduleConvertsOffsetToUtc() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "改期任务");

        String dueAt = LocalDate.now(ZONE).plusDays(2).atTime(9, 30).atZone(ZONE).toOffsetDateTime().toString();
        var rescheduleInput = mapper.createObjectNode().put("action", "RESCHEDULE").put("dueAt", dueAt);
        rescheduleInput.putArray("taskIds").add(task.publicId());
        var prepared = tools.taskBatchMovePrepareTool(tasks, actions, user, mapper).execute(rescheduleInput);
        String expectedLabel = LocalDate.now(ZONE).plusDays(2).toString() + " 09:30";
        assertEquals(expectedLabel, prepared.structuredContent().path("preview").path("target").asText(),
                "预览按用户时区展示");

        actions.approve(prepared.actionId(), userId);
        tools.taskBatchMoveCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        String stored = tasks.get(task.publicId()).dueAt();
        assertTrue(stored.endsWith("Z"), "写库必须是 UTC：" + stored);
        assertEquals(java.time.OffsetDateTime.parse(dueAt).toInstant(), java.time.Instant.parse(stored));
    }

    @Test
    void batchTagsOnlyAddsAndRejectsUnknownTag() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "打标签任务");
        var tag = tasks.createTag(new TaskModels.TaskTagCommand(null, "紧急", "#e2685f", 0));

        var unknownTagInput = mapper.createObjectNode().put("action", "TAGS");
        unknownTagInput.putArray("taskIds").add(task.publicId());
        unknownTagInput.putArray("tagIds").add(UUID.randomUUID().toString());
        var unknown = assertThrows(IllegalArgumentException.class, () -> tools.taskBatchMovePrepareTool(tasks, actions, user, mapper)
                .execute(unknownTagInput));
        assertTrue(unknown.getMessage().contains("标签不存在"));

        var tagInput = mapper.createObjectNode().put("action", "TAGS");
        tagInput.putArray("taskIds").add(task.publicId());
        tagInput.putArray("tagIds").add(tag.publicId());
        var prepared = tools.taskBatchMovePrepareTool(tasks, actions, user, mapper).execute(tagInput);
        assertEquals("紧急", prepared.structuredContent().path("preview").path("target").asText());
        actions.approve(prepared.actionId(), userId);
        tools.taskBatchMoveCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(List.of(tag.publicId()), tasks.get(task.publicId()).tagIds());
    }

    @Test
    void commentCreateWritesBodyAndRejectsBlank() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        TaskCommentService comments = new TaskCommentService(jdbc, currentUser(userId), tasks);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "要写评论的任务");

        assertThrows(IllegalArgumentException.class, () -> tools.taskCommentCreatePrepareTool(tasks, comments, actions, user, mapper)
                .execute(mapper.createObjectNode().put("taskId", task.publicId()).put("bodyMarkdown", "   ")));

        var prepared = tools.taskCommentCreatePrepareTool(tasks, comments, actions, user, mapper)
                .execute(mapper.createObjectNode().put("taskId", task.publicId()).put("bodyMarkdown", "先确认接口契约"));
        assertEquals("给「要写评论的任务」写评论", prepared.summary());
        assertEquals("7 字", prepared.structuredContent().path("preview").path("length").asText());
        assertEquals(0, count("SELECT COUNT(*) FROM task_comment WHERE user_id=" + userId));

        actions.approve(prepared.actionId(), userId);
        var committed = tools.taskCommentCreateCommitTool(comments, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, committed.status(), committed.summary());
        assertEquals(1, count("SELECT COUNT(*) FROM task_comment WHERE user_id=" + userId + " AND deleted=FALSE"));
        assertEquals("先确认接口契约", committed.structuredContent().path("bodyMarkdown").asText());
    }

    @Test
    void habitCreateUpdateDeleteRoundTrip() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        TaskEfficiencyService efficiency = efficiencyService(userId, tasks);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);

        var custom = assertThrows(IllegalArgumentException.class, () -> tools.habitCreatePrepareTool(efficiency, tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("name", "晨跑").put("frequency", "CUSTOM")));
        assertTrue(custom.getMessage().contains("至少要选一天"));

        var createInput = mapper.createObjectNode().put("name", "晨跑").put("frequency", "WEEKLY_N")
                .put("targetCount", 3).put("remindAt", "07:00");
        var created = tools.habitCreatePrepareTool(efficiency, tasks, actions, user, mapper).execute(createInput);
        assertEquals("每周 3 次", created.structuredContent().path("preview").path("frequency").asText());
        actions.approve(created.actionId(), userId);
        var habit = tools.habitCreateCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", created.actionId()));
        String habitId = habit.structuredContent().path("publicId").asText();
        assertEquals(1, count("SELECT COUNT(*) FROM habit WHERE user_id=" + userId + " AND deleted_at IS NULL"));

        var noChange = assertThrows(IllegalArgumentException.class, () -> tools.habitUpdatePrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("habitId", habitId)));
        assertTrue(noChange.getMessage().contains("没有要修改的字段"));

        var update = tools.habitUpdatePrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("habitId", habitId).put("name", "晨跑 5 公里")
                        .put("frequency", "DAILY"));
        assertEquals(2, update.structuredContent().path("diff").size(), "名称与频率各一行差异");
        actions.approve(update.actionId(), userId);
        tools.habitUpdateCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", update.actionId()));
        assertEquals("晨跑 5 公里", efficiency.habits(false).get(0).name());
        assertEquals("DAILY", efficiency.habits(false).get(0).frequency());

        var remove = tools.habitDeletePrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("habitId", habitId));
        assertEquals("删除习惯：晨跑 5 公里", remove.summary());
        actions.approve(remove.actionId(), userId);
        tools.habitDeleteCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", remove.actionId()));
        assertTrue(efficiency.habits(false).isEmpty());
    }

    @Test
    void countdownCreateUpdateAndBuiltinGuards() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        TaskEfficiencyService efficiency = efficiencyService(userId, tasks);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);

        String target = LocalDate.now(ZONE).plusDays(30).toString();
        var created = tools.countdownCreatePrepareTool(efficiency, tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("title", "年假出发").put("targetDate", target)
                        .put("repeatRule", "NONE").put("preciseTime", "08:00"));
        assertEquals("新建倒数日：年假出发", created.summary());
        assertTrue(created.structuredContent().path("preview").path("targetDate").asText().contains("还有 30 天"),
                created.structuredContent().path("preview").path("targetDate").asText());
        actions.approve(created.actionId(), userId);
        var item = tools.countdownCreateCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", created.actionId()));
        String countdownId = item.structuredContent().path("publicId").asText();
        assertEquals("08:00", efficiency.countdowns().stream()
                .filter(row -> countdownId.equals(row.publicId())).findFirst().orElseThrow().preciseTime());

        var update = tools.countdownUpdatePrepareTool(efficiency, tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("countdownId", countdownId)
                        .put("repeatRule", "YEARLY"));
        assertEquals("每年", update.structuredContent().path("diff").get(0).path("after").asText());
        actions.approve(update.actionId(), userId);
        tools.countdownUpdateCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", update.actionId()));
        assertEquals("YEARLY", efficiency.countdowns().stream()
                .filter(row -> countdownId.equals(row.publicId())).findFirst().orElseThrow().repeatRule());

        var builtin = efficiency.countdowns().stream()
                .filter(row -> row.builtinKey() != null && !row.builtinKey().isBlank()).findFirst();
        assertFalse(builtin.isEmpty(), "内置倒数日由服务端补建，应该存在");
        var refused = assertThrows(IllegalArgumentException.class,
                () -> tools.countdownUpdatePrepareTool(efficiency, tasks, actions, user, mapper)
                        .execute(mapper.createObjectNode().put("countdownId", builtin.get().publicId())
                                .put("title", "改内置项")));
        assertTrue(refused.getMessage().contains("内置"));

        var remove = tools.countdownDeletePrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("countdownId", countdownId));
        actions.approve(remove.actionId(), userId);
        tools.countdownDeleteCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", remove.actionId()));
        assertTrue(efficiency.countdowns().stream().noneMatch(row -> countdownId.equals(row.publicId())));
    }

    @Test
    void focusUpdateChangesNoteAndUnlinksTarget() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        TaskEfficiencyService efficiency = efficiencyService(userId, tasks);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "写方案");
        var session = efficiency.startFocus(new TaskEfficiencyModels.FocusStartCommand(task.publicId(), null, 25,
                null, null, null, null, null), "focus-" + UUID.randomUUID());

        var prepared = tools.focusUpdatePrepareTool(efficiency, tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("sessionId", session.publicId()).put("note", "先搭骨架")
                        .put("unlink", true));
        assertEquals("修改专注记录：任务 写方案", prepared.summary());
        actions.approve(prepared.actionId(), userId);
        tools.focusUpdateCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        var updated = efficiency.getFocus(session.publicId());
        assertEquals("先搭骨架", updated.note());
        assertTrue(updated.taskId() == null || updated.taskId().isBlank(), "解除关联后不应再有 taskId");

        var running = assertThrows(IllegalArgumentException.class, () -> tools.focusDeletePrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("sessionId", session.publicId())));
        assertTrue(running.getMessage().contains("进行中"));

        var finish = tools.focusFinishPrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("status", "COMPLETED"));
        actions.approve(finish.actionId(), userId);
        tools.focusFinishCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", finish.actionId()));

        var remove = tools.focusDeletePrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("sessionId", session.publicId()));
        assertEquals("删除专注记录：自由专注", remove.summary());
        actions.approve(remove.actionId(), userId);
        tools.focusDeleteCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", remove.actionId()));
        assertEquals(0, count("SELECT COUNT(*) FROM focus_session WHERE user_id=" + userId));
    }

    @Test
    void settingsUpdateRejectsNoChangeAndAppliesDiff() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);

        var noChange = assertThrows(IllegalArgumentException.class, () -> tools.taskSettingsUpdatePrepareTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode()));
        assertTrue(noChange.getMessage().contains("没有要修改的字段"));

        int before = tasks.settings().weekStart();
        int after = before == 1 ? 0 : 1;
        Integer beforeReminder = tasks.settings().defaultReminderMinutes();
        int newReminder = beforeReminder != null && beforeReminder == 30 ? 45 : 30;
        var prepared = tools.taskSettingsUpdatePrepareTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("weekStart", after).put("defaultReminderMinutes", newReminder));
        var diff = prepared.structuredContent().path("diff");
        assertEquals(2, diff.size(), "两项改动各一行差异");
        assertEquals("默认提醒", diff.get(0).path("label").asText());
        assertEquals(newReminder + " 分钟", diff.get(0).path("after").asText());
        assertEquals("一周起始", diff.get(1).path("label").asText());
        assertEquals(before, tasks.settings().weekStart(), "prepare 不得写设置");

        actions.approve(prepared.actionId(), userId);
        var committed = tools.taskSettingsUpdateCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, committed.status(), committed.summary());
        assertEquals(after, tasks.settings().weekStart());
        assertEquals(Integer.valueOf(newReminder), tasks.settings().defaultReminderMinutes());
    }

    private TaskService taskService(long userId) {
        return new TaskService(jdbc, currentUser(userId), new TaskChangeLog(jdbc, mapper));
    }

    private TaskEfficiencyService efficiencyService(long userId, TaskService tasks) {
        return new TaskEfficiencyService(jdbc, currentUser(userId), tasks, new TaskChangeLog(jdbc, mapper));
    }

    private PendingActionService actions() {
        return new PendingActionService(new JdbcPendingActionRepository(jdbc), mapper, new InteractionPolicy());
    }

    private CurrentUserResolver currentUser(long userId) {
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(userId);
        return resolver;
    }

    private TaskModels.TaskItem create(TaskService tasks, String title) {
        return tasks.create(new TaskModels.TaskCommand(null, title, "", "NONE", null, null, false,
                "Asia/Shanghai", null, null, List.of(), List.of()), "manage-" + UUID.randomUUID());
    }

    private String statusOf(TaskService tasks, String publicId) {
        return tasks.get(publicId).status();
    }

    private int count(String statement) {
        Integer value = jdbc.queryForObject(statement, Integer.class);
        return value == null ? 0 : value;
    }

    private long createUser() {
        String username = "task-manage-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '管理工具测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
