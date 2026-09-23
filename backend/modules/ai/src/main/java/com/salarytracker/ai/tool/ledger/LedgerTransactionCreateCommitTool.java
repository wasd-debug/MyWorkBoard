package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.ActionStatus;
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
import com.salarytracker.ledger.LedgerModels.TransactionCommand;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.platform.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Component
public class LedgerTransactionCreateCommitTool implements DomainTool {
    private static final String PREPARE_TOOL = "ledger.transaction.create.prepare";
    private final LedgerTransactionService transactions;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionCreateCommitTool(LedgerTransactionService transactions, PendingActionService actions,
                                             CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.transactions = transactions; this.actions = actions; this.currentUser = currentUser; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("ledger.transaction.create.commit", 1,
                "提交已批准的单笔收入或支出；一个 action 只能进入一次执行。",
                ToolRisk.R2, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override @Transactional
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!PREPARE_TOOL.equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与当前工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode value = mapper.readTree(action.inputSnapshot());
            String bookId = ToolInputs.requiredText(value, "bookId");
            TransactionCommand command = new TransactionCommand(null,
                    ToolInputs.requiredText(value, "accountId"), null,
                    ToolInputs.requiredText(value, "categoryId"), null, null, null,
                    TransactionKind.valueOf(ToolInputs.requiredText(value, "kind")),
                    value.path("amount").decimalValue(), null,
                    value.path("occurredOn").isTextual() ? LocalDate.parse(value.path("occurredOn").asText()) : LocalDate.now(),
                    null, null, null, ToolInputs.optionalText(value, "note"), "agent", actionId, null, null);
            Transaction created = transactions.create(bookId, command, actionId);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed("账本流水已保存", mapper.valueToTree(created));
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取 action 参数快照", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }
}
