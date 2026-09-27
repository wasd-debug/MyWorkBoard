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
import com.salarytracker.ledger.LedgerModels.TransactionDeleteCommand;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.platform.ConflictException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class LedgerTransactionsBatchDeleteCommitTool implements DomainTool {
    private static final String PREPARE_TOOL = "ledger.transactions.batch.delete.prepare";
    private final LedgerTransactionService transactions;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionsBatchDeleteCommitTool(LedgerTransactionService transactions,
                                                   PendingActionService actions,
                                                   CurrentUserResolver currentUser,
                                                   ObjectMapper mapper) {
        this.transactions = transactions;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的批量删除 action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("ledger.transactions.batch.delete.commit", 1,
                "在一个数据库事务内删除 prepare 固化的明确流水；任意冲突时整批回滚。",
                ToolRisk.R3, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!PREPARE_TOOL.equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与当前批量删除工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode value = mapper.readTree(action.inputSnapshot());
            String bookId = ToolInputs.requiredText(value, "bookId");
            JsonNode items = value.path("items");
            List<TransactionDeleteCommand> commands = new ArrayList<>();
            for (JsonNode item : items) commands.add(new TransactionDeleteCommand(
                    ToolInputs.requiredText(item, "transactionId"), item.path("revision").asLong()));
            var deleted = transactions.deleteBatch(bookId, commands, actionId);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            ObjectNode content = mapper.createObjectNode();
            content.put("count", deleted.size());
            content.set("items", mapper.valueToTree(deleted));
            return ToolResult.completed("已批量删除 " + deleted.size() + " 笔流水", content);
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode()
                    .put("actionId", actionId).put("latestRevision", exception.getServerRevision()), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取批量删除 action 参数快照",
                    mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }
}
