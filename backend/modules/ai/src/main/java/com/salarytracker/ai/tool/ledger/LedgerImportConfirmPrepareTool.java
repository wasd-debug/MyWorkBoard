package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.salarytracker.ledger.LedgerImportService;
import com.salarytracker.ledger.LedgerModels.ImportPreview;

import java.time.Duration;
import java.util.List;
import java.util.Set;

final class LedgerImportConfirmPrepareTool implements DomainTool {
    private final LedgerImportService imports;
    private final PendingActionService actions;
    private final AgentApprovalService approvals;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerImportConfirmPrepareTool(LedgerImportService imports, PendingActionService actions,
                                   AgentApprovalService approvals, CurrentUserResolver currentUser,
                                   ObjectMapper mapper) {
        this.imports = imports;
        this.actions = actions;
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "batchId", "已生成的导入预览批次 ID", null);
        ToolSchemas.stringProperty(schema, "duplicateStrategy", "重复流水策略，当前仅支持 SKIP", null);
        ToolSchemas.required(schema, "bookId", "batchId");
        definition = new ToolDefinition("ledger.import.confirm.prepare", 1,
                "为已解析的账本导入批次创建 R4 站内审批申请。", ToolRisk.R4,
                Set.of("ledger:import"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String batchId = ToolInputs.requiredText(input, "batchId");
        String duplicateStrategy = ToolInputs.optionalText(input, "duplicateStrategy");
        if (duplicateStrategy == null) duplicateStrategy = "SKIP";
        if (!"SKIP".equalsIgnoreCase(duplicateStrategy)) {
            throw new IllegalArgumentException("当前只支持跳过重复流水");
        }
        ImportPreview preview = imports.getPreview(bookId, batchId);
        ObjectNode normalized = mapper.createObjectNode()
                .put("bookId", bookId)
                .put("batchId", batchId)
                .put("duplicateStrategy", "SKIP");
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, null,
                false, Duration.ofMinutes(30));
        ObjectNode payload = mapper.createObjectNode();
        payload.put("actionType", "ledger.import.confirm");
        payload.set("input", normalized);
        payload.set("preview", mapper.valueToTree(preview));
        payload.set("effects", mapper.valueToTree(List.of(
                "只写入预览中状态为 VALID 的流水",
                "重复流水默认跳过，错误行不会写入",
                "任一关键校验失败时整批回滚",
                "批准前会重新校验用户、账本权限和批次有效期")));
        AgentApproval approval = approvals.create(action, bookId,
                "确认导入“" + preview.filename() + "”中的 " + preview.validCount() + " 笔有效流水", payload);
        payload.put("approvalId", approval.id());
        payload.put("webApprovalRequired", true);
        payload.put("commitAvailable", false);
        String confirmationUrl = "/approvals/" + approval.id();
        return new ToolResult(com.salarytracker.ai.tool.ToolStatus.NEEDS_CONFIRMATION,
                "已创建高风险导入审批，请前往站内审批中心处理", payload,
                action.id(), confirmationUrl, action.expiresAt().toString(), null);
    }
}
