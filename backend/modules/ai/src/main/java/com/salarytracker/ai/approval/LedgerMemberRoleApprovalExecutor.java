package com.salarytracker.ai.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.MemberCommand;
import com.salarytracker.ledger.LedgerModels.RoleCommand;
import com.salarytracker.platform.ConflictException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LedgerMemberRoleApprovalExecutor implements AgentApprovalExecutor {
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;

    public LedgerMemberRoleApprovalExecutor(LedgerBookService books, PendingActionService actions,
                                            CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.books = books;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String toolName) {
        return toolName != null && (toolName.startsWith("ledger.member.") || toolName.startsWith("ledger.role."))
                && toolName.endsWith(".prepare");
    }

    @Override
    public ToolResult commit(String actionId) {
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!supports(action.toolName())) throw new IllegalArgumentException("action 不是成员或角色审批操作");
        actions.beginCommit(actionId, userId);
        try {
            JsonNode values = mapper.readTree(action.inputSnapshot());
            Object result = execute(action, values, actionId);
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed(summary(action.toolName()), mapper.valueToTree(result));
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

    private Object execute(PendingAction action, JsonNode values, String actionId) {
        String tool = action.toolName();
        String bookId = text(values, "bookId");
        String revision = action.expectedRevision() == null ? null : String.valueOf(action.expectedRevision());
        if ("ledger.member.create.prepare".equals(tool)) {
            return books.addMember(bookId, new MemberCommand(null, text(values, "username"),
                    text(values, "roleId"), text(values, "icon")), actionId);
        }
        if ("ledger.member.update.prepare".equals(tool)) {
            return books.updateMember(bookId, text(values, "memberId"), new MemberCommand(null, null,
                    text(values, "roleId"), text(values, "icon")), revision, actionId);
        }
        if ("ledger.member.delete.prepare".equals(tool)) {
            return books.deleteMember(bookId, text(values, "memberId"), revision, actionId);
        }
        List<String> permissions = mapper.convertValue(values.path("permissions"),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        if ("ledger.role.create.prepare".equals(tool)) {
            return books.createRole(bookId, new RoleCommand(null, null, text(values, "name"), permissions), actionId);
        }
        if ("ledger.role.update.prepare".equals(tool)) {
            return books.updateRole(bookId, text(values, "roleId"),
                    new RoleCommand(null, null, text(values, "name"), permissions), revision, actionId);
        }
        if ("ledger.role.delete.prepare".equals(tool)) {
            return books.deleteRole(bookId, text(values, "roleId"), revision, actionId);
        }
        throw new IllegalArgumentException("不支持的成员或角色审批操作");
    }

    private String summary(String tool) {
        if (tool.contains("member.create")) return "成员已添加";
        if (tool.contains("member.update")) return "成员已更新";
        if (tool.contains("member.delete")) return "成员已移除";
        if (tool.contains("role.create")) return "角色已创建";
        if (tool.contains("role.update")) return "角色已更新";
        return "角色已删除";
    }

    private String text(JsonNode values, String field) {
        String value = values.path(field).asText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(field + " 必填");
        return value;
    }
}
