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

    public static ToolResult needsInput(String summary, JsonNode structuredContent,
                                        String actionId, String expiresAt) {
        return new ToolResult(ToolStatus.NEEDS_INPUT, summary, structuredContent,
                actionId, null, expiresAt, null);
    }

    public static ToolResult needsConfirmation(String summary, JsonNode structuredContent,
                                               String actionId, String expiresAt) {
        return new ToolResult(ToolStatus.NEEDS_CONFIRMATION, summary, structuredContent,
                actionId, null, expiresAt, null);
    }

    public static ToolResult conflict(String summary, JsonNode structuredContent, String actionId) {
        return new ToolResult(ToolStatus.CONFLICT, summary, structuredContent,
                actionId, null, null, null);
    }

    public static ToolResult failed(String summary, JsonNode structuredContent, String actionId) {
        return new ToolResult(ToolStatus.FAILED, summary, structuredContent,
                actionId, null, null, null);
    }
}
