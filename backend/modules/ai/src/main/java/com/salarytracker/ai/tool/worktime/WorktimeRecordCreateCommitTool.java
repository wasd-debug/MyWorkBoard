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
public class WorktimeRecordCreateCommitTool implements DomainTool {
    private static final String PREPARE_TOOL = "worktime.record.create.prepare";

    private final WorktimeService worktime;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public WorktimeRecordCreateCommitTool(WorktimeService worktime, PendingActionService actions,
                                          CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.worktime = worktime;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("worktime.record.create.commit", 1,
                "提交已批准的新增工时 action；一个 action 只能进入一次执行。",
                ToolRisk.R2, Set.of("worktime:write"), schema);
    }

    @Override
    public ToolDefinition definition() { return definition; }

    @Override
    @Transactional
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!PREPARE_TOOL.equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与当前工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            RecordCommand command = mapper.readValue(action.inputSnapshot(), RecordCommand.class);
            WorkRecord record = worktime.createRecord(command, actionId);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed("工时记录已保存", mapper.valueToTree(record));
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            ObjectNode content = mapper.createObjectNode();
            content.put("actionId", actionId);
            return ToolResult.conflict(exception.getMessage(), content, actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            ObjectNode content = mapper.createObjectNode();
            content.put("actionId", actionId);
            return ToolResult.failed(exception.getMessage(), content, actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            ObjectNode content = mapper.createObjectNode();
            content.put("actionId", actionId);
            return ToolResult.failed("无法读取 action 参数快照", content, actionId);
        }
    }
}
