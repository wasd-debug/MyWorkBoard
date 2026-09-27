package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.ledger.LedgerScheduledTaskService;

import java.util.Set;

final class LedgerScheduleListTool implements DomainTool {
    private final LedgerScheduledTaskService schedules;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerScheduleListTool(LedgerScheduledTaskService schedules, ObjectMapper mapper) {
        this.schedules = schedules;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.booleanProperty(schema, "includeDeleted", "是否包含已删除任务");
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger.schedule.list", 1,
                "列出指定账本的周期任务、执行规则、下次执行日期、最近结果和 revision，不修改数据。",
                ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        boolean includeDeleted = Boolean.TRUE.equals(ToolInputs.optionalBoolean(input, "includeDeleted"));
        var values = schedules.list(bookId, includeDeleted);
        return ToolResult.completed("当前账本有 " + values.size() + " 个周期任务", mapper.valueToTree(values));
    }
}
