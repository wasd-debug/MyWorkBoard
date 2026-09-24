package com.salarytracker.ai.tool.worktime;

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
import com.salarytracker.worktime.WorktimeModels.RecordCommand;
import com.salarytracker.worktime.WorktimeModels.RecordPreview;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;
import com.salarytracker.worktime.WorktimeService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;

@Component
public class WorktimeRecordUpdatePrepareTool implements DomainTool {
    private final WorktimeService worktime;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public WorktimeRecordUpdatePrepareTool(WorktimeService worktime, PendingActionService actions,
                                           CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.worktime = worktime;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.integerProperty(schema, "recordId", "工时记录 ID", 0, 1, Integer.MAX_VALUE);
        ToolSchemas.stringProperty(schema, "date", "修改后的日期 yyyy-MM-dd；省略则保留原值", "date");
        ToolSchemas.stringProperty(schema, "start", "修改后的开始时间 HH:mm；省略则保留原值", "time");
        ToolSchemas.stringProperty(schema, "end", "修改后的结束时间 HH:mm；空字符串表示尚未下班", "time");
        ToolSchemas.integerProperty(schema, "rest", "修改后的额外休息分钟数", 0, 0, 600);
        ToolSchemas.stringProperty(schema, "note", "修改后的备注；省略则保留原值", null);
        ToolSchemas.required(schema, "recordId");
        definition = new ToolDefinition("worktime.record.update.prepare", 1,
                "读取当前用户的工时记录，生成修改差异与服务端重新计算预览；不写入数据。",
                ToolRisk.R3, Set.of("worktime:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        long recordId = input.path("recordId").asLong(0);
        if (recordId <= 0) throw new IllegalArgumentException("recordId 必须大于 0");
        WorkRecord original = worktime.record(recordId);
        String date = ToolInputs.optionalDate(input, "date");
        String start = optionalTime(input, "start", false);
        String end = optionalTime(input, "end", true);
        Integer rest = input.has("rest") && !input.path("rest").isNull()
                ? ToolInputs.integer(input, "rest", original.rest(), 0, 600) : null;
        String note = input.has("note") && !input.path("note").isNull() ? input.path("note").asText() : null;
        RecordCommand changes = new RecordCommand(date, start, end, rest, note);
        RecordPreview preview = worktime.previewUpdateRecord(recordId, changes);

        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("recordId", recordId);
        snapshot.put("date", preview.date()); snapshot.put("start", preview.start()); snapshot.put("end", preview.end());
        snapshot.put("rest", preview.rest()); snapshot.put("note", preview.note());
        PendingAction action = actions.prepare(currentUser.id(), definition, snapshot, original.revision(),
                false, Duration.ofMinutes(15));

        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "worktime.record.update");
        content.set("input", snapshot);
        content.set("original", mapper.valueToTree(original));
        content.set("preview", mapper.valueToTree(preview));
        ArrayNode fields = content.putArray("fields");
        field(fields, "date", "日期", "date", true);
        field(fields, "start", "开始时间", "time", true);
        field(fields, "end", "结束时间", "time", false);
        field(fields, "rest", "额外休息（分钟）", "number", false);
        field(fields, "note", "备注", "textarea", false);
        content.set("diff", diff(original, preview));
        return ToolResult.needsConfirmation("请确认修改工时记录", content,
                action.id(), action.expiresAt().toString());
    }

    private ArrayNode diff(WorkRecord original, RecordPreview preview) {
        ArrayNode rows = mapper.createArrayNode();
        change(rows, "日期", original.date(), preview.date());
        change(rows, "开始时间", original.start(), preview.start());
        change(rows, "结束时间", original.end(), preview.end());
        change(rows, "额外休息", original.rest() + " 分钟", preview.rest() + " 分钟");
        change(rows, "加班时间", original.overtimeMin() + " 分钟", preview.overtimeMin() + " 分钟");
        change(rows, "实际时薪", money(original.realHourlyWage()), money(preview.realHourlyWage()));
        change(rows, "备注", original.note(), preview.note());
        return rows;
    }

    private void change(ArrayNode rows, String label, Object before, Object after) {
        if (Objects.equals(String.valueOf(before), String.valueOf(after))) return;
        rows.addObject().put("label", label).put("before", before == null ? "" : String.valueOf(before))
                .put("after", after == null ? "" : String.valueOf(after));
    }

    private String money(java.math.BigDecimal value) { return value == null ? "" : "¥" + value.stripTrailingZeros().toPlainString(); }

    private String optionalTime(JsonNode input, String field, boolean allowBlank) {
        if (!input.has(field) || input.path(field).isNull()) return null;
        String value = input.path(field).asText().trim();
        if (value.isBlank() && allowBlank) return "";
        if (!value.matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException(field + " 必须是 HH:mm");
        try { return LocalTime.parse(value).toString(); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException(field + " 必须是 HH:mm"); }
    }

    private void field(ArrayNode fields, String name, String label, String type, boolean required) {
        fields.addObject().put("name", name).put("label", label).put("type", type).put("required", required);
    }
}
