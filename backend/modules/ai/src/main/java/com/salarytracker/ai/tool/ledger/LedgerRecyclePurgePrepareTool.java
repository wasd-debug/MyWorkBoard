package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.RecycleItem;

import java.time.Duration;
import java.util.List;
import java.util.Set;

final class LedgerRecyclePurgePrepareTool implements DomainTool {
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerRecyclePurgePrepareTool(LedgerBookService books, PendingActionService actions,
                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.books = books; this.actions = actions; this.currentUser = currentUser; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "itemId", "回收站项目公开 ID", null);
        ToolSchemas.stringProperty(schema, "resourceType", "资源类型", null);
        ToolSchemas.required(schema, "bookId", "itemId", "resourceType");
        definition = new ToolDefinition("ledger.recycle.purge.prepare", 1,
                "生成永久清除的 R4 影响预览；不会提供聊天内 commit。",
                ToolRisk.R4, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String itemId = ToolInputs.requiredText(input, "itemId");
        String type = ToolInputs.requiredText(input, "resourceType");
        RecycleItem item = books.recycle(bookId, 1, 100).items().stream()
                .filter(value -> value.id().equals(itemId) && value.type().name().equalsIgnoreCase(type))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("回收站项目不存在"));
        ObjectNode snapshot = mapper.createObjectNode().put("bookId", bookId).put("itemId", itemId)
                .put("resourceType", type.toLowerCase());
        PendingAction action = actions.prepare(currentUser.id(), definition, snapshot, item.revision(),
                false, Duration.ofMinutes(15));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.recycle.purge");
        content.put("commitAvailable", false); content.put("webApprovalRequired", true);
        content.set("input", snapshot); content.set("preview", mapper.valueToTree(item));
        content.set("effects", mapper.valueToTree(List.of(
                "永久清除后无法从回收站恢复",
                "历史引用可能按现有规则匿名化，而不是直接物理删除",
                "当前增量不开放聊天内提交，后续必须在站内 R4 审批中心完成")));
        return ToolResult.needsConfirmation("永久清除属于高风险操作，需要站内审批", content,
                action.id(), action.expiresAt().toString());
    }
}
