package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import com.salarytracker.ledger.LedgerModels.TransactionCommand;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.platform.ConflictException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class LedgerTransactionsBatchCreateCommitTool implements DomainTool {
    private static final String PREPARE_TOOL = "ledger.transactions.batch.create.prepare";
    private final LedgerTransactionService transactions;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionsBatchCreateCommitTool(LedgerTransactionService transactions,
                                                   PendingActionService actions,
                                                   CurrentUserResolver currentUser,
                                                   ObjectMapper mapper) {
        this.transactions = transactions;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的批量 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("ledger.transactions.batch.create.commit", 1,
                "在一个数据库事务内提交已批准的批量流水；任何一笔失败时整批回滚。",
                ToolRisk.R2, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!PREPARE_TOOL.equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与当前批量工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode value = mapper.readTree(action.inputSnapshot());
            String bookId = ToolInputs.requiredText(value, "bookId");
            JsonNode items = value.path("items");
            if (!items.isArray() || items.isEmpty()) throw new IllegalArgumentException("批量 action 缺少流水");
            List<TransactionCommand> commands = new ArrayList<>();
            for (JsonNode item : items) commands.add(command(item, actionId));
            var created = transactions.createBatch(bookId, commands, actionId);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            ObjectNode content = mapper.createObjectNode();
            content.put("count", created.size());
            content.set("items", mapper.valueToTree(created));
            return ToolResult.completed("已批量保存 " + created.size() + " 笔流水", content);
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode()
                    .put("actionId", actionId).put("latestRevision", exception.getServerRevision()), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取批量 action 参数快照",
                    mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }

    private TransactionCommand command(JsonNode value, String actionId) {
        return new TransactionCommand(null, ToolInputs.requiredText(value, "accountId"),
                ToolInputs.optionalText(value, "targetAccountId"), ToolInputs.optionalText(value, "categoryId"),
                ToolInputs.optionalText(value, "merchantId"), ToolInputs.optionalText(value, "memberId"),
                ToolInputs.optionalText(value, "projectId"), TransactionKind.valueOf(ToolInputs.requiredText(value, "kind")),
                value.path("amount").decimalValue(), null, LocalDate.parse(value.path("occurredOn").asText()),
                null, null, null, ToolInputs.optionalText(value, "note"), "agent", actionId, null, null);
    }
}
