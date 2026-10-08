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
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LedgerCategoryListTool implements DomainTool {
    private final LedgerBookService books;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerCategoryListTool(LedgerBookService books, ObjectMapper mapper) {
        this.books = books;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.booleanProperty(schema, "includeHidden", "是否包含已隐藏分类");
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger.category.list", 1,
                "只读查询指定账本已有一二级收支分类列表（id、名称、kind、parentId 和 revision）。收入或支出记账前调用，按用途匹配对应 kind 的二级分类（parentId 非空），使用返回的真实 id，不得编造分类；bookId 来自 ledger.books.list。不修改数据，默认排除隐藏分类。",
                ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        boolean includeHidden = Boolean.TRUE.equals(ToolInputs.optionalBoolean(input, "includeHidden"));
        var categories = books.categories(bookId, includeHidden);
        return ToolResult.completed("当前账本有 " + categories.size() + " 个分类", mapper.valueToTree(categories));
    }
}
