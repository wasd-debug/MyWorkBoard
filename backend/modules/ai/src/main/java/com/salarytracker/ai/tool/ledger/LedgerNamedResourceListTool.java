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

final class LedgerNamedResourceListTool implements DomainTool {
    private final String type;
    private final String label;
    private final LedgerBookService books;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerNamedResourceListTool(String type, String label, LedgerBookService books, ObjectMapper mapper) {
        this.type = type;
        this.label = label;
        this.books = books;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.booleanProperty(schema, "includeHidden", "是否包含已停用" + label);
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger." + type + ".list", 1,
                "列出指定账本的" + label + "及其 revision，不修改数据。",
                ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        boolean includeHidden = Boolean.TRUE.equals(ToolInputs.optionalBoolean(input, "includeHidden"));
        var values = "merchant".equals(type)
                ? books.merchants(bookId, includeHidden)
                : books.projects(bookId, includeHidden);
        return ToolResult.completed("当前账本有 " + values.size() + " 个" + label, mapper.valueToTree(values));
    }
}
