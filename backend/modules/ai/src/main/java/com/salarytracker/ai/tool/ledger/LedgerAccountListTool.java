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
public class LedgerAccountListTool implements DomainTool {
    private final LedgerBookService books;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerAccountListTool(LedgerBookService books, ObjectMapper mapper) {
        this.books = books;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.booleanProperty(schema, "includeHidden", "是否包含已隐藏账户");
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger.account.list", 1,
                "只读查询指定账本已有账户列表（id、名称、余额、类型和 revision）。记账或转账前调用，使用返回的真实账户 id，不得编造账户；bookId 来自 ledger.books.list。不修改数据，默认排除隐藏账户。",
                ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        boolean includeHidden = Boolean.TRUE.equals(ToolInputs.optionalBoolean(input, "includeHidden"));
        var accounts = books.accounts(bookId, includeHidden);
        return ToolResult.completed("当前账本有 " + accounts.size() + " 个账户", mapper.valueToTree(accounts));
    }
}
