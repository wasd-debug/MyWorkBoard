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
import com.salarytracker.worktime.WorktimeService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Component
public class WorktimeRecordCreatePrepareTool implements DomainTool {
    private final WorktimeService worktime;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public WorktimeRecordCreatePrepareTool(WorktimeService worktime, PendingActionService actions,
                                           CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.worktime = worktime;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "date", "工时日期，格式 yyyy-MM-dd", "date");
        ToolSchemas.stringProperty(schema, "start", "开始时间，格式 HH:mm", "time");
        ToolSchemas.stringProperty(schema, "end", "结束时间，格式 HH:mm；可留空表示尚未下班", "time");
        ToolSchemas.integerProperty(schema, "rest", "额外休息分钟数", 0, 0, 600);
        ToolSchemas.stringProperty(schema, "note", "备注", null);
        definition = new ToolDefinition("worktime.record.create.prepare", 1,
                "校验新增工时参数并生成预览；不写入工时数据。",
                ToolRisk.R2, Set.of("worktime:write"), schema);
    }

    @Override
    public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String date = ToolInputs.optionalDate(input, "date");
        String start = optionalTime(input, "start");
        String end = optionalTime(input, "end");
        int rest = ToolInputs.integer(input, "rest", 0, 0, 600);
        String note = ToolInputs.optionalText(input, "note");
        ObjectNode normalized = mapper.createObjectNode();
        put(normalized, "date", date);
        put(normalized, "start", start);
        put(normalized, "end", end);
        normalized.put("rest", rest);
        put(normalized, "note", note);
        ArrayNode missing = mapper.createArrayNode();
        if (date == null) missing.add("date");
        if (start == null) missing.add("start");
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, null,
                !missing.isEmpty(), Duration.ofMinutes(15));
        if (!missing.isEmpty()) {
            ObjectNode content = mapper.createObjectNode();
            content.set("missingFields", missing);
            content.set("input", normalized);
            return ToolResult.needsInput("请补充工时日期和开始时间", content,
                    action.id(), action.expiresAt().toString());
        }
        RecordPreview preview = worktime.previewCreateRecord(new RecordCommand(date, start, end, rest, note));
        return ToolResult.needsConfirmation("请确认新增工时记录", mapper.valueToTree(preview),
                action.id(), action.expiresAt().toString());
    }

    private String optionalTime(JsonNode input, String field) {
        String value = ToolInputs.optionalText(input, field);
        if (value == null) return null;
        if (!value.matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException(field + " 必须是 HH:mm");
        try {
            return LocalTime.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " 必须是 HH:mm");
        }
    }

    private void put(ObjectNode target, String field, String value) {
        if (value == null) target.putNull(field); else target.put(field, value);
    }
}
