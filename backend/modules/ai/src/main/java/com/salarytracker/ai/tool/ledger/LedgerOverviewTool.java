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
import com.salarytracker.ledger.LedgerModels.Overview;
import com.salarytracker.ledger.LedgerTransactionService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LedgerOverviewTool implements DomainTool {
    private final LedgerTransactionService transactionService;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerOverviewTool(LedgerTransactionService transactionService, ObjectMapper mapper) {
        this.transactionService = transactionService;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "from", "统计起始日期，包含当天", "date");
        ToolSchemas.stringProperty(schema, "to", "统计结束日期，包含当天", "date");
        ToolSchemas.required(schema, "bookId");
        this.definition = new ToolDefinition(
                "ledger.overview",
                1,
                "查询指定账本在日期范围内的收入、支出、结余、每日趋势、分类和预算概览。",
                ToolRisk.R1,
                Set.of("ledger:read"),
                schema);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String from = ToolInputs.optionalDate(input, "from");
        String to = ToolInputs.optionalDate(input, "to");
        Overview overview = transactionService.overview(bookId, from, to);
        return ToolResult.completed(
                "已读取 " + overview.from() + " 至 " + overview.to() + " 的账本概览",
                mapper.valueToTree(overview));
    }
}
