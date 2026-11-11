package com.salarytracker.ai.tool.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.task.TaskService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 标签目录：按标签筛选或给任务打标签前先取真实 tagId，语义对齐 ledger.category.list。
 */
@Component
public class TaskTagsListTool implements DomainTool {
    private final TaskService tasks;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public TaskTagsListTool(TaskService tasks, ObjectMapper mapper) {
        this.tasks = tasks;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        definition = new ToolDefinition("task.tags.list", 1,
                "只读查询当前用户的全部任务标签（publicId、名称、父标签、颜色和 revision）。"
                        + "按标签筛选任务或给任务打标签前先调用本工具取得真实标签 ID 和名称，不得编造标签；"
                        + "不修改数据。",
                ToolRisk.R1, Set.of("task:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public ToolResult execute(JsonNode input) {
        var tags = tasks.listTags();
        return ToolResult.completed("当前有 " + tags.size() + " 个标签", mapper.valueToTree(tags));
    }
}
