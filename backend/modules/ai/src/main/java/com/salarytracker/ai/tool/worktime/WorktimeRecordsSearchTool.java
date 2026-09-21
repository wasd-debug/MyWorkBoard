package com.salarytracker.ai.tool.worktime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;
import com.salarytracker.worktime.WorktimeService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class WorktimeRecordsSearchTool implements DomainTool {
    private final WorktimeService worktimeService;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public WorktimeRecordsSearchTool(WorktimeService worktimeService, ObjectMapper mapper) {
        this.worktimeService = worktimeService;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "from", "起始日期，包含当天", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期，包含当天", "date");
        ToolSchemas.integerProperty(schema, "limit", "最多返回的记录数", 50, 1, 100);
        ToolSchemas.integerProperty(schema, "offset", "从第几条记录开始", 0, 0, 10000);
        this.definition = new ToolDefinition(
                "worktime.records.search",
                1,
                "按日期范围分页查询当前用户的工时记录，最多返回 100 条。",
                ToolRisk.R1,
                Set.of("worktime:read"),
                schema);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String from = ToolInputs.optionalDate(input, "from");
        String to = ToolInputs.optionalDate(input, "to");
        if (from != null && to != null && LocalDate.parse(to).isBefore(LocalDate.parse(from))) {
            throw new IllegalArgumentException("to 不能早于 from");
        }
        int limit = ToolInputs.integer(input, "limit", 50, 1, 100);
        int offset = ToolInputs.integer(input, "offset", 0, 0, 10000);
        List<WorkRecord> records = worktimeService.listRecords(from, to, limit, offset);
        ObjectNode content = mapper.createObjectNode();
        content.set("items", mapper.valueToTree(records));
        content.put("from", from);
        content.put("to", to);
        content.put("limit", limit);
        content.put("offset", offset);
        content.put("returned", records.size());
        return ToolResult.completed("已查询到 " + records.size() + " 条工时记录", content);
    }
}
