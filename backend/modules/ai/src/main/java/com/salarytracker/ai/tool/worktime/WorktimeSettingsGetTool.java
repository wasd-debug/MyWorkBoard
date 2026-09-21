package com.salarytracker.ai.tool.worktime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.worktime.WorktimeService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class WorktimeSettingsGetTool implements DomainTool {
    private final WorktimeService worktimeService;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public WorktimeSettingsGetTool(WorktimeService worktimeService, ObjectMapper mapper) {
        this.worktimeService = worktimeService;
        this.mapper = mapper;
        this.definition = new ToolDefinition(
                "worktime.settings.get",
                1,
                "读取当前用户的工时与薪资计算设置，不修改任何数据。",
                ToolRisk.R1,
                Set.of("worktime:read"),
                ToolSchemas.object(mapper));
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        return ToolResult.completed("已读取当前工时设置", mapper.valueToTree(worktimeService.readSettings()));
    }
}
