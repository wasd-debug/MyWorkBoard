package com.salarytracker.ai.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.RecyclePurgeCommand;
import com.salarytracker.ledger.LedgerModels.ResourceType;
import com.salarytracker.ledger.LedgerRecyclePurgeService;
import com.salarytracker.platform.ConflictException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LedgerDestructiveApprovalExecutor implements AgentApprovalExecutor {
    private final LedgerBookService books;
    private final LedgerRecyclePurgeService recycle;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;

    public LedgerDestructiveApprovalExecutor(LedgerBookService books, LedgerRecyclePurgeService recycle,
                                             PendingActionService actions, CurrentUserResolver currentUser,
                                             ObjectMapper mapper) {
        this.books = books;
        this.recycle = recycle;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String toolName) {
        return "ledger.book.delete.prepare".equals(toolName)
                || "ledger.recycle.purge.prepare".equals(toolName);
    }

    @Override
    public ToolResult commit(String actionId) {
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!supports(action.toolName())) throw new IllegalArgumentException("action 不是账本高风险审批操作");
        actions.beginCommit(actionId, userId);
        try {
            JsonNode snapshot = mapper.readTree(action.inputSnapshot());
            ToolResult result = execute(action, snapshot);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return result;
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode()
                    .put("actionId", actionId).put("latestRevision", exception.getServerRevision()), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取审批参数快照", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }

    private ToolResult execute(PendingAction action, JsonNode snapshot) {
        String bookId = requiredText(snapshot, "bookId");
        if ("ledger.book.delete.prepare".equals(action.toolName())) {
            var result = books.deleteBook(bookId, String.valueOf(action.expectedRevision()));
            ObjectNode content = mapper.valueToTree(result);
            content.put("bookId", bookId);
            return ToolResult.completed("账本已删除", content);
        }
        List<RecyclePurgeCommand> commands = new ArrayList<>();
        JsonNode items = snapshot.path("items");
        if (!items.isArray() || items.isEmpty()) throw new IllegalArgumentException("审批快照中没有回收站项目");
        for (JsonNode item : items) {
            ResourceType type;
            try {
                type = ResourceType.valueOf(requiredText(item, "type").toLowerCase());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("审批快照包含不支持的资源类型");
            }
            commands.add(new RecyclePurgeCommand(type, requiredText(item, "id"), item.path("revision").asLong(-1)));
        }
        if (commands.stream().anyMatch(value -> value.revision() < 0)) {
            throw new IllegalArgumentException("审批快照缺少资源版本");
        }
        var results = recycle.purge(bookId, commands);
        ObjectNode content = mapper.createObjectNode().put("bookId", bookId).put("purgedCount", results.size());
        content.set("items", mapper.valueToTree(results));
        return ToolResult.completed("已永久清除 " + results.size() + " 个回收站项目", content);
    }

    private String requiredText(JsonNode value, String field) {
        String text = value.path(field).asText().trim();
        if (text.isEmpty()) throw new IllegalArgumentException(field + " 必填");
        return text;
    }
}
