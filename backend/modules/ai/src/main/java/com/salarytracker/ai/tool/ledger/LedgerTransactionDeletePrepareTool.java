package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Duration;
import java.util.Set;

@Component
public class LedgerTransactionDeletePrepareTool implements DomainTool {
    private final LedgerTransactionService transactions;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionDeletePrepareTool(LedgerTransactionService transactions, PendingActionService actions,
                                              CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.transactions = transactions;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "transactionId", "要删除的流水公开 ID", null);
        ToolSchemas.required(schema, "bookId", "transactionId");
        definition = new ToolDefinition("ledger.transaction.delete.prepare", 1,
                "读取一笔收入或支出并生成完整删除影响预览；不写入数据。",
                ToolRisk.R3, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String transactionId = ToolInputs.requiredText(input, "transactionId");
        Transaction transaction = transactions.transaction(bookId, transactionId);
        if (!Set.of(TransactionKind.EXPENSE, TransactionKind.INCOME).contains(transaction.kind())) {
            throw new IllegalArgumentException("首版只支持删除普通收入或支出流水");
        }
        ObjectNode snapshot = mapper.createObjectNode().put("bookId", bookId).put("transactionId", transactionId);
        PendingAction action = actions.prepare(currentUser.id(), definition, snapshot, transaction.revision(),
                false, Duration.ofMinutes(15));

        ObjectNode preview = mapper.createObjectNode();
        preview.put("transactionId", transaction.id());
        preview.put("kind", transaction.kind().name());
        preview.put("amount", transaction.amount());
        nullable(preview, "accountName", transaction.accountName());
        nullable(preview, "categoryName", transaction.categoryName());
        String categoryPath = transaction.parentCategoryName() == null ? transaction.categoryName()
                : transaction.parentCategoryName() + " / " + transaction.categoryName();
        nullable(preview, "categoryPath", categoryPath);
        nullable(preview, "merchantName", transaction.merchantName());
        nullable(preview, "memberName", transaction.member());
        nullable(preview, "projectName", transaction.projectName());
        preview.put("occurredOn", transaction.occurredOn().toString());
        preview.put("note", transaction.note() == null ? "" : transaction.note());
        preview.put("revision", transaction.revision());

        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.transaction.delete");
        content.set("input", snapshot);
        content.set("preview", preview);
        content.putArray("effects")
                .add("该流水将从账本列表、报表和统计中移除")
                .add("对应账户余额和本地账本投影将重新计算")
                .add("删除会进入现有回收站和审计链路，不会立即永久清除");
        return ToolResult.needsConfirmation("请确认删除这笔流水", content,
                action.id(), action.expiresAt().toString());
    }

    private void nullable(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) node.putNull(field); else node.put(field, value);
    }
}
