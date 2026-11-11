package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.JdbcPendingActionRepository;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.approval.AgentApproval;
import com.salarytracker.ai.approval.AgentApprovalRepository;
import com.salarytracker.ai.approval.AgentApprovalService;
import com.salarytracker.ai.approval.JdbcAgentApprovalRepository;
import com.salarytracker.ai.approval.TaskApprovalExecutor;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.ai.tool.task.TaskR4ToolsConfiguration;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P4 最高风险工具（R4）：批量删除、删除清单、永久清除、导入确认。
 *
 * <p>四条硬约束：① prepare 一律不写业务数据；② 只能生成站内审批（commitAvailable=false，
 * confirmationUrl 指向 /approvals/{id}）；③ 批准后由 {@link TaskApprovalExecutor} 执行，
 * 不走模型；④ 快照里的版本对不上时必须失败而不是「尽力执行」。
 */
class TaskR4ToolsIntegrationTest extends MySqlIntegrationTestSupport {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TaskR4ToolsConfiguration tools = new TaskR4ToolsConfiguration();

    @Test
    void batchDeleteFreezesRevisionsAndExecutesOnlyAfterApproval() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        TaskApprovalExecutor executor = executor(userId, tasks);
        AgentApprovalService approvals = approvals(userId, actions, executor);
        CurrentUserResolver user = currentUser(userId);
        var first = create(tasks, "过期需求一");
        var second = create(tasks, "过期需求二");

