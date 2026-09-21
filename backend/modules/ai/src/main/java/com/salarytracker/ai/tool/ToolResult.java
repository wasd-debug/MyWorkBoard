package com.salarytracker.ai.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(
        ToolStatus status,
        String summary,
        JsonNode structuredContent,
        String actionId,
        String confirmationUrl,
        String expiresAt,
        String traceId) {

    public static ToolResult completed(String summary, JsonNode structuredContent) {
        return new ToolResult(ToolStatus.COMPLETED, summary, structuredContent,
                null, null, null, null);
    }
}
