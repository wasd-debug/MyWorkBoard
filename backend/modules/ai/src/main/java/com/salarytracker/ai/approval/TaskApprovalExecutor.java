package com.salarytracker.ai.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.task.TaskEnhancementModels;
import com.salarytracker.task.TaskEnhancementService;
import com.salarytracker.task.TaskModels;
import com.salarytracker.task.TaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 任务域 R4 执行器：只在站内审批中心批准后调用。
 *
 * <p>四个动作共用一套状态流转（beginCommit → 执行 → COMPLETED / CONFLICT / FAILED），
 * 失败原因必须写进 result，因为审批页只展示这里返回的内容。
 */
@Service
public class TaskApprovalExecutor implements AgentApprovalExecutor {
    private static final Set<String> SUPPORTED = Set.of(
            "task.batch.delete.prepare", "task.lists.delete.prepare",
            "task.purge.prepare", "task.import.confirm.prepare");

    private final TaskService tasks;
    private final TaskEnhancementService enhancement;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;

    public TaskApprovalExecutor(TaskService tasks, TaskEnhancementService enhancement, PendingActionService actions,
                                CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.tasks = tasks;
        this.enhancement = enhancement;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String toolName) {
        return SUPPORTED.contains(toolName);
    }

    @Override
    public ToolResult commit(String actionId) {
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!supports(action.toolName())) throw new IllegalArgumentException("action 不是任务域高风险审批操作");
        if (action.status() == ActionStatus.COMPLETED) {
            return ToolResult.completed("该审批已经执行过（幂等重放）", mapper.createObjectNode()
                    .put("actionId", actionId).put("replayed", true));
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode snapshot = mapper.readTree(action.inputSnapshot());
            ToolResult result = execute(action, snapshot);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return result;
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode()
                    .put("actionId", actionId).put("latestRevision", exception.getServerRevision()), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取审批参数快照", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }

    private ToolResult execute(PendingAction action, JsonNode snapshot) {
        return switch (action.toolName()) {
            case "task.batch.delete.prepare" -> batchDelete(snapshot);
            case "task.lists.delete.prepare" -> deleteList(snapshot);
            case "task.purge.prepare" -> purge(snapshot);
            case "task.import.confirm.prepare" -> importConfirm(snapshot);
            default -> throw new IllegalArgumentException("审批操作暂不支持执行");
        };
    }

    private ToolResult batchDelete(JsonNode snapshot) {
        JsonNode targets = snapshot.path("targets");
        if (!targets.isArray() || targets.isEmpty()) throw new IllegalArgumentException("审批快照里没有要删除的任务");
        List<TaskModels.BatchTarget> frozen = new ArrayList<>();
        for (JsonNode item : targets) {
            frozen.add(new TaskModels.BatchTarget(requiredText(item, "taskId"), item.path("revision").asLong(-1)));
        }
        if (frozen.stream().anyMatch(target -> target.revision() < 0)) {
            throw new IllegalArgumentException("审批快照缺少任务版本");
        }
        TaskModels.BatchResult result = tasks.batch(new TaskModels.BatchCommand(frozen, "DELETE",
                null, null, null, null, null));
        ObjectNode content = mapper.createObjectNode().put("affected", result.affected());
        ArrayNode ids = content.putArray("taskIds");
        frozen.forEach(target -> ids.add(target.publicId()));
        return ToolResult.completed("已删除 " + result.affected() + " 个任务", content);
    }

    private ToolResult deleteList(JsonNode snapshot) {
        String listId = requiredText(snapshot, "listId");
        long revision = snapshot.path("revision").asLong(-1);
        if (revision < 0) throw new IllegalArgumentException("审批快照缺少清单版本");
        TaskModels.DeletedResource deleted = tasks.deleteList(listId, String.valueOf(revision));
        ObjectNode content = mapper.createObjectNode().put("listId", listId)
                .put("name", snapshot.path("name").asText()).put("revision", deleted.revision());
        return ToolResult.completed("清单已删除", content);
    }

    private ToolResult purge(JsonNode snapshot) {
        String taskId = requiredText(snapshot, "taskId");
        String confirmation = snapshot.path("confirmation").asText("");
        if (!("DELETE:" + taskId).equals(confirmation)) confirmation = "DELETE:" + taskId;
        TaskModels.DeletedResource purged = tasks.purge(taskId, confirmation);
        ObjectNode content = mapper.createObjectNode().put("taskId", taskId)
                .put("title", snapshot.path("title").asText()).put("purged", purged.deleted());
        return ToolResult.completed("任务已永久清除", content);
    }

    private ToolResult importConfirm(JsonNode snapshot) {
        String previewId = requiredText(snapshot, "previewId");
        boolean skipDuplicates = !snapshot.has("skipDuplicates") || snapshot.path("skipDuplicates").asBoolean(true);
        TaskEnhancementModels.ImportResult result =
                enhancement.commit(new TaskEnhancementModels.ImportCommitCommand(previewId, skipDuplicates));
        ObjectNode content = mapper.createObjectNode().put("previewId", previewId)
                .put("created", result.created()).put("skipped", result.skipped()).put("replayed", result.replayed());
        return ToolResult.completed(result.replayed()
                ? "该导入已提交过，未重复写入"
                : "导入完成：新增 " + result.created() + " 条，跳过 " + result.skipped() + " 条", content);
    }

    private String requiredText(JsonNode value, String field) {
        String text = value.path(field).asText().trim();
        if (text.isEmpty()) throw new IllegalArgumentException(field + " 必填");
        return text;
    }
}