        var prepared = tools.taskBatchDeletePrepareTool(tasks, actions, approvals, user, mapper).execute(
                mapper.createObjectNode().set("taskIds",
                        mapper.createArrayNode().add(first.publicId()).add(second.publicId())));
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("批量删除 2 个任务", prepared.summary());
        var content = prepared.structuredContent();
        assertEquals("task.batch.delete", content.path("actionType").asText());
        assertFalse(content.path("commitAvailable").asBoolean(true), "R4 不允许在对话里直接提交");
        assertTrue(content.path("webApprovalRequired").asBoolean(false));
        assertTrue(prepared.confirmationUrl().startsWith("/approvals/"), "必须跳站内审批中心");
        assertEquals("数量", content.path("previewRows").get(0).path("label").asText());
        assertEquals(2, content.path("stats").get(0).path("value").asInt());
        assertEquals(0, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId + " AND deleted=TRUE"), "prepare 不得删除");

        AgentApproval approval = approvals.list(null).get(0);
        assertEquals("PENDING", approval.status().name());
        assertEquals(approval.id(), content.path("approvalId").asText());
        assertEquals("批量删除 2 个任务", approval.summary());

        approvals.approveAndExecute(approval.id());
        assertEquals("COMPLETED", approvals.get(approval.id()).status().name());
        assertEquals(2, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId + " AND deleted=TRUE"));

        var replay = tools.taskBatchDeleteCommitTool(executor, mapper)
                .execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, replay.status());
        assertTrue(replay.structuredContent().path("replayed").asBoolean(false), "重复提交必须幂等");
    }

    @Test
    void batchDeleteFailsWhenTaskChangedAfterPreview() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        TaskApprovalExecutor executor = executor(userId, tasks);
        AgentApprovalService approvals = approvals(userId, actions, executor);
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "会被改动的任务");

        var prepared = tools.taskBatchDeletePrepareTool(tasks, actions, approvals, user, mapper).execute(
                mapper.createObjectNode().set("taskIds", mapper.createArrayNode().add(task.publicId())));
        tasks.update(task.publicId(), new TaskModels.TaskCommand(null, "改过标题", "", "NONE", null, null, false,
                "Asia/Shanghai", null, null, List.of(), List.of()), String.valueOf(task.revision()));

        String approvalId = prepared.structuredContent().path("approvalId").asText();
        approvals.approveAndExecute(approvalId);
        assertEquals("FAILED", approvals.get(approvalId).status().name(), "版本冲突时审批必须失败");
        assertEquals(1, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId + " AND deleted=FALSE"));
    }

    @Test
    void listDeleteCountsTasksAndRefusesInbox() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        TaskApprovalExecutor executor = executor(userId, tasks);
        AgentApprovalService approvals = approvals(userId, actions, executor);
        CurrentUserResolver user = currentUser(userId);
        var list = tasks.createList(new TaskModels.TaskListCommand("临时清单", "#3d8fce", null, 9L, false),
                "r4-list-" + UUID.randomUUID());
        createInList(tasks, "清单里的任务", list.publicId());

        var inbox = tasks.listLists().stream().filter(row -> "INBOX".equals(row.systemKey())).findFirst().orElseThrow();
        var refused = assertThrows(IllegalArgumentException.class,
                () -> tools.taskListDeletePrepareTool(tasks, actions, approvals, user, mapper)
                        .execute(mapper.createObjectNode().put("listId", inbox.publicId())));
        assertTrue(refused.getMessage().contains("收集箱"));

        var prepared = tools.taskListDeletePrepareTool(tasks, actions, approvals, user, mapper)
                .execute(mapper.createObjectNode().put("listId", list.publicId()));
        assertEquals("删除清单：临时清单", prepared.summary());
        var content = prepared.structuredContent();
        assertEquals("task.lists.delete", content.path("actionType").asText());
        assertEquals(1, content.path("preview").path("taskCount").asLong());
        assertEquals(1, content.path("stats").get(0).path("value").asInt());
        assertTrue(tasks.listLists().stream().anyMatch(row -> row.publicId().equals(list.publicId())), "prepare 不得删清单");

        String approvalId = content.path("approvalId").asText();
        approvals.approveAndExecute(approvalId);
        assertEquals("COMPLETED", approvals.get(approvalId).status().name());
        assertTrue(tasks.listLists().stream().noneMatch(row -> row.publicId().equals(list.publicId())));
        assertEquals(1, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId + " AND deleted=FALSE"), "清单内任务不跟着删");
    }

    @Test
    void purgeRequiresTrashedTaskAndMatchingConfirmation() {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        PendingActionService actions = actions();
        TaskApprovalExecutor executor = executor(userId, tasks);
        AgentApprovalService approvals = approvals(userId, actions, executor);
        CurrentUserResolver user = currentUser(userId);
        var task = create(tasks, "要彻底清掉的任务");

        var live = assertThrows(IllegalArgumentException.class,
                () -> tools.taskPurgePrepareTool(tasks, actions, approvals, user, mapper)
                        .execute(mapper.createObjectNode().put("taskId", task.publicId())));
        assertTrue(live.getMessage().contains("不在回收站"));

        tasks.delete(task.publicId(), String.valueOf(task.revision()));
        var wrong = assertThrows(IllegalArgumentException.class,
                () -> tools.taskPurgePrepareTool(tasks, actions, approvals, user, mapper)
                        .execute(mapper.createObjectNode().put("taskId", task.publicId()).put("confirmation", "DELETE:随便")));
        assertTrue(wrong.getMessage().contains("DELETE:"));

        var prepared = tools.taskPurgePrepareTool(tasks, actions, approvals, user, mapper)
                .execute(mapper.createObjectNode().put("taskId", task.publicId()));
        assertEquals("永久清除任务：要彻底清掉的任务", prepared.summary());
        var content = prepared.structuredContent();
        assertEquals("task.purge", content.path("actionType").asText());
        assertEquals("DELETE:" + task.publicId(), content.path("input").path("confirmation").asText());
        assertEquals(1, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId + " AND deleted=TRUE"), "prepare 不得清除");

        String approvalId = content.path("approvalId").asText();
        approvals.approveAndExecute(approvalId);
        assertEquals("COMPLETED", approvals.get(approvalId).status().name());
        assertEquals(0, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId), "永久清除后不留行");
    }

    @Test
    void importConfirmOnlyAcceptsExistingPreviewAndCommitsOnce() throws Exception {
        long userId = createUser();
        TaskService tasks = taskService(userId);
        TaskEnhancementService enhancement = enhancementService(userId, tasks);
        PendingActionService actions = actions();
        TaskApprovalExecutor executor = new TaskApprovalExecutor(tasks, enhancement, actions, currentUser(userId), mapper);
        AgentApprovalService approvals = approvals(userId, actions, executor);
        CurrentUserResolver user = currentUser(userId);

        var empty = assertThrows(IllegalArgumentException.class,
                () -> tools.taskImportConfirmPrepareTool(enhancement, actions, approvals, user, mapper)
                        .execute(mapper.createObjectNode()));
        assertTrue(empty.getMessage().contains("没有待确认的导入预览"));

        String previewId = UUID.randomUUID().toString();
        insertBatch(userId, previewId);
        var prepared = tools.taskImportConfirmPrepareTool(enhancement, actions, approvals, user, mapper)
                .execute(mapper.createObjectNode().put("previewId", previewId).put("skipDuplicates", false));
        assertEquals("导入任务：r4-import.csv", prepared.summary());
        var content = prepared.structuredContent();
        assertEquals("task.import.confirm", content.path("actionType").asText());
        assertEquals(1, content.path("preview").path("valid").asInt());
        assertEquals(0, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId), "prepare 不得写任务");

        String approvalId = content.path("approvalId").asText();
        approvals.approveAndExecute(approvalId);
        assertEquals("COMPLETED", approvals.get(approvalId).status().name());
        assertEquals(1, count("SELECT COUNT(*) FROM task WHERE user_id=" + userId + " AND title='导入进来的任务'"));

        var again = assertThrows(IllegalArgumentException.class,
                () -> tools.taskImportConfirmPrepareTool(enhancement, actions, approvals, user, mapper)
                        .execute(mapper.createObjectNode().put("previewId", previewId)));
        assertTrue(again.getMessage().contains("已经提交过"), "同一预览不能再建审批");
    }

    // ---------------------------------------------------------------- 辅助

    /** 直接插一条已解析好的导入批次，模拟用户刚在任务页上传完文件。 */
    private void insertBatch(long userId, String previewId) throws Exception {
        var row = new TaskEnhancementModels.ImportRow(2, "Sheet1", "导入进来的任务", "", "导入清单",
                null, "NONE", List.of(), "OPEN", "", "VALID", "", null, null, List.of());
        jdbc.update("INSERT INTO task_import_batch(public_id,user_id,file_name,rows_json,valid_count," +
                        "duplicate_count,error_count,expires_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP+INTERVAL 24 HOUR)",
                previewId, userId, "r4-import.csv", mapper.writeValueAsString(List.of(row)), 1, 0, 0);
    }

    private AgentApprovalService approvals(long userId, PendingActionService actions, TaskApprovalExecutor executor) {
        AgentApprovalRepository repository = new JdbcAgentApprovalRepository(jdbc);
        return new AgentApprovalService(repository, actions, List.of(executor), currentUser(userId), mapper);
    }

    private TaskApprovalExecutor executor(long userId, TaskService tasks) {
        return new TaskApprovalExecutor(tasks, enhancementService(userId, tasks), actions(), currentUser(userId), mapper);
    }

    private TaskService taskService(long userId) {
        return new TaskService(jdbc, currentUser(userId), new TaskChangeLog(jdbc, mapper));
    }

    private TaskEnhancementService enhancementService(long userId, TaskService tasks) {
        return new TaskEnhancementService(jdbc, currentUser(userId), tasks, mapper);
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
        return createInList(tasks, title, null);
    }

    private TaskModels.TaskItem createInList(TaskService tasks, String title, String listId) {
        return tasks.create(new TaskModels.TaskCommand(listId, title, "", "NONE", null, null, false,
                "Asia/Shanghai", null, null, List.of(), List.of()), "r4-" + UUID.randomUUID());
    }

    private int count(String statement) {
        Integer value = jdbc.queryForObject(statement, Integer.class);
        return value == null ? 0 : value;
    }

    private long createUser() {
        String username = "task-r4-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'R4 工具测试')", username);
        Long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
        assertNotNull(id);
        return id;
    }
}
