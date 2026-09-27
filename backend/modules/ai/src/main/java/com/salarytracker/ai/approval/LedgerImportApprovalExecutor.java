package com.salarytracker.ai.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerImportService;
import com.salarytracker.ledger.LedgerModels.ImportConfirm;
import org.springframework.stereotype.Service;

@Service
public class LedgerImportApprovalExecutor implements AgentApprovalExecutor {
    private final LedgerImportService imports;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;

    public LedgerImportApprovalExecutor(LedgerImportService imports, PendingActionService actions,
                                        CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.imports = imports;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String toolName) {
        return "ledger.import.confirm.prepare".equals(toolName);
    }

    @Override
    public ToolResult commit(String actionId) {
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!"ledger.import.confirm.prepare".equals(action.toolName())) {
            throw new IllegalArgumentException("action 不是导入确认操作");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode snapshot = mapper.readTree(action.inputSnapshot());
            String bookId = snapshot.path("bookId").asText();
            String batchId = snapshot.path("batchId").asText();
            ImportConfirm result = imports.confirm(bookId, batchId);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            ObjectNode content = mapper.valueToTree(result);
            content.put("bookId", bookId);
            return ToolResult.completed("导入完成，共写入 " + result.createdCount() + " 笔流水", content);
        } catch (IllegalArgumentException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("导入执行失败", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }
}
