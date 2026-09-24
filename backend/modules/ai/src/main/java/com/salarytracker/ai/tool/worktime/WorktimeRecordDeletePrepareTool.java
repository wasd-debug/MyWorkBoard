package com.salarytracker.ai.tool.worktime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;
import com.salarytracker.worktime.WorktimeService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
public class WorktimeRecordDeletePrepareTool implements DomainTool {
    private final WorktimeService worktime;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public WorktimeRecordDeletePrepareTool(WorktimeService worktime, PendingActionService actions,
                                           CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.worktime = worktime;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.integerProperty(schema, "recordId", "要删除的工时记录 ID", 0, 1, Integer.MAX_VALUE);
        ToolSchemas.required(schema, "recordId");
        definition = new ToolDefinition("worktime.record.delete.prepare", 1,
                "读取当前用户的一条工时记录并生成完整删除影响预览；不写入数据。",
                ToolRisk.R3, Set.of("worktime:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        long recordId = input.path("recordId").asLong(0);
        if (recordId <= 0) throw new IllegalArgumentException("recordId 必须大于 0");
        WorkRecord record = worktime.record(recordId);
        ObjectNode snapshot = mapper.createObjectNode().put("recordId", recordId);
        PendingAction action = actions.prepare(currentUser.id(), definition, snapshot, record.revision(),
                false, Duration.ofMinutes(15));

        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "worktime.record.delete");
        content.set("input", snapshot);
        content.set("preview", mapper.valueToTree(record));
        content.putArray("effects")
                .add("该记录将从工时列表和统计中移除")
                .add("相关加班时间和实际时薪统计将按剩余记录重新展示")
                .add("删除成功后会刷新本地工时数据");
        return ToolResult.needsConfirmation("请确认删除这条工时记录", content,
                action.id(), action.expiresAt().toString());
    }
}
