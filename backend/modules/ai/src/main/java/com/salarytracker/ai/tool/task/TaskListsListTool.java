package com.salarytracker.ai.tool.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.task.TaskService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 清单目录：创建或筛选任务前必须先取真实 listId，语义对齐 ledger.books.list。
 */
@Component
public class TaskListsListTool implements DomainTool {
    private final TaskService tasks;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public TaskListsListTool(TaskService tasks, ObjectMapper mapper) {
        this.tasks = tasks;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.booleanProperty(schema, "includeArchived", "是否包含已归档清单");
        definition = new ToolDefinition("task.lists.list", 1,
                "只读查询当前用户的清单与清单分组（publicId、名称、类型、是否归档、排序和 revision）。"
                        + "创建、修改或筛选任务前先调用本工具取得真实清单 ID，不得编造清单名或 UUID；"
                        + "systemKey 非空的清单是系统清单（收集箱、今天等），不要重命名或删除。不修改数据，默认排除已归档清单。",
                ToolRisk.R1, Set.of("task:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public ToolResult execute(JsonNode input) {
        boolean includeArchived = Boolean.TRUE.equals(ToolInputs.optionalBoolean(input, "includeArchived"));
        List<?> lists = tasks.listLists().stream()
                .filter(list -> includeArchived || !list.archived())
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lists", lists);
        payload.put("groups", tasks.listGroups());
        return ToolResult.completed("当前有 " + lists.size() + " 个清单", mapper.valueToTree(payload));
    }
}
