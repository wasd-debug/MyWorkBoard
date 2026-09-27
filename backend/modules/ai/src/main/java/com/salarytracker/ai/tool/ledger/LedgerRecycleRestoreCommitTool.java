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
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.platform.ConflictException;

import java.util.Set;

final class LedgerRecycleRestoreCommitTool implements DomainTool {
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerRecycleRestoreCommitTool(LedgerBookService books, LedgerTransactionService transactions,
                                   PendingActionService actions, CurrentUserResolver currentUser,
                                   ObjectMapper mapper) {
        this.books = books; this.transactions = transactions; this.actions = actions;
        this.currentUser = currentUser; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("ledger.recycle.restore.commit", 1,
                "提交已批准的回收站恢复操作，并重新校验用户、权限、状态和 revision。",
                ToolRisk.R3, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!"ledger.recycle.restore.prepare".equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与回收站恢复工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode values = mapper.readTree(action.inputSnapshot());
            String bookId = values.path("bookId").asText();
            String itemId = values.path("itemId").asText();
            String type = values.path("resourceType").asText();
            String revision = action.expectedRevision() == null ? null : String.valueOf(action.expectedRevision());
            Object restored = "transaction".equals(type)
                    ? transactions.restore(bookId, itemId, revision, actionId)
                    : books.restoreResource(bookId, type, itemId, revision, actionId);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed("回收站项目已恢复", mapper.valueToTree(restored));
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode()
                    .put("actionId", actionId).put("latestRevision", exception.getServerRevision()), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取 action 参数快照", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }
}
