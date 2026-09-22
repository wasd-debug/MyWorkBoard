package com.salarytracker.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ForbiddenException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DomainToolRegistry {
    private final CurrentUserResolver currentUserResolver;
    private final Map<String, DomainTool> tools;

    public DomainToolRegistry(CurrentUserResolver currentUserResolver, List<DomainTool> tools) {
        this.currentUserResolver = currentUserResolver;
        Map<String, DomainTool> registered = new LinkedHashMap<>();
        for (DomainTool tool : tools) {
            String name = tool.definition().name();
            if (registered.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("重复的领域工具名称: " + name);
            }
        }
        this.tools = Map.copyOf(registered);
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(DomainTool::definition)
                .sorted(java.util.Comparator.comparing(ToolDefinition::name)).toList();
    }

    public List<ToolDefinition> definitionsForCurrentUser() {
        CurrentUser user = currentUserResolver.required();
        return definitions().stream()
                .filter(tool -> user.authorities().containsAll(tool.requiredAuthorities()))
                .toList();
    }

    public ToolResult invoke(String name, JsonNode input) {
        DomainTool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("未知领域工具: " + name);
        CurrentUser user = currentUserResolver.required();
        if (!user.authorities().containsAll(tool.definition().requiredAuthorities())) {
            throw new ForbiddenException("没有执行该工具的权限");
        }
        JsonNode normalizedInput = ToolInputs.object(input);
        rejectUnknownFields(tool.definition(), normalizedInput);
        return tool.execute(normalizedInput);
    }

    private void rejectUnknownFields(ToolDefinition definition, JsonNode input) {
        JsonNode properties = definition.inputSchema().path("properties");
        input.fieldNames().forEachRemaining(field -> {
            if (!properties.has(field)) {
                throw new IllegalArgumentException(definition.name() + " 不支持输入字段: " + field);
            }
        });
    }
}
