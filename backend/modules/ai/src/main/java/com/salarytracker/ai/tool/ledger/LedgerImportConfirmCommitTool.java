package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.approval.LedgerImportApprovalExecutor;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;

import java.util.Set;

final class LedgerImportConfirmCommitTool implements DomainTool {
    private final LedgerImportApprovalExecutor executor;
    private final ToolDefinition definition;

    LedgerImportConfirmCommitTool(LedgerImportApprovalExecutor executor, ObjectMapper mapper) {
        this.executor = executor;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已由站内审批批准的 action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("ledger.import.confirm.commit", 1,
                "执行已通过站内审批的导入批次。", ToolRisk.R4, Set.of("ledger:import"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public ToolResult execute(JsonNode input) {
        return executor.commit(ToolInputs.requiredText(input, "actionId"));
    }
}
