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
import com.salarytracker.ledger.LedgerModels.TransactionPage;
import com.salarytracker.ledger.LedgerModels.TransactionQuery;
import com.salarytracker.ledger.LedgerTransactionService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LedgerTransactionsSearchTool implements DomainTool {
    private final LedgerTransactionService service;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionsSearchTool(LedgerTransactionService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "from", "起始日期，包含当天", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期，包含当天", "date");
        ToolSchemas.stringProperty(schema, "kind", "流水类型，例如 EXPENSE 或 INCOME", null);
        ToolSchemas.stringProperty(schema, "q", "搜索备注、收款方等文本", null);
        ToolSchemas.stringProperty(schema, "categoryId", "分类公开 ID", null);
        ToolSchemas.stringProperty(schema, "accountId", "账户公开 ID", null);
        ToolSchemas.stringProperty(schema, "sort", "排序字段：occurredOn、amount 或 createdAt", null);
        ToolSchemas.stringProperty(schema, "direction", "排序方向：asc 或 desc", null);
        ToolSchemas.integerProperty(schema, "page", "页码", 1, 1, Integer.MAX_VALUE);
        ToolSchemas.integerProperty(schema, "pageSize", "每页数量", 20, 1, 100);
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger.transactions.search", 1,
                "按账本、日期、类型和文本条件查询流水，返回分页结果和收入支出汇总。",
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
        String kind = ToolInputs.optionalText(input, "kind");
        String accountId = ToolInputs.optionalText(input, "accountId");
        String categoryId = ToolInputs.optionalText(input, "categoryId");
        String q = ToolInputs.optionalText(input, "q");
        String sort = ToolInputs.optionalText(input, "sort");
        String direction = ToolInputs.optionalText(input, "direction");
        int page = ToolInputs.integer(input, "page", 1, 1, Integer.MAX_VALUE);
        int pageSize = ToolInputs.integer(input, "pageSize", 20, 1, 100);
        TransactionQuery query = new TransactionQuery(String.valueOf(page), String.valueOf(pageSize), from, to,
                kind, accountId, categoryId, null, null, null, null, null, null, null, null, null, q,
                null, sort, direction);
        TransactionPage result = service.list(bookId, query);
        ObjectNode content = mapper.valueToTree(result);
        return ToolResult.completed("已查询到 " + result.items().size() + " 条账本流水", content);
    }
}
