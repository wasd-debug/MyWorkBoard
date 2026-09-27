package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Book;
import com.salarytracker.ledger.LedgerModels.TransactionQuery;
import com.salarytracker.ledger.LedgerTransactionService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

final class LedgerExportPrepareTool implements DomainTool {
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerExportPrepareTool(LedgerBookService books, LedgerTransactionService transactions,
                            PendingActionService actions, CurrentUserResolver currentUser,
                            ObjectMapper mapper) {
        this.books = books; this.transactions = transactions; this.actions = actions;
        this.currentUser = currentUser; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.enumProperty(schema, "format", "导出格式", "xlsx", "csv");
        ToolSchemas.stringProperty(schema, "from", "开始日期 yyyy-MM-dd", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期 yyyy-MM-dd", "date");
        definition = new ToolDefinition("ledger.export.prepare", 1,
                "准备导出当前用户有权访问的账本流水，展示格式、日期范围和预计数量。",
                ToolRisk.R2, Set.of("ledger:read"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        List<Book> visible = books.books();
        String bookId = ToolInputs.optionalText(input, "bookId");
        if (bookId == null && visible.size() == 1) bookId = visible.get(0).id();
        String selectedBookId = bookId;
        String format = value(input, "format", "xlsx").toLowerCase();
        if (!Set.of("csv", "xlsx").contains(format)) throw new IllegalArgumentException("format 仅支持 csv 或 xlsx");
        String from = ToolInputs.optionalText(input, "from");
        String to = ToolInputs.optionalText(input, "to");
        validateDates(from, to);

        ObjectNode normalized = mapper.createObjectNode();
        ArrayNode missing = mapper.createArrayNode();
        ArrayNode fields = mapper.createArrayNode();
        if (selectedBookId == null || visible.stream().noneMatch(item -> item.id().equals(selectedBookId))) {
            missing.add("bookId"); fields.add(bookField(visible));
        } else normalized.put("bookId", selectedBookId);
        normalized.put("format", format);
        if (from != null) normalized.put("from", from); else normalized.putNull("from");
        if (to != null) normalized.put("to", to); else normalized.putNull("to");
        fields.add(selectField("format", "文件格式", new String[][]{{"xlsx", "Excel (.xlsx)"}, {"csv", "CSV (.csv)"}}));
        fields.add(field("from", "开始日期", "date", false));
        fields.add(field("to", "结束日期", "date", false));

        long count = 0;
        if (!missing.isEmpty()) {
            count = 0;
        } else {
            count = transactions.list(selectedBookId, new TransactionQuery("1", "1", from, to, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null)).total();
        }
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, null,
                !missing.isEmpty(), Duration.ofMinutes(15));
        ObjectNode preview = normalized.deepCopy();
        preview.put("estimatedCount", count);
        preview.put("filename", "ledger-" + LocalDate.now() + "." + format);
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.export"); content.set("input", normalized);
        content.set("fields", fields); content.set("missingFields", missing); content.set("preview", preview);
        content.set("effects", mapper.valueToTree(List.of(
                "导出只包含当前账本和日期范围内的有效流水",
                "文件通过当前登录会话下载，不会暴露服务器文件路径",
                "导出不会修改账本数据")));
        if (!missing.isEmpty()) return ToolResult.needsInput("请选择要导出的账本", content,
                action.id(), action.expiresAt().toString());
        return ToolResult.needsConfirmation("请确认导出 " + count + " 笔流水", content,
                action.id(), action.expiresAt().toString());
    }

    private void validateDates(String from, String to) {
        LocalDate start = from == null ? null : LocalDate.parse(from);
        LocalDate end = to == null ? null : LocalDate.parse(to);
        if (start != null && end != null && start.isAfter(end)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
    }

    private String value(JsonNode input, String field, String fallback) {
        String value = ToolInputs.optionalText(input, field); return value == null ? fallback : value;
    }

    private ObjectNode field(String name, String label, String type, boolean required) {
        ObjectNode result = mapper.createObjectNode(); result.put("name", name); result.put("label", label);
        result.put("type", type); result.put("required", required); return result;
    }

    private ObjectNode selectField(String name, String label, String[][] values) {
        ObjectNode result = field(name, label, "select", true); ArrayNode options = mapper.createArrayNode();
        for (String[] value : values) options.add(mapper.createObjectNode().put("value", value[0]).put("label", value[1]));
        result.set("options", options); return result;
    }

    private ObjectNode bookField(List<Book> values) {
        ObjectNode result = field("bookId", "账本", "entity-picker", true); ArrayNode options = mapper.createArrayNode();
        values.forEach(item -> options.add(mapper.createObjectNode().put("value", item.id()).put("label", item.name()).put("description", "账本")));
        result.set("options", options); return result;
    }
}
