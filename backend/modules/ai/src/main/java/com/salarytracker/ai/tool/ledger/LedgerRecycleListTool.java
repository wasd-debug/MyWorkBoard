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

import java.util.Set;

final class LedgerRecycleListTool implements DomainTool {
    private final LedgerBookService books;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerRecycleListTool(LedgerBookService books, ObjectMapper mapper) {
        this.books = books;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.integerProperty(schema, "page", "页码", 1, 1, 10000);
        ToolSchemas.integerProperty(schema, "pageSize", "每页数量", 20, 1, 100);
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger.recycle.list", 1,
                "查询当前用户有权查看的账本回收站项目、删除时间和 revision，不修改数据。",
                ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        int page = input.path("page").asInt(1);
        int pageSize = input.path("pageSize").asInt(20);
        var result = books.recycle(bookId, page, pageSize);
        return ToolResult.completed("回收站共有 " + result.total() + " 项，当前返回 " + result.items().size() + " 项",
                mapper.valueToTree(result));
    }
}
