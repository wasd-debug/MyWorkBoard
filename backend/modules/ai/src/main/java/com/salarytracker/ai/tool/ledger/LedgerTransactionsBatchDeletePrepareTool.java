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
import com.salarytracker.ledger.LedgerModels.Transaction;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import com.salarytracker.ledger.LedgerTransactionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Component
public class LedgerTransactionsBatchDeletePrepareTool implements DomainTool {
    private final LedgerTransactionService transactions;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionsBatchDeletePrepareTool(LedgerTransactionService transactions,
                                                    PendingActionService actions,
                                                    CurrentUserResolver currentUser,
                                                    ObjectMapper mapper) {
        this.transactions = transactions;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ObjectNode stringItem = mapper.createObjectNode().put("type", "string");
        ToolSchemas.arrayProperty(schema, "transactionIds", "要删除的明确流水公开 ID 列表", stringItem, 1, 50);
        ToolSchemas.required(schema, "bookId", "transactionIds");
        definition = new ToolDefinition("ledger.transactions.batch.delete.prepare", 1,
                "读取最多 50 笔明确流水并固化 ID 与 revision，生成强确认删除预览；不接受模糊条件。",
                ToolRisk.R3, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        JsonNode ids = input.path("transactionIds");
        if (!ids.isArray() || ids.isEmpty()) throw new IllegalArgumentException("transactionIds 至少包含一笔流水");
        if (ids.size() > 50) throw new IllegalArgumentException("单次批量删除最多 50 笔");
        ObjectNode snapshot = mapper.createObjectNode().put("bookId", bookId);
        ArrayNode snapshotItems = snapshot.putArray("items");
        ArrayNode previews = mapper.createArrayNode();
        Set<String> idsSeen = new HashSet<>();
        Set<String> logicalGroups = new HashSet<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal transfer = BigDecimal.ZERO;
        for (int index = 0; index < ids.size(); index++) {
            if (!ids.get(index).isTextual() || ids.get(index).asText().isBlank()) {
                throw new IllegalArgumentException("transactionIds[" + index + "] 必须是非空字符串");
            }
            String id = ids.get(index).asText();
            if (!idsSeen.add(id)) throw new IllegalArgumentException("批量删除包含重复流水 ID: " + id);
            Transaction value = transactions.transaction(bookId, id);
            String group = value.kind() == TransactionKind.TRANSFER && value.transferGroupId() != null
                    ? "transfer:" + value.transferGroupId() : "transaction:" + value.id();
            if (!logicalGroups.add(group)) throw new IllegalArgumentException("批量删除包含同一转账组的重复记录");
            snapshotItems.addObject().put("transactionId", value.id()).put("revision", value.revision());
            previews.add(preview(value));
            if (Set.of(TransactionKind.INCOME, TransactionKind.BORROW_IN, TransactionKind.COLLECT_DEBT).contains(value.kind())) {
                income = income.add(value.amount());
            } else if (Set.of(TransactionKind.EXPENSE, TransactionKind.LEND_OUT, TransactionKind.REPAY_DEBT).contains(value.kind())) {
                expense = expense.add(value.amount());
            } else if (value.kind() == TransactionKind.TRANSFER) {
                transfer = transfer.add(value.amount());
            }
        }
        PendingAction action = actions.prepare(currentUser.id(), definition, snapshot, null,
                false, Duration.ofMinutes(15));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.transactions.batch.delete");
        content.set("input", snapshot);
        content.set("items", previews);
        ObjectNode totals = content.putObject("totals");
        totals.put("count", previews.size()); totals.put("income", income); totals.put("expense", expense); totals.put("transfer", transfer);
        content.putArray("effects")
                .add("仅删除本次预览中固化 ID 与 revision 的流水")
                .add("任意一笔版本变化时整批不执行")
                .add("转账的关联两端会同步删除")
                .add("删除结果进入回收站、审计和同步链路");
        return ToolResult.needsConfirmation("请确认批量删除 " + previews.size() + " 笔流水",
                content, action.id(), action.expiresAt().toString());
    }

    private ObjectNode preview(Transaction value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("transactionId", value.id()); node.put("revision", value.revision());
        node.put("kind", value.kind().name()); node.put("amount", value.amount());
        node.put("occurredOn", value.occurredOn().toString());
        nullable(node, "accountName", value.accountName()); nullable(node, "targetAccountName", value.targetAccountName());
        nullable(node, "categoryName", value.categoryName()); nullable(node, "merchantName", value.merchantName());
        nullable(node, "memberName", value.member()); nullable(node, "projectName", value.projectName());
        nullable(node, "note", value.note());
        return node;
    }

    private void nullable(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) node.putNull(field); else node.put(field, value);
    }
}
