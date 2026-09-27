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
import com.salarytracker.ledger.LedgerModels.RecycleItem;

import java.time.Duration;
import java.util.List;
import java.util.Set;

final class LedgerRecycleRestorePrepareTool implements DomainTool {
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerRecycleRestorePrepareTool(LedgerBookService books, PendingActionService actions,
                                    CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.books = books;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "itemId", "回收站项目公开 ID", null);
        ToolSchemas.stringProperty(schema, "itemName", "回收站项目名称", null);
        ToolSchemas.stringProperty(schema, "resourceType", "资源类型，例如 transaction/account/category", null);
        definition = new ToolDefinition("ledger.recycle.restore.prepare", 1,
                "恢复回收站项目之前读取真实项目和 revision，生成强确认预览，不立即写入。",
                ToolRisk.R3, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        ObjectNode normalized = mapper.createObjectNode();
        ArrayNode fields = mapper.createArrayNode();
        ArrayNode missing = mapper.createArrayNode();
        List<Book> visibleBooks = books.books();
        String bookId = selectBook(input, visibleBooks);
        if (bookId == null) {
            missing.add("bookId");
            fields.add(entityField("bookId", "账本", bookOptions(visibleBooks)));
        } else normalized.put("bookId", bookId);

        RecycleItem selected = null;
        List<RecycleItem> items = List.of();
        if (bookId != null) {
            items = books.recycle(bookId, 1, 100).items();
            selected = selectItem(input, items);
            if (selected == null) {
                missing.add("itemId");
                fields.add(entityField("itemId", "回收站项目", itemOptions(items)));
            } else {
                normalized.put("itemId", selected.id());
                normalized.put("resourceType", selected.type().name().toLowerCase());
            }
        }

        PendingAction action = actions.prepare(currentUser.id(), definition, normalized,
                selected == null ? null : selected.revision(), !missing.isEmpty(), Duration.ofMinutes(15));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.recycle.restore");
        content.set("input", normalized);
        content.set("fields", fields);
        content.set("missingFields", missing);
        if (selected != null) {
            content.set("preview", mapper.valueToTree(selected));
            content.set("effects", mapper.valueToTree(List.of(
                    "恢复后项目会重新出现在账本及本地同步投影中",
                    "提交前会再次校验回收站状态和 revision",
                    "重复提交同一个 action 不会重复恢复")));
        }
        if (!missing.isEmpty()) {
            return ToolResult.needsInput("请选择要恢复的回收站项目", content,
                    action.id(), action.expiresAt().toString());
        }
        return ToolResult.needsConfirmation("请确认恢复“" + selected.name() + "”", content,
                action.id(), action.expiresAt().toString());
    }

    private String selectBook(JsonNode input, List<Book> values) {
        String id = ToolInputs.optionalText(input, "bookId");
        if (id != null) return values.stream().anyMatch(item -> item.id().equals(id)) ? id : null;
        return values.size() == 1 ? values.get(0).id() : null;
    }

    private RecycleItem selectItem(JsonNode input, List<RecycleItem> values) {
        String id = ToolInputs.optionalText(input, "itemId");
        String name = ToolInputs.optionalText(input, "itemName");
        String type = ToolInputs.optionalText(input, "resourceType");
        List<RecycleItem> filtered = values.stream()
                .filter(item -> type == null || item.type().name().equalsIgnoreCase(type))
                .filter(item -> id == null || item.id().equals(id))
                .filter(item -> name == null || item.name().equalsIgnoreCase(name)
                        || item.name().toLowerCase().contains(name.toLowerCase()))
                .toList();
        return filtered.size() == 1 ? filtered.get(0) : null;
    }

    private ObjectNode entityField(String name, String label, ArrayNode options) {
        ObjectNode field = mapper.createObjectNode();
        field.put("name", name); field.put("label", label); field.put("type", "entity-picker");
        field.put("required", true); field.set("options", options); return field;
    }

    private ArrayNode bookOptions(List<Book> values) {
        ArrayNode result = mapper.createArrayNode();
        values.forEach(item -> result.add(option(item.id(), item.name(), "账本")));
        return result;
    }

    private ArrayNode itemOptions(List<RecycleItem> values) {
        ArrayNode result = mapper.createArrayNode();
        values.forEach(item -> result.add(option(item.id(), item.name(),
                item.type().name().toLowerCase() + " · 删除于 " + item.deletedAt())));
        return result;
    }

    private ObjectNode option(String value, String label, String description) {
        ObjectNode result = mapper.createObjectNode();
        result.put("value", value); result.put("label", label); result.put("description", description);
        return result;
    }
}
