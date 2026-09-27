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
import com.salarytracker.ledger.LedgerModels.AccountCommand;
import com.salarytracker.ledger.LedgerModels.BookCommand;
import com.salarytracker.ledger.LedgerModels.BookMode;
import com.salarytracker.ledger.LedgerModels.BudgetCommand;
import com.salarytracker.ledger.LedgerModels.CategoryCommand;
import com.salarytracker.ledger.LedgerModels.CategoryKind;
import com.salarytracker.ledger.LedgerModels.NamedResourceCommand;
import com.salarytracker.platform.ConflictException;

import java.util.Set;

final class LedgerManagementCommitTool implements DomainTool {
    private final LedgerManagementToolMode mode;
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerManagementCommitTool(LedgerManagementToolMode mode, LedgerBookService books,
                               PendingActionService actions, CurrentUserResolver currentUser,
                               ObjectMapper mapper) {
        this.mode = mode;
        this.books = books;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition(mode.actionType() + ".commit", 1,
                "提交已批准的" + mode.actionLabel() + " action；提交前再次校验权限和 revision。",
                mode.delete() ? ToolRisk.R3 : ToolRisk.R2, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        String prepareTool = mode.actionType() + ".prepare";
        if (!prepareTool.equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与当前工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode values = mapper.readTree(action.inputSnapshot());
            Object result = executeOperation(values, action, actionId);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed(mode.actionLabel() + "已完成", mapper.valueToTree(result));
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId)
                    .put("latestRevision", exception.getServerRevision()), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取 action 参数快照", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }

    private Object executeOperation(JsonNode values, PendingAction action, String actionId) {
        String revision = action.expectedRevision() == null ? null : String.valueOf(action.expectedRevision());
        if (mode == LedgerManagementToolMode.BOOK_CREATE) {
            return books.createBook(new BookCommand(null, text(values, "name"), text(values, "currency"),
                    BookMode.valueOf(text(values, "mode")), optional(values, "sourceBookId"), null));
        }
        String bookId = text(values, "bookId");
        if (mode == LedgerManagementToolMode.BOOK_UPDATE) {
            return books.updateBook(bookId, new BookCommand(null, text(values, "name"), text(values, "currency"),
                    null, null, booleanValue(values, "archived")), revision);
        }
        String resourceId = optional(values, "resourceId");
        if (mode.account()) {
            if (mode.create()) return books.createAccount(bookId, account(values), actionId);
            if (mode.update()) return books.updateAccount(bookId, resourceId, account(values), revision, actionId);
            return books.deleteAccount(bookId, resourceId, revision, actionId);
        }
        if (mode.category()) {
            if (mode.create()) return books.createCategory(bookId, category(values), actionId);
            if (mode.update()) return books.updateCategory(bookId, resourceId, category(values), revision, actionId);
            return books.deleteCategory(bookId, resourceId, revision, actionId);
        }
        if (mode.namedResource()) {
            String type = mode.namedResourceType();
            if (mode.create()) return books.createNamedResource(bookId, type, namedResource(values), actionId);
            if (mode.update()) return books.updateNamedResource(bookId, type, resourceId,
                    namedResource(values), revision, actionId);
            return books.deleteNamedResource(bookId, type, resourceId, revision, actionId);
        }
        if (mode == LedgerManagementToolMode.BUDGET_UPSERT) {
            return books.upsertBudget(bookId, budget(values), revision, actionId);
        }
        return books.deleteBudget(bookId, resourceId, revision, actionId);
    }

    private AccountCommand account(JsonNode values) {
        return new AccountCommand(null, text(values, "name"), text(values, "icon"), text(values, "accountType"),
                text(values, "currency"), values.path("openingBalance").decimalValue(), booleanValue(values, "hidden"));
    }

    private CategoryCommand category(JsonNode values) {
        return new CategoryCommand(null, text(values, "name"), text(values, "icon"),
                CategoryKind.valueOf(text(values, "kind")), optional(values, "parentId"),
                optional(values, "color"), booleanValue(values, "hidden"));
    }

    private NamedResourceCommand namedResource(JsonNode values) {
        return new NamedResourceCommand(null, text(values, "name"), text(values, "icon"),
                optional(values, "color"), optional(values, "note"), booleanValue(values, "hidden"));
    }

    private BudgetCommand budget(JsonNode values) {
        return new BudgetCommand(null, optional(values, "categoryId"), optional(values, "scope"),
                text(values, "monthKey"), values.path("budget").decimalValue());
    }

    private String text(JsonNode values, String field) {
        String value = optional(values, field);
        if (value == null) throw new IllegalArgumentException(field + " 必填");
        return value;
    }

    private String optional(JsonNode values, String field) {
        JsonNode value = values.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean booleanValue(JsonNode values, String field) {
        JsonNode value = values.get(field);
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.booleanValue();
        return Boolean.valueOf(value.asText());
    }
}
