package com.salarytracker.ai.tool.ledger;

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
import com.salarytracker.ledger.LedgerModels.CalendarRule;
import com.salarytracker.ledger.LedgerModels.ScheduledTaskCommand;
import com.salarytracker.ledger.LedgerModels.TransactionCommand;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import com.salarytracker.ledger.LedgerScheduledTaskService;
import com.salarytracker.platform.ConflictException;

import java.time.LocalDate;
import java.util.Set;

final class LedgerScheduleCommitTool implements DomainTool {
    private final LedgerScheduleToolMode mode;
    private final LedgerScheduledTaskService schedules;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerScheduleCommitTool(LedgerScheduleToolMode mode, LedgerScheduledTaskService schedules,
                             PendingActionService actions, CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.mode = mode;
        this.schedules = schedules;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已批准的 pending action ID", null);
        ToolSchemas.required(schema, "actionId");
        definition = new ToolDefinition(mode.actionType() + ".commit", 1,
                "提交已批准的周期任务操作；提交前再次校验用户、账本权限、revision 和 action 状态。",
                mode == LedgerScheduleToolMode.CREATE ? ToolRisk.R2 : ToolRisk.R3,
                Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String actionId = ToolInputs.requiredText(input, "actionId");
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (!(mode.actionType() + ".prepare").equals(action.toolName()) || action.toolVersion() != 1) {
            throw new IllegalArgumentException("action 与当前工具不匹配");
        }
        actions.beginCommit(actionId, userId);
        try {
            JsonNode values = mapper.readTree(action.inputSnapshot());
            String bookId = text(values, "bookId");
            String revision = action.expectedRevision() == null ? null : String.valueOf(action.expectedRevision());
            Object result = switch (mode) {
                case CREATE -> schedules.create(bookId, command(values));
                case UPDATE -> schedules.update(bookId, text(values, "taskId"), command(values), revision);
                case DELETE -> schedules.delete(bookId, text(values, "taskId"), revision);
                case RUN -> schedules.run(bookId, text(values, "taskId"), revision);
            };
            actions.transition(actionId, userId, ActionStatus.COMPLETED);
            return ToolResult.completed(mode.label() + "已完成", mapper.valueToTree(result));
        } catch (ConflictException exception) {
            actions.transition(actionId, userId, ActionStatus.CONFLICT);
            return ToolResult.conflict(exception.getMessage(), mapper.createObjectNode()
                    .put("actionId", actionId).put("latestRevision", exception.getServerRevision()), actionId);
        } catch (RuntimeException exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed(exception.getMessage(), mapper.createObjectNode().put("actionId", actionId), actionId);
        } catch (Exception exception) {
            actions.transition(actionId, userId, ActionStatus.FAILED);
            return ToolResult.failed("无法读取 action 参数快照", mapper.createObjectNode().put("actionId", actionId), actionId);
        }
    }

    private ScheduledTaskCommand command(JsonNode values) {
        CalendarRule rule = new CalendarRule(optional(values, "monthlyMode"), integer(values, "weekOfMonth"),
                integer(values, "dayOfWeek"), integer(values, "dayOfMonth"), integer(values, "month"));
        TransactionCommand payload = new TransactionCommand(null, text(values, "accountId"), null,
                optional(values, "categoryId"), optional(values, "merchantId"), optional(values, "memberId"),
                optional(values, "projectId"), TransactionKind.valueOf(text(values, "kind")),
                values.path("amount").decimalValue(), "CNY", null, null, null, null,
                optional(values, "note"), "scheduled-task", null, null, null);
        return new ScheduledTaskCommand(null, "RECURRING_TRANSACTION", text(values, "name"),
                booleanValue(values, "enabled"), text(values, "scheduleMode"), text(values, "frequency"),
                integer(values, "intervalValue"), rule, LocalDate.parse(text(values, "startOn")),
                date(values, "endOn"), integer(values, "maxRuns"), payload);
    }

    private String text(JsonNode values, String field) {
        String value = optional(values, field);
        if (value == null) throw new IllegalArgumentException(field + " 必填");
        return value;
    }

    private String optional(JsonNode values, String field) {
        JsonNode value = values.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer integer(JsonNode values, String field) {
        JsonNode value = values.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asInt();
    }

    private Boolean booleanValue(JsonNode values, String field) {
        JsonNode value = values.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private LocalDate date(JsonNode values, String field) {
        String value = optional(values, field);
        return value == null ? null : LocalDate.parse(value);
    }
}
