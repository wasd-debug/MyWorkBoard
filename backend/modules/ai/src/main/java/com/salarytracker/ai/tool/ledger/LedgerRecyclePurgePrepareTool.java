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
import com.salarytracker.ledger.LedgerModels.RecycleItem;
import com.salarytracker.ledger.LedgerRecyclePurgeService;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LedgerRecyclePurgePrepareTool implements DomainTool {
    private final LedgerRecyclePurgeService recycle;
    private final PendingActionService actions;
    private final AgentApprovalService approvals;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerRecyclePurgePrepareTool(LedgerRecyclePurgeService recycle, PendingActionService actions,
                                  AgentApprovalService approvals, CurrentUserResolver currentUser,
                                  ObjectMapper mapper) {
        this.recycle = recycle;
        this.actions = actions;
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.enumProperty(schema, "scope", "清除范围", "ITEM", "BOOK");
        ToolSchemas.stringProperty(schema, "itemId", "单项清除时的回收站项目公开 ID", null);
        ToolSchemas.stringProperty(schema, "resourceType", "单项清除时的资源类型", null);
        ToolSchemas.required(schema, "bookId", "scope");
        definition = new ToolDefinition("ledger.recycle.purge.prepare", 1,
                "冻结永久清除快照并创建 R4 站内审批，不直接清除数据。",
                ToolRisk.R4, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String scope = ToolInputs.requiredText(input, "scope").toUpperCase();
        if (!"ITEM".equals(scope) && !"BOOK".equals(scope)) {
            throw new IllegalArgumentException("scope 只支持 ITEM 或 BOOK");
        }
        List<RecycleItem> all = recycle.all(bookId);
        List<RecycleItem> selected;
        if ("ITEM".equals(scope)) {
            String itemId = ToolInputs.requiredText(input, "itemId");
            String type = ToolInputs.requiredText(input, "resourceType");
            selected = List.of(all.stream()
                    .filter(value -> value.id().equals(itemId) && value.type().name().equalsIgnoreCase(type))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("回收站项目不存在")));
        } else {
            if (all.isEmpty()) throw new IllegalArgumentException("回收站为空，无需清除");
            selected = all;
        }

        ObjectNode snapshot = mapper.createObjectNode().put("bookId", bookId).put("scope", scope);
        ArrayNode frozen = snapshot.putArray("items");
        selected.forEach(item -> frozen.add(mapper.createObjectNode().put("type", item.type().name())
                .put("id", item.id()).put("revision", item.revision())));
        PendingAction action = actions.prepare(currentUser.id(), definition, snapshot,
                selected.size() == 1 ? selected.get(0).revision() : null, false, Duration.ofMinutes(30));

        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.recycle.purge");
        content.put("resourceType", "recycle");
        content.put("operation", "purge");
        content.put("commitAvailable", false);
        content.put("webApprovalRequired", true);
        content.set("input", snapshot);
        ObjectNode preview = content.putObject("preview");
        preview.put("scope", scope).put("count", selected.size());
        Map<String, Long> counts = new LinkedHashMap<>();
        selected.forEach(item -> counts.merge(item.type().name(), 1L, Long::sum));
        preview.set("countsByType", mapper.valueToTree(counts));
        preview.set("items", mapper.valueToTree(selected.stream().limit(50).toList()));
        preview.put("truncated", selected.size() > 50);
        content.set("effects", mapper.valueToTree(List.of(
                "永久清除后无法从回收站恢复",
                "历史引用可能按现有规则匿名化，而不是直接物理删除",
                "审批只清除当前冻结的 " + selected.size() + " 个项目，不包含审批期间新增的回收站项目")));
        AgentApproval approval = approvals.create(action, bookId,
                "永久清除 " + selected.size() + " 个回收站项目", content);
        content.put("approvalId", approval.id());
        return new ToolResult(com.salarytracker.ai.tool.ToolStatus.NEEDS_CONFIRMATION,
                "已创建永久清除审批，请前往站内审批中心处理", content, action.id(),
                "/approvals/" + approval.id(), action.expiresAt().toString(), null);
    }
}
