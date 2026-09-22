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
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Budget;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.DateTimeException;
import java.util.Set;

@Component
public class LedgerBudgetsListTool implements DomainTool {
    private final LedgerBookService service;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerBudgetsListTool(LedgerBookService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "month", "月份，格式 yyyy-MM，默认当前月份", "month");
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger.budgets.list", 1,
                "查询指定账本某月份的预算及已使用金额。",
                ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override
    public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String month = ToolInputs.optionalText(input, "month");
        if (month != null) {
            try { month = YearMonth.parse(month).toString(); }
            catch (DateTimeException exception) { throw new IllegalArgumentException("month 必须是 yyyy-MM"); }
        }
        var budgets = service.budgets(bookId, month);
        ObjectNode content = mapper.createObjectNode();
        content.set("items", mapper.valueToTree(budgets));
        content.put("month", month == null ? YearMonth.now().toString() : month);
        content.put("returned", budgets.size());
        return ToolResult.completed("已查询到 " + budgets.size() + " 条预算", content);
    }
}
