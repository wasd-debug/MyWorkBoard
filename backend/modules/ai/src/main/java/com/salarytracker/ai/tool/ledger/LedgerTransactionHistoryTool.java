package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.ledger.LedgerTransactionService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LedgerTransactionHistoryTool implements DomainTool {
    private final LedgerTransactionService transactions;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionHistoryTool(LedgerTransactionService transactions, ObjectMapper mapper) {
        this.transactions = transactions; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "transactionId", "流水公开 ID", null);
        ToolSchemas.required(schema, "bookId", "transactionId");
        definition = new ToolDefinition("ledger.transaction.history", 1,
                "查询当前用户可修改流水的版本历史。", ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String transactionId = ToolInputs.requiredText(input, "transactionId");
        var history = transactions.history(bookId, transactionId);
        return ToolResult.completed("已查询到 " + history.size() + " 个流水版本", mapper.valueToTree(history));
    }
}
