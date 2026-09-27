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

final class LedgerMemberRoleListTool implements DomainTool {
    private final boolean member;
    private final LedgerBookService books;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerMemberRoleListTool(boolean member, LedgerBookService books, ObjectMapper mapper) {
        this.member = member;
        this.books = books;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.required(schema, "bookId");
        String resource = member ? "members" : "roles";
        definition = new ToolDefinition("ledger." + resource + ".list", 1,
                "列出指定账本的" + (member ? "成员及其角色" : "角色及权限") + "，不修改数据。",
                ToolRisk.R1, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        var values = member ? books.members(bookId) : books.roles(bookId);
        int size = values.size();
        return ToolResult.completed("当前账本有 " + size + " 个" + (member ? "成员" : "角色"),
                mapper.valueToTree(values));
    }
}
