package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Book;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class LedgerBooksListTool implements DomainTool {
    private final LedgerBookService ledgerBookService;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerBooksListTool(LedgerBookService ledgerBookService, ObjectMapper mapper) {
        this.ledgerBookService = ledgerBookService;
        this.mapper = mapper;
        this.definition = new ToolDefinition(
                "ledger.books.list",
                1,
                "列出当前用户有权访问的账本及其角色，不修改任何数据。",
                ToolRisk.R1,
                Set.of("ledger:read"),
                ToolSchemas.object(mapper));
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        List<Book> books = ledgerBookService.books();
        return ToolResult.completed("当前可访问 " + books.size() + " 个账本", mapper.valueToTree(books));
    }
}
