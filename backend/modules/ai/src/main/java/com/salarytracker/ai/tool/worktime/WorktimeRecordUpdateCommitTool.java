package com.salarytracker.ai.tool.worktime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.worktime.WorktimeModels.RecordCommand;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;
import com.salarytracker.worktime.WorktimeService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class WorktimeRecordUpdateCommitTool implements DomainTool {
    private static final String PREPARE_TOOL = "worktime.record.update.prepare";
    private final WorktimeService worktime; private final PendingActionService actions;
    private final CurrentUserResolver currentUser; private final ObjectMapper mapper; private final ToolDefinition definition;

    public WorktimeRecordUpdateCommitTool(WorktimeService worktime, PendingActionService actions,
                                          CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.worktime = worktime; this.actions = actions; this.currentUser = currentUser; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("worktime.record.update.commit", 1,
                "按 prepare 保存的 revision 提交工时修改；一个 action 只能执行一次。",
                ToolRisk.R3, Set.of("worktime:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override @Transactional
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId"); long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!PREPARE_TOOL.equals(action.toolName()) || action.toolVersion() != 1) throw new IllegalArgumentException("action 与当前工具不匹配");
        actions.beginCommit(actionId, userId);
        try {
            JsonNode value = mapper.readTree(action.inputSnapshot());
            long recordId = value.path("recordId").asLong();
            RecordCommand command = new RecordCommand(value.path("date").asText(), value.path("start").asText(),
                    value.path("end").asText(), value.path("rest").asInt(), value.path("note").asText());
            WorkRecord result = worktime.updateRecord(recordId, command, String.valueOf(action.expectedRevision()));
            JsonNode content = mapper.valueToTree(result);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed("工时记录已修改", content);
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            ObjectNode content = mapper.createObjectNode().put("actionId", actionId).put("latestRevision", exception.getServerRevision());
            return ToolResult.conflict(exception.getMessage(), content, actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取 action 参数快照", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }
}
