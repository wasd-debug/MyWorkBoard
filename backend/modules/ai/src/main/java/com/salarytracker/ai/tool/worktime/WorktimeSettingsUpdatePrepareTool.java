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
import com.salarytracker.worktime.WorktimeModels.Settings;
import com.salarytracker.worktime.WorktimeService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

@Component
public class WorktimeSettingsUpdatePrepareTool implements DomainTool {
    private final WorktimeService worktime;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public WorktimeSettingsUpdatePrepareTool(WorktimeService worktime, PendingActionService actions,
                                             CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.worktime = worktime;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.numberProperty(schema, "salaryPre", "税前月薪", -0.01);
        ToolSchemas.numberProperty(schema, "salaryPost", "税后月薪", -0.01);
        ToolSchemas.enumProperty(schema, "basis", "时薪计算口径", "pre", "post");
        ToolSchemas.stringProperty(schema, "workStart", "标准上班时间 HH:mm", "time");
        ToolSchemas.stringProperty(schema, "workEnd", "标准下班时间 HH:mm", "time");
        ToolSchemas.integerProperty(schema, "lunchMin", "午休分钟数", 60, 0, 600);
        ToolSchemas.numberProperty(schema, "daysPerMonth", "每月计薪工作日", 0);
        ToolSchemas.booleanProperty(schema, "autoDays", "是否自动计算每月工作日");
        ToolSchemas.enumProperty(schema, "lunchScope", "午休修改对历史记录的影响范围", "NONE", "ALL", "FROM_DATE");
        ToolSchemas.stringProperty(schema, "fromDate", "按日期重算时的起始日期 yyyy-MM-dd", "date");
        definition = new ToolDefinition("worktime.settings.update.prepare", 1,
                "合并当前工时设置并生成差异预览；历史重算只生成影响预览，不立即写入。",
                ToolRisk.R3, Set.of("worktime:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        Settings before = worktime.readSettings();
        ObjectNode normalized = mapper.valueToTree(before);
        normalized.remove("salaries");
        copyIfPresent(input, normalized, "salaryPre", "salaryPost", "basis", "workStart", "workEnd",
                "lunchMin", "daysPerMonth", "autoDays", "lunchScope", "fromDate");
        if (!normalized.hasNonNull("lunchScope")) normalized.put("lunchScope", "NONE");
        if (normalized.path("lunchScope").asText().equals("FROM_DATE")) {
            String fromDate = ToolInputs.optionalDate(normalized, "fromDate");
            if (fromDate == null) return needsInput(normalized, before, "按日期重算时请补充起始日期");
            if (LocalDate.parse(fromDate).isAfter(LocalDate.now())) throw new IllegalArgumentException("重算起始日期不能晚于今天");
        }
        boolean supplied = input != null && input.fieldNames().hasNext();
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, before.revision(),
                !supplied, Duration.ofMinutes(15));
        ObjectNode content = content(normalized, before);
        if (!supplied) {
            content.putArray("missingFields").add("setting");
            return ToolResult.needsInput("请选择要修改的工时设置", content,
                    action.id(), action.expiresAt().toString());
        }
        return ToolResult.needsConfirmation(
                normalized.path("lunchScope").asText().equals("NONE") ? "请确认修改工时设置" : "请确认修改设置并重算历史工时",
                content, action.id(), action.expiresAt().toString());
    }

    private ToolResult needsInput(ObjectNode normalized, Settings before, String summary) {
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, before.revision(),
                true, Duration.ofMinutes(15));
        ObjectNode content = content(normalized, before);
        content.putArray("missingFields").add("fromDate");
        return ToolResult.needsInput(summary, content, action.id(), action.expiresAt().toString());
    }

    private ObjectNode content(ObjectNode normalized, Settings before) {
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "worktime.settings.update");
        content.set("input", normalized);
        content.set("preview", normalized.deepCopy());
        ArrayNode fields = content.putArray("fields");
        field(fields, "salaryPre", "税前月薪", "money", false);
        field(fields, "salaryPost", "税后月薪", "money", false);
        select(fields, "basis", "时薪口径", false, new String[][]{{"pre", "税前"}, {"post", "税后"}});
        field(fields, "workStart", "标准上班时间", "time", true);
        field(fields, "workEnd", "标准下班时间", "time", true);
        field(fields, "lunchMin", "午休（分钟）", "number", true);
        field(fields, "daysPerMonth", "每月工作日", "money", true);
        select(fields, "autoDays", "自动计算工作日", false, new String[][]{{"true", "开启"}, {"false", "关闭"}});
        select(fields, "lunchScope", "历史工时处理", true,
                new String[][]{{"NONE", "仅影响新记录"}, {"ALL", "重算全部历史"}, {"FROM_DATE", "从指定日期重算"}});
        field(fields, "fromDate", "重算起始日期", "date", false);
        ArrayNode diff = content.putArray("diff");
        addDiff(diff, "税前月薪", before.salaryPre(), normalized.get("salaryPre"));
        addDiff(diff, "税后月薪", before.salaryPost(), normalized.get("salaryPost"));
        addDiff(diff, "时薪口径", before.basis().value(), normalized.get("basis"));
        addDiff(diff, "标准上班时间", before.workStart(), normalized.get("workStart"));
        addDiff(diff, "标准下班时间", before.workEnd(), normalized.get("workEnd"));
        addDiff(diff, "午休", before.lunchMin(), normalized.get("lunchMin"));
        addDiff(diff, "每月工作日", before.daysPerMonth(), normalized.get("daysPerMonth"));
        addDiff(diff, "自动工作日", before.autoDays(), normalized.get("autoDays"));
        if (!"NONE".equals(normalized.path("lunchScope").asText())) {
            content.putArray("effects").add("将重新计算范围内工时记录的午休快照、加班分钟和实际时薪")
                    .add("提交前会再次校验工时设置 revision，冲突时不会覆盖最新设置");
        }
        return content;
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String... names) {
        if (source == null) return;
        for (String name : names) if (source.has(name)) target.set(name, source.get(name));
    }

    private void field(ArrayNode fields, String name, String label, String type, boolean required) {
        ObjectNode field = fields.addObject();
        field.put("name", name).put("label", label).put("type", type).put("required", required);
    }

    private void select(ArrayNode fields, String name, String label, boolean required, String[][] options) {
        ObjectNode field = fields.addObject();
        field.put("name", name).put("label", label).put("type", "select").put("required", required);
        ArrayNode values = field.putArray("options");
        for (String[] option : options) values.addObject().put("value", option[0]).put("label", option[1]);
    }

    private void addDiff(ArrayNode diff, String label, Object before, JsonNode after) {
        JsonNode beforeNode = mapper.valueToTree(before);
        if (beforeNode.isNumber() && after != null && after.isNumber()
                && beforeNode.decimalValue().compareTo(after.decimalValue()) == 0) return;
        String left = beforeNode.isNull() ? "" : beforeNode.asText();
        String right = after == null || after.isNull() ? "" : after.asText();
        if (!left.equals(right)) diff.addObject().put("label", label).put("before", left).put("after", right);
    }
}
