package com.salarytracker.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface DomainTool {
    ToolDefinition definition();

    ToolResult execute(JsonNode input);
}
