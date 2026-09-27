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
import com.salarytracker.ledger.LedgerImportService;
import com.salarytracker.ledger.LedgerModels.Book;
import com.salarytracker.ledger.LedgerModels.ImportPreview;

import java.time.Duration;
import java.util.List;
import java.util.Set;

final class LedgerImportPreviewPrepareTool implements DomainTool {
    private final LedgerBookService books;
    private final LedgerImportService imports;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerImportPreviewPrepareTool(LedgerBookService books, LedgerImportService imports,
                                   PendingActionService actions, CurrentUserResolver currentUser,
                                   ObjectMapper mapper) {
        this.books = books; this.imports = imports; this.actions = actions;
        this.currentUser = currentUser; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "batchId", "浏览器上传并解析后生成的临时批次 ID", null);
        definition = new ToolDefinition("ledger.import.preview.prepare", 1,
                "收集账本文件并展示导入解析结果；只生成临时预览，不写入流水。",
                ToolRisk.R2, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        List<Book> visible = books.books();
        String bookId = ToolInputs.optionalText(input, "bookId");
        if (bookId == null && visible.size() == 1) bookId = visible.get(0).id();
        String selectedBookId = bookId;
        String batchId = ToolInputs.optionalText(input, "batchId");
        ObjectNode normalized = mapper.createObjectNode(); ArrayNode fields = mapper.createArrayNode(); ArrayNode missing = mapper.createArrayNode();
        if (selectedBookId == null || visible.stream().noneMatch(item -> item.id().equals(selectedBookId))) {
            missing.add("bookId"); fields.add(bookField(visible));
        } else normalized.put("bookId", selectedBookId);
        if (batchId == null) {
            missing.add("batchId"); fields.add(fileField());
        } else normalized.put("batchId", batchId);

        ImportPreview preview = null;
        if (selectedBookId != null && batchId != null) preview = imports.getPreview(selectedBookId, batchId);
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, null,
                !missing.isEmpty(), Duration.ofMinutes(30));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.import.preview"); content.set("input", normalized);
        content.set("fields", fields); content.set("missingFields", missing);
        if (preview != null) {
            content.set("preview", mapper.valueToTree(preview));
            content.put("commitAvailable", false); content.put("webApprovalRequired", true);
            content.set("effects", mapper.valueToTree(List.of(
                    "本轮只完成文件解析和匹配预览，不会创建流水",
                    "有效、重复和错误行会分别展示",
                    "正式导入确认将在后续 R4 站内审批增量开放")));
        }
        if (!missing.isEmpty()) return ToolResult.needsInput("请选择 CSV、XLS 或 XLSX 文件生成导入预览", content,
                action.id(), action.expiresAt().toString());
        return ToolResult.needsConfirmation("导入预览已生成，本轮不会写入流水", content,
                action.id(), action.expiresAt().toString());
    }

    private ObjectNode fileField() {
        ObjectNode result = mapper.createObjectNode(); result.put("name", "importFile"); result.put("label", "账本文件");
        result.put("type", "file"); result.put("required", true); result.put("accept", ".csv,.xls,.xlsx"); return result;
    }

    private ObjectNode bookField(List<Book> values) {
        ObjectNode result = mapper.createObjectNode(); result.put("name", "bookId"); result.put("label", "账本");
        result.put("type", "entity-picker"); result.put("required", true); ArrayNode options = mapper.createArrayNode();
        values.forEach(item -> options.add(mapper.createObjectNode().put("value", item.id()).put("label", item.name()).put("description", "账本")));
        result.set("options", options); return result;
    }
}
