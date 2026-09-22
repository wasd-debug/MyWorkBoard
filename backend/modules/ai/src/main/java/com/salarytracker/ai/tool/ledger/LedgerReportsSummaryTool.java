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
public class LedgerReportsSummaryTool implements DomainTool {
    private final LedgerTransactionService service;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerReportsSummaryTool(LedgerTransactionService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "from", "统计起始日期，包含当天", "date");
        ToolSchemas.stringProperty(schema, "to", "统计结束日期，包含当天", "date");
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger.reports.summary", 1,
                "生成账本收入、支出、结余、分类和预算汇总。",
                ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override
    public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String from = ToolInputs.optionalDate(input, "from");
        String to = ToolInputs.optionalDate(input, "to");
        if (from != null && to != null && to.compareTo(from) < 0) {
            throw new IllegalArgumentException("to 不能早于 from");
        }
        Overview overview = service.overview(bookId, from, to);
        return ToolResult.completed("已生成 " + overview.from() + " 至 " + overview.to() + " 的报表汇总",
                mapper.valueToTree(overview));
    }
}
