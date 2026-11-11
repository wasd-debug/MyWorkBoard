package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.action.JdbcPendingActionRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2 高频写工具：创建带检查项/提醒、批量完成、重开/放弃的状态校验、打卡四态、专注单例。
 */
class TaskWriteToolsIntegrationTest extends MySqlIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TaskToolsConfiguration tools = new TaskToolsConfiguration();

    @Test
    void createPrepareCarriesChecklistDurationAndReminder() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();

        var input = mapper.createObjectNode().put("title", "写季度复盘").put("durationMinutes", 90)
                .put("dueAt", LocalDate.now(ZONE).plusDays(1).atTime(15, 0).atZone(ZONE).toOffsetDateTime().toString());
        input.set("checklist", mapper.createArrayNode()
                .add(mapper.createObjectNode().put("title", "整理数据"))
                .add(mapper.createObjectNode().put("title", "写结论")));
        input.set("reminder", mapper.createObjectNode().put("kind", "RELATIVE").put("offsetMinutes", 30));

        var result = tools.taskCreatePrepareTool(tasks, actions, currentUser(userId), mapper).execute(input);
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, result.status(), "prepare 只能生成待确认");
        var preview = result.structuredContent().path("preview");
        assertEquals("2 个检查项", preview.path("checklistCount").asText());
        assertEquals("1 小时 30 分钟", preview.path("durationMinutes").asText());
        assertEquals("到期前 30 分钟", preview.path("reminder").asText());
        assertEquals("task.create", result.structuredContent().path("actionType").asText());
        assertEquals(0, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId), "prepare 不得写任务");
    }

    /**
     * 相对提醒依赖到期时间。prepare 阶段不拦，commit 时事务已被标记回滚，
     * 用户只会看到 500 UnexpectedRollbackException，所以这里锁住提前校验。
     */
    @Test
    void createPrepareRejectsRelativeReminderWithoutDueDate() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();

        var input = mapper.createObjectNode().put("title", "没有到期时间");
        input.set("reminder", mapper.createObjectNode().put("kind", "RELATIVE").put("offsetMinutes", 30));
        var error = assertThrows(IllegalArgumentException.class,
                () -> tools.taskCreatePrepareTool(tasks, actions, currentUser(userId), mapper).execute(input));
        assertTrue(error.getMessage().contains("到期时间"));

        var absolute = mapper.createObjectNode().put("title", "绝对提醒");
        absolute.set("reminder", mapper.createObjectNode().put("kind", "ABSOLUTE").put("remindAt", "2026-12-01T01:00:00Z"));
        var ok = tools.taskCreatePrepareTool(tasks, actions, currentUser(userId), mapper).execute(absolute);
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, ok.status(), "绝对提醒不需要到期时间");
    }

    @Test
    void createCommitPersistsChecklistAndReminder() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);

        var input = mapper.createObjectNode().put("title", "提交校验").put("durationMinutes", 45)
                .put("dueAt", LocalDate.now(ZONE).plusDays(1).atTime(15, 0).atZone(ZONE).toOffsetDateTime().toString());
        input.set("checklist", mapper.createArrayNode().add(mapper.createObjectNode().put("title", "整理数据")));
        input.set("reminder", mapper.createObjectNode().put("kind", "RELATIVE").put("offsetMinutes", 30));
        var prepared = tools.taskCreatePrepareTool(tasks, actions, user, mapper).execute(input);
        actions.approve(prepared.actionId(), userId);

        var committed = tools.taskCreateCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, committed.status(), committed.summary());
        String taskId = committed.structuredContent().path("publicId").asText();
        assertEquals(1, count("SELECT COUNT(*) FROM task_checklist_item c JOIN task t ON t.id=c.task_id WHERE t.public_id='" + taskId + "'"));
        assertEquals(1, count("SELECT COUNT(*) FROM task_reminder r JOIN task t ON t.id=r.task_id WHERE t.public_id='" + taskId + "' AND r.cancelled_at IS NULL"));
        assertEquals(45, count("SELECT duration_minutes FROM task WHERE public_id='" + taskId + "'"));
    }

    @Test
    void batchCompleteSkipsAlreadyFinishedTasks() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var open = create(tasks, "未完成项");
        var done = create(tasks, "已完成项");
        tasks.complete(done.publicId(), String.valueOf(done.revision()));

        var input = mapper.createObjectNode();
        input.set("taskIds", mapper.createArrayNode().add(open.publicId()).add(done.publicId()));
        var prepared = tools.taskBatchCompletePrepareTool(tasks, actions, user, mapper).execute(input);
        assertEquals("1 个任务", prepared.structuredContent().path("preview").path("count").asText(),
                "已完成的任务要跳过，避免重复生成下一实例");
        actions.approve(prepared.actionId(), userId);
        var committed = tools.taskBatchCompleteCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, committed.status());
        assertEquals("COMPLETED", statusOf(tasks, open.publicId()));

        var allDone = mapper.createObjectNode();
        allDone.set("taskIds", mapper.createArrayNode().add(done.publicId()));
        var error = assertThrows(IllegalArgumentException.class,
                () -> tools.taskBatchCompletePrepareTool(tasks, actions, user, mapper).execute(allDone));
        assertTrue(error.getMessage().contains("都已完成"));
    }

    @Test
    void reopenRejectsOpenTaskAndWorksForCompleted() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "待重开");

        var openError = assertThrows(IllegalArgumentException.class,
                () -> tools.taskReopenPrepareTool(tasks, actions, user, mapper)
                        .execute(mapper.createObjectNode().put("taskId", task.publicId())));
        assertTrue(openError.getMessage().contains("不需要重开"));

        tasks.complete(task.publicId(), String.valueOf(task.revision()));
        var prepared = tools.taskReopenPrepareTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("taskId", task.publicId()));
        assertTrue(prepared.summary().startsWith("重开任务：待重开"), prepared.summary());
        assertEquals("已完成", prepared.structuredContent().path("preview").path("status").asText());
        actions.approve(prepared.actionId(), userId);
        tools.taskReopenCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals("OPEN", statusOf(tasks, task.publicId()));
    }

    @Test
    void abandonMarksTaskAndRejectsSecondAbandon() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "不做了");

        var prepared = tools.taskAbandonPrepareTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("taskId", task.publicId()).put("reason", "优先级调整"));
        assertEquals("优先级调整", prepared.structuredContent().path("preview").path("reason").asText());
        actions.approve(prepared.actionId(), userId);
        tools.taskAbandonCommitTool(tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals("ABANDONED", statusOf(tasks, task.publicId()));

        assertThrows(IllegalArgumentException.class,
                () -> tools.taskAbandonPrepareTool(tasks, actions, user, mapper)
                        .execute(mapper.createObjectNode().put("taskId", task.publicId())));
    }

    @Test
    void habitCheckinPreviewExplainsFourStates() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        TaskEfficiencyService efficiency = efficiencyService(userId, tasks);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var habit = efficiency.createHabit(new TaskEfficiencyModels.HabitCommand("喝水", "💧", "#6fb3e0", "DAILY",
                1, List.of(), null, LocalDate.now(ZONE).minusDays(5).toString(), false, 0, "other", null,
                null, null, null, null, null, null, null, null, null));

        var skip = tools.habitCheckinPrepareTool(efficiency, tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("habitId", habit.publicId()).put("status", "SKIP").put("skipReason", "出差"));
        assertEquals("跳过（不断连击）", skip.structuredContent().path("preview").path("status").asText());
        assertEquals("出差", skip.structuredContent().path("preview").path("skipReason").asText());

        var clear = tools.habitCheckinPrepareTool(efficiency, tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("habitId", habit.publicId()).put("status", "CLEAR"));
        assertEquals("取消打卡", clear.structuredContent().path("preview").path("status").asText());
        assertTrue(clear.summary().contains("取消打卡"), "取消打卡必须用 CLEAR 语义，不能让模型去删除");

        var future = assertThrows(IllegalArgumentException.class,
                () -> tools.habitCheckinPrepareTool(efficiency, tasks, actions, user, mapper)
                        .execute(mapper.createObjectNode().put("habitId", habit.publicId())
                                .put("date", LocalDate.now(ZONE).plusDays(1).toString())));
        assertTrue(future.getMessage().contains("未来"));

        var tooEarly = assertThrows(IllegalArgumentException.class,
                () -> tools.habitCheckinPrepareTool(efficiency, tasks, actions, user, mapper)
                        .execute(mapper.createObjectNode().put("habitId", habit.publicId())
                                .put("date", LocalDate.now(ZONE).minusDays(30).toString())));
        assertTrue(tooEarly.getMessage().contains("开始日期"));
    }

    @Test
    void habitCheckinCommitWritesStatus() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        TaskEfficiencyService efficiency = efficiencyService(userId, tasks);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);
        var habit = efficiency.createHabit(new TaskEfficiencyModels.HabitCommand("早睡", null, null, "DAILY",
                1, List.of(), null, LocalDate.now(ZONE).minusDays(3).toString(), false, 0, "other", null,
                null, null, null, null, null, null, null, null, null));

        var prepared = tools.habitCheckinPrepareTool(efficiency, tasks, actions, user, mapper)
                .execute(mapper.createObjectNode().put("habitId", habit.publicId()).put("status", "MISS").put("note", "熬夜了"));
        actions.approve(prepared.actionId(), userId);
        var committed = tools.habitCheckinCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, committed.status());
        assertEquals("MISS", committed.structuredContent().path("status").asText());
        assertEquals("熬夜了", committed.structuredContent().path("note").asText());
    }

    @Test
    void focusToolsEnforceSingleRunningSession() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        TaskEfficiencyService efficiency = efficiencyService(userId, tasks);
        PendingActionService actions = actions();
        CurrentUserResolver user = currentUser(userId);

        assertThrows(IllegalArgumentException.class, () -> tools.focusFinishPrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode()), "没有进行中的专注时不能结束");

        var started = tools.focusStartPrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("plannedMinutes", 25).put("note", "写周报"));
        assertEquals("开始专注：自由专注", started.summary());
        actions.approve(started.actionId(), userId);
        var session = tools.focusStartCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", started.actionId()));
        assertEquals("RUNNING", session.structuredContent().path("status").asText());

        var conflict = assertThrows(IllegalArgumentException.class, () -> tools.focusStartPrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("plannedMinutes", 25)));
        assertTrue(conflict.getMessage().contains("进行中的专注"));

        var finish = tools.focusFinishPrepareTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("status", "ABORTED"));
        actions.approve(finish.actionId(), userId);
        var finished = tools.focusFinishCommitTool(efficiency, actions, user, mapper)
                .execute(mapper.createObjectNode().put("actionId", finish.actionId()));
        assertEquals("ABORTED", finished.structuredContent().path("status").asText());
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
                "Asia/Shanghai", null, null, List.of(), List.of()), "write-" + UUID.randomUUID());
    }

    private String statusOf(TaskService tasks, String publicId) {
        return tasks.get(publicId).status();
    }

    private int count(String statement) {
        Integer value = jdbc.queryForObject(statement, Integer.class);
        return value == null ? 0 : value;
    }

    private long createUser() {
        String username = "task-write-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '写工具测试')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
