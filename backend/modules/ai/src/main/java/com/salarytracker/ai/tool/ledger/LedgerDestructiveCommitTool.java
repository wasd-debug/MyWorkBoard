package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.approval.LedgerDestructiveApprovalExecutor;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;

import java.util.Set;

final class LedgerDestructiveCommitTool implements DomainTool {
    private final LedgerDestructiveApprovalExecutor executor;
    private final ToolDefinition definition;

    LedgerDestructiveCommitTool(String name, String label, LedgerDestructiveApprovalExecutor executor,
                                ObjectMapper mapper) {
        this.executor = executor;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已由站内审批批准的 action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition(name, 1, "执行已通过站内审批的" + label + "操作。",
                ToolRisk.R4, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        return executor.commit(ToolInputs.requiredText(input, "actionId"));
    }
}
