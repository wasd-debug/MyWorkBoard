package com.salarytracker.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

public record ToolDefinition(
        String name,
        int version,
        String description,
        ToolRisk riskLevel,
        Set<String> requiredAuthorities,
        JsonNode inputSchema) {

    public ToolDefinition {
        if (name == null || !name.matches("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*){1,4}")) {
            throw new IllegalArgumentException("工具名称必须使用稳定的点分格式");
        }
        if (version < 1) throw new IllegalArgumentException("工具版本必须大于 0");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("工具描述不能为空");
        if (riskLevel == null) throw new IllegalArgumentException("工具风险等级不能为空");
        requiredAuthorities = requiredAuthorities == null ? Set.of() : Set.copyOf(requiredAuthorities);
        if (inputSchema == null || !inputSchema.isObject()) {
            throw new IllegalArgumentException("工具输入 Schema 必须是 JSON object");
        }
    }
}
