package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.approval.AgentApproval;
import com.salarytracker.ai.approval.AgentApprovalService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.BookDeletionImpact;

import java.time.Duration;
import java.util.Set;

final class LedgerBookDeletePrepareTool implements DomainTool {
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final AgentApprovalService approvals;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerBookDeletePrepareTool(LedgerBookService books, PendingActionService actions,
                                AgentApprovalService approvals, CurrentUserResolver currentUser,
                                ObjectMapper mapper) {
        this.books = books;
        this.actions = actions;
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "待删除账本公开 ID", null);
        ToolSchemas.required(schema, "bookId");
        definition = new ToolDefinition("ledger.book.delete.prepare", 1,
                "冻结账本删除影响并创建 R4 站内审批，不直接删除账本。",
                ToolRisk.R4, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        BookDeletionImpact impact = books.bookDeletionImpact(bookId);
        if (impact.remainingBookCount() < 1) throw new IllegalArgumentException("至少保留一个可用账本");

        ObjectNode normalized = mapper.createObjectNode().put("bookId", bookId);
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized,
                impact.book().revision(), false, Duration.ofMinutes(30));
        ObjectNode payload = mapper.createObjectNode();
        payload.put("actionType", "ledger.book.delete");
        payload.put("resourceType", "book");
        payload.put("operation", "delete");
        payload.set("input", normalized);
        payload.set("before", mapper.valueToTree(impact.book()));
        payload.putNull("after");
        payload.set("preview", mapper.valueToTree(impact));
        ArrayNode effects = payload.putArray("effects");
        effects.add("账本及其当前业务数据将从可用账本中移除");
        effects.add("该操作不会删除用户的其他账本");
        effects.add("批准执行前会再次校验 OWNER 权限、账本版本和剩余账本数量");
        payload.put("webApprovalRequired", true);
        payload.put("commitAvailable", false);
        AgentApproval approval = approvals.create(action, bookId,
                "删除账本“" + impact.book().name() + "”", payload);
        payload.put("approvalId", approval.id());
        return new ToolResult(com.salarytracker.ai.tool.ToolStatus.NEEDS_CONFIRMATION,
                "已创建账本删除审批，请前往站内审批中心处理", payload, action.id(),
                "/approvals/" + approval.id(), action.expiresAt().toString(), null);
    }
}
