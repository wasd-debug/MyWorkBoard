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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

@Component
public class LedgerTransactionsBatchCreatePrepareTool implements DomainTool {
    private final LedgerTransactionCreatePrepareTool single;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionsBatchCreatePrepareTool(LedgerTransactionCreatePrepareTool single,
                                                    PendingActionService actions,
                                                    CurrentUserResolver currentUser,
                                                    ObjectMapper mapper) {
        this.single = single;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "整批流水所属账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "bookName", "整批流水所属账本名称", null);
        ToolSchemas.arrayProperty(schema, "items", "要创建的流水草稿列表",
                single.definition().inputSchema().deepCopy(), 1, 50);
        ToolSchemas.required(schema, "items");
        definition = new ToolDefinition("ledger.transactions.batch.create.prepare", 1,
                "校验最多 50 笔混合流水并生成一个可编辑的批量确认操作；不写入账本数据。",
                ToolRisk.R2, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        JsonNode items = input.path("items");
        if (!items.isArray() || items.isEmpty()) throw new IllegalArgumentException("items 至少包含一笔流水");
        if (items.size() > 50) throw new IllegalArgumentException("单次批量记账最多 50 笔");
        String sharedBookId = ToolInputs.optionalText(input, "bookId");
        String sharedBookName = ToolInputs.optionalText(input, "bookName");
        ObjectNode normalized = mapper.createObjectNode();
        ArrayNode normalizedItems = normalized.putArray("items");
        ArrayNode itemContents = mapper.createArrayNode();
        ArrayNode incompleteIndexes = mapper.createArrayNode();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal transfer = BigDecimal.ZERO;
        boolean complete = true;

        for (int index = 0; index < items.size(); index++) {
            if (!items.get(index).isObject()) throw new IllegalArgumentException("items[" + index + "] 必须是 object");
            ObjectNode itemInput = ((ObjectNode) items.get(index)).deepCopy();
            if (!itemInput.hasNonNull("bookId") && sharedBookId != null) itemInput.put("bookId", sharedBookId);
            if (!itemInput.hasNonNull("bookName") && sharedBookName != null) itemInput.put("bookName", sharedBookName);
            LedgerTransactionCreatePrepareTool.ResolvedDraft resolved = single.resolve(itemInput);
            String itemBookId = text(resolved.normalized(), "bookId");
            if (itemBookId != null) {
                if (sharedBookId == null) sharedBookId = itemBookId;
                else if (!sharedBookId.equals(itemBookId)) throw new IllegalArgumentException("批量记账只能提交到同一个账本");
            }
            normalizedItems.add(resolved.normalized());
            ObjectNode itemContent = resolved.content().deepCopy();
            itemContent.put("index", index);
            itemContent.put("status", resolved.complete() ? "READY" : "NEEDS_INPUT");
            itemContent.put("summary", resolved.summary());
            itemContents.add(itemContent);
            if (!resolved.complete()) {
                complete = false;
                incompleteIndexes.add(index);
            } else {
                BigDecimal amount = resolved.normalized().path("amount").decimalValue();
                String kind = resolved.normalized().path("kind").asText();
                if (Set.of("INCOME", "BORROW_IN", "COLLECT_DEBT").contains(kind)) income = income.add(amount);
                else if (Set.of("EXPENSE", "LEND_OUT", "REPAY_DEBT").contains(kind)) expense = expense.add(amount);
                else if ("TRANSFER".equals(kind)) transfer = transfer.add(amount);
            }
        }
        put(normalized, "bookId", sharedBookId);
        put(normalized, "bookName", sharedBookName);
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, null,
                !complete, Duration.ofMinutes(20));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.transactions.batch.create");
        content.set("input", normalized);
        content.set("items", itemContents);
        content.set("incompleteItemIndexes", incompleteIndexes);
        ObjectNode totals = content.putObject("totals");
        totals.put("count", items.size());
        totals.put("readyCount", items.size() - incompleteIndexes.size());
        totals.put("needsInputCount", incompleteIndexes.size());
        totals.put("income", income);
        totals.put("expense", expense);
        totals.put("transfer", transfer);
        String summary = complete ? "请确认批量保存 " + items.size() + " 笔流水"
                : "请补充批量流水中缺失或存在歧义的信息";
        return complete
                ? ToolResult.needsConfirmation(summary, content, action.id(), action.expiresAt().toString())
                : ToolResult.needsInput(summary, content, action.id(), action.expiresAt().toString());
    }

    private String text(JsonNode value, String field) {
        return value.hasNonNull(field) && !value.path(field).asText().isBlank() ? value.path(field).asText() : null;
    }

    private void put(ObjectNode value, String field, String text) {
        if (text == null) value.putNull(field); else value.put(field, text);
    }
}
