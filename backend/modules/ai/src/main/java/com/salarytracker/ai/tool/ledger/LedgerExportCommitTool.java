package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerImportService;

import java.time.LocalDate;
import java.util.Set;

final class LedgerExportCommitTool implements DomainTool {
    private final LedgerImportService imports;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerExportCommitTool(LedgerImportService imports, PendingActionService actions,
                           CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.imports = imports; this.actions = actions; this.currentUser = currentUser; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("ledger.export.commit", 1,
                "校验并生成已批准的账本导出文件元数据；浏览器随后通过受认证接口下载。",
                ToolRisk.R2, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!"ledger.export.prepare".equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与导出工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode values = mapper.readTree(action.inputSnapshot());
            String bookId = values.path("bookId").asText();
            String format = values.path("format").asText("xlsx");
            String from = optional(values, "from"); String to = optional(values, "to");
            byte[] bytes = imports.export(bookId, format, from, to);
            ObjectNode result = mapper.createObjectNode().put("bookId", bookId).put("format", format)
                    .put("filename", "ledger-" + LocalDate.now() + "." + format).put("byteSize", bytes.length);
            if (from != null) result.put("from", from); if (to != null) result.put("to", to);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed("导出文件已准备，共 " + bytes.length + " 字节", result);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法生成导出文件", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }

    private String optional(JsonNode values, String field) {
        JsonNode value = values.get(field); return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
