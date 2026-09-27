package com.salarytracker.ai.tool.worktime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.worktime.WorktimeModels.Basis;
import com.salarytracker.worktime.WorktimeModels.LunchRecalculationScope;
import com.salarytracker.worktime.WorktimeModels.LunchUpdate;
import com.salarytracker.worktime.WorktimeModels.Settings;
import com.salarytracker.worktime.WorktimeModels.SettingsUpdate;
import com.salarytracker.worktime.WorktimeService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class WorktimeSettingsUpdateCommitTool implements DomainTool {
    private static final String PREPARE_TOOL = "worktime.settings.update.prepare";
    private final WorktimeService worktime;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public WorktimeSettingsUpdateCommitTool(WorktimeService worktime, PendingActionService actions,
                                            CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.worktime = worktime;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition("worktime.settings.update.commit", 1,
                "提交已批准的工时设置修改；历史重算与设置修改处于同一事务。",
                ToolRisk.R3, Set.of("worktime:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    @Transactional
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!PREPARE_TOOL.equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与当前工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode values = mapper.readTree(action.inputSnapshot());
            SettingsUpdate update = new SettingsUpdate(
                    ToolInputs.optionalDecimal(values, "salaryPre"), ToolInputs.optionalDecimal(values, "salaryPost"),
                    Basis.from(ToolInputs.optionalText(values, "basis")), ToolInputs.optionalText(values, "workStart"),
                    ToolInputs.optionalText(values, "workEnd"), values.hasNonNull("lunchMin") ? values.get("lunchMin").asInt() : null,
                    ToolInputs.optionalDecimal(values, "daysPerMonth"), booleanValue(values, "autoDays"), null);
            LunchRecalculationScope scope = LunchRecalculationScope.valueOf(values.path("lunchScope").asText("NONE"));
            String revision = String.valueOf(action.expectedRevision());
            Settings settings;
            ObjectNode result = mapper.createObjectNode();
            if (scope == LunchRecalculationScope.NONE) {
                settings = worktime.writeSettings(update, revision);
                result.set("settings", mapper.valueToTree(settings));
                result.put("recalculatedRecords", 0);
            } else {
                SettingsUpdate withoutLunch = new SettingsUpdate(update.salaryPre(), update.salaryPost(), update.basis(),
                        update.workStart(), update.workEnd(), null, update.daysPerMonth(), update.autoDays(), null);
                settings = worktime.writeSettings(withoutLunch, revision);
                var lunch = worktime.updateLunch(new LunchUpdate(values.path("lunchMin").asInt(), scope,
                        ToolInputs.optionalText(values, "fromDate")), String.valueOf(settings.revision()));
                result.set("settings", mapper.valueToTree(lunch.settings()));
                result.put("recalculatedRecords", lunch.recalculatedRecords());
                if (lunch.recalculatedFrom() != null) result.put("recalculatedFrom", lunch.recalculatedFrom());
            }
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed("工时设置已更新", result);
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId)
                    .put("latestRevision", exception.getServerRevision()), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取 action 参数快照", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }

    private Boolean booleanValue(JsonNode values, String field) {
        JsonNode value = values.get(field);
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.booleanValue();
        if (value.isTextual()) return Boolean.valueOf(value.asText());
        throw new IllegalArgumentException(field + " 必须是布尔值");
    }
}
