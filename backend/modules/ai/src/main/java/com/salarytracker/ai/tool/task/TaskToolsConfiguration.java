package com.salarytracker.ai.tool.task;

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
import com.salarytracker.task.TaskCommentService;
import com.salarytracker.task.TaskEfficiencyModels;
import com.salarytracker.task.TaskEfficiencyService;
import com.salarytracker.task.TaskEnhancementService;
import com.salarytracker.task.TaskModels;
import com.salarytracker.task.TaskService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Configuration
public class TaskToolsConfiguration {
    @Bean public DomainTool taskQueryTool(TaskService tasks, ObjectMapper mapper) {
        return taskQuery("task.query",
                "查询当前用户任务，可按清单（可多选）、状态、视图、标签、优先级、关键词或日期范围筛选；"
                        + "view=TRASH 查回收站、view=ABANDONED 查已放弃的任务。", tasks, mapper);
    }

    /** 旧名别名：外部客户端仍按 task.list.query 调用，下个版本删除。 */
    @Bean public DomainTool taskListQueryAliasTool(TaskService tasks, ObjectMapper mapper) {
        return taskQuery("task.list.query",
                "（旧名，等同 task.query）查询当前用户任务，可按清单、状态、视图、标签或日期范围筛选。", tasks, mapper);
    }

    private DomainTool taskQuery(String name, String description, TaskService tasks, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "listId", "任务清单公开 ID", null);
        ToolSchemas.arrayProperty(schema, "listIds", "多个清单公开 ID（最多 10 个）；提供后忽略 listId",
                mapper.createObjectNode().put("type", "string"), 1, 10);
        ToolSchemas.enumProperty(schema, "status", "状态", "OPEN", "COMPLETED");
        ToolSchemas.enumProperty(schema, "view", "视图；TRASH 只查回收站，ABANDONED 只查已放弃",
                "TODAY", "TOMORROW", "NEXT7", "INBOX", "ALL", "COMPLETED", "TRASH", "ABANDONED");
        ToolSchemas.stringProperty(schema, "tagId", "标签公开 ID", null);
        ToolSchemas.enumProperty(schema, "priority", "优先级", "NONE", "LOW", "MEDIUM", "HIGH");
        ToolSchemas.stringProperty(schema, "keyword", "标题/描述关键词，1-200 字", null);
        ToolSchemas.stringProperty(schema, "from", "开始日期 yyyy-MM-dd；与 to 同时提供时按日期范围查询", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期 yyyy-MM-dd（含）；与 from 同时提供", "date");
        ToolSchemas.integerProperty(schema, "page", "页码", 0, 0, 1000);
        ToolSchemas.integerProperty(schema, "size", "每页数量", 50, 1, 200);
        return read(name, description, schema, mapper, input -> {
            String from = text(input, "from");
            String to = text(input, "to");
            Instant dueFrom = null;
            Instant dueTo = null;
            if (from != null || to != null) {
                if (from == null || to == null) throw new IllegalArgumentException("from 与 to 必须同时提供");
                ZoneId zone = ZoneId.of(tasks.settings().timezone());
                LocalDate start = LocalDate.parse(from);
                LocalDate end = LocalDate.parse(to);
                if (end.isBefore(start)) throw new IllegalArgumentException("to 不能早于 from");
                dueFrom = start.atStartOfDay(zone).toInstant();
                dueTo = end.plusDays(1).atStartOfDay(zone).toInstant();
            }
            int page = integer(input, "page", 0);
            int size = integer(input, "size", 50);
            String status = text(input, "status");
            String view = text(input, "view");
            String tagId = text(input, "tagId");
            String priority = text(input, "priority");
            String keyword = text(input, "keyword");
            List<String> listIds = new java.util.ArrayList<>();
            if (input.path("listIds").isArray()) {
                input.path("listIds").forEach(node -> {
                    if (node.isTextual() && !node.asText().isBlank()) listIds.add(node.asText());
                });
            }
            if (listIds.isEmpty()) {
                return tasks.listTasks(text(input, "listId"), status, view, tagId, priority,
                        dueFrom, dueTo, keyword, page, size);
            }
            if (listIds.size() > 10) throw new IllegalArgumentException("listIds 最多 10 个");
            java.util.LinkedHashMap<String, TaskModels.TaskItem> merged = new java.util.LinkedHashMap<>();
            for (String listId : listIds) {
                tasks.listTasks(listId, status, view, tagId, priority, dueFrom, dueTo, keyword, page, size)
                        .items().forEach(item -> merged.putIfAbsent(item.publicId(), item));
            }
            List<TaskModels.TaskItem> items = merged.values().stream().limit(size).toList();
            return new TaskModels.TaskPage(items, 0, size, merged.size());
        });
    }

    @Bean public DomainTool taskTodayTool(TaskService tasks, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.integerProperty(schema, "size", "最多返回条数", 50, 1, 200);
        return read("task.today", "查询当前用户今天到期且未完成的任务（只含今天，不含逾期；当天到期无论时刻是否已过都不算逾期）。", schema, mapper,
                input -> tasks.todayTasks(integer(input, "size", 50)));
    }

    @Bean public DomainTool taskOverdueTool(TaskService tasks, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.integerProperty(schema, "size", "最多返回条数", 50, 1, 500);
        return read("task.overdue", "查询当前用户已逾期未完成的任务（到期日早于今天，按到期时间升序，最多 500 条）。", schema, mapper,
                input -> tasks.overdueTasks(integer(input, "size", 50)));
    }

    @Bean public DomainTool taskStatsSummaryTool(TaskEnhancementService enhancements, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "from", "开始日期 yyyy-MM-dd", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期 yyyy-MM-dd", "date");
        return read("task.stats.summary", "查询任务创建、完成、逾期完成、专注和分布统计。", schema, mapper,
                input -> enhancements.stats(text(input,"from"), text(input,"to")));
    }

    @Bean public DomainTool habitCheckinQueryTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "habitId", "习惯公开 ID；提供后返回该习惯的打卡统计", null);
        ToolSchemas.stringProperty(schema, "from", "打卡统计开始日期 yyyy-MM-dd", "date");
        ToolSchemas.stringProperty(schema, "to", "打卡统计结束日期 yyyy-MM-dd", "date");
        return read("habit.checkin.query", "（旧名，等同 habit.list / habit.stats）查询当前用户习惯清单，或按习惯查询打卡统计。", schema, mapper,
                input -> text(input, "habitId") == null ? efficiency.habits(false)
                        : efficiency.habitStats(text(input, "habitId"), text(input, "from"), text(input, "to")));
    }

    @Bean public DomainTool focusStatsQueryTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        return read("focus.stats.query", "查询当前用户今日、本周、本月专注统计。", ToolSchemas.object(mapper), mapper,
                input -> efficiency.focusStats());
    }

    @Bean public DomainTool taskGetTool(TaskService tasks, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "taskId", "任务公开 ID", null);
        ToolSchemas.required(schema, "taskId");
        return read("task.get", "按公开 ID 查询单条任务详情，附带所属清单名称与活动条数。", schema, mapper, input -> {
            TaskModels.TaskItem task = tasks.get(ToolInputs.requiredText(input, "taskId"));
            ObjectNode content = mapper.createObjectNode();
            content.set("task", mapper.valueToTree(task));
            row(content, "listName", listName(tasks, task.listId()));
            return content;
        });
    }

    @Bean public DomainTool taskSearchTool(TaskEnhancementService enhancements, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "keyword", "关键词，1-200 字", null);
        ToolSchemas.integerProperty(schema, "size", "任务、清单、标签各自最多返回条数", 20, 1, 50);
        ToolSchemas.required(schema, "keyword");
        return read("task.search", "按关键词搜索任务、清单和标签（匹配标题/描述与名称）。", schema, mapper, input -> {
            var result = enhancements.search(ToolInputs.requiredText(input, "keyword"));
            int size = integer(input, "size", 20);
            ObjectNode content = mapper.createObjectNode().put("query", result.query());
            content.set("tasks", mapper.valueToTree(result.tasks().stream().limit(size).toList()));
            content.set("lists", mapper.valueToTree(result.lists().stream().limit(size).toList()));
            content.set("tags", mapper.valueToTree(result.tags().stream().limit(size).toList()));
            return content;
        });
    }

    @Bean public DomainTool taskCalendarTool(TaskService tasks, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "from", "开始日期 yyyy-MM-dd（含）", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期 yyyy-MM-dd（含），与 from 最多相差 92 天", "date");
        ToolSchemas.required(schema, "from", "to");
        return read("task.calendar", "按日期区间查询日历条目（任务与习惯），区间最多 92 天。", schema, mapper, input -> {
            ZoneId zone = ZoneId.of(tasks.settings().timezone());
            LocalDate start = LocalDate.parse(ToolInputs.requiredText(input, "from"));
            LocalDate end = LocalDate.parse(ToolInputs.requiredText(input, "to"));
            if (end.isBefore(start)) throw new IllegalArgumentException("to 不能早于 from");
            if (end.isAfter(start.plusDays(92))) throw new IllegalArgumentException("日历区间不能超过 92 天");
            return tasks.calendarItems(start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant());
        });
    }

    @Bean public DomainTool taskSettingsGetTool(TaskService tasks, ObjectMapper mapper) {
        return read("task.settings.get", "查询当前用户任务域设置（时区、默认清单、默认视图、提醒与周起始日等）。",
                ToolSchemas.object(mapper), mapper, input -> tasks.settings());
    }

    @Bean public DomainTool taskSmartFiltersTool(TaskEnhancementService enhancements, ObjectMapper mapper) {
        return read("task.filters.list", "查询当前用户的智能清单（智能筛选）定义，含筛选条件。",
                ToolSchemas.object(mapper), mapper, input -> enhancements.filters());
    }

    @Bean public DomainTool habitListTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.booleanProperty(schema, "includeArchived", "是否包含已归档习惯，默认 false");
        return read("habit.list", "查询当前用户的习惯清单（目标次数、周期、打卡状态、连击等）。", schema, mapper,
                input -> efficiency.habits(input.path("includeArchived").asBoolean(false)));
    }

    @Bean public DomainTool habitStatsTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "habitId", "习惯公开 ID", null);
        ToolSchemas.stringProperty(schema, "from", "统计开始日期 yyyy-MM-dd", "date");
        ToolSchemas.stringProperty(schema, "to", "统计结束日期 yyyy-MM-dd", "date");
        ToolSchemas.required(schema, "habitId");
        return read("habit.stats", "查询单个习惯在日期区间内的打卡统计（完成率、连击、缺失等）。", schema, mapper,
                input -> efficiency.habitStats(ToolInputs.requiredText(input, "habitId"),
                        text(input, "from"), text(input, "to")));
    }

    @Bean public DomainTool habitCalendarTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "from", "开始日期 yyyy-MM-dd（含）", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期 yyyy-MM-dd（含），与 from 最多相差 92 天", "date");
        ToolSchemas.required(schema, "from", "to");
        return read("habit.calendar", "查询习惯日历：每天应打卡数与完成数，区间最多 92 天。", schema, mapper, input -> {
            LocalDate start = LocalDate.parse(ToolInputs.requiredText(input, "from"));
            LocalDate end = LocalDate.parse(ToolInputs.requiredText(input, "to"));
            if (end.isBefore(start)) throw new IllegalArgumentException("to 不能早于 from");
            if (end.isAfter(start.plusDays(92))) throw new IllegalArgumentException("日历区间不能超过 92 天");
            return efficiency.habitCalendar(start, end.plusDays(1));
        });
    }

    @Bean public DomainTool focusSessionsListTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "from", "开始日期 yyyy-MM-dd", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期 yyyy-MM-dd", "date");
        return read("focus.sessions.list", "查询专注记录，可按日期区间筛选（不传则取默认范围）。", schema, mapper,
                input -> efficiency.focusSessions(text(input, "from"), text(input, "to")));
    }

    @Bean public DomainTool focusCurrentTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        return read("focus.current", "查询当前正在进行的专注；没有进行中的专注时返回 null。",
                ToolSchemas.object(mapper), mapper, input -> efficiency.currentFocus());
    }

    @Bean public DomainTool countdownListTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        return read("countdown.list", "查询倒数日与倒数日分组（含内置发薪日、节假日项）。",
                ToolSchemas.object(mapper), mapper, input -> {
                    ObjectNode content = mapper.createObjectNode();
                    content.set("countdowns", mapper.valueToTree(efficiency.countdowns()));
                    content.set("groups", mapper.valueToTree(efficiency.countdownGroups()));
                    return content;
                });
    }

    @Bean public DomainTool taskCreatePrepareTool(TaskService tasks, PendingActionService actions,
                                            CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = taskSchema(mapper); ToolSchemas.required(schema, "title");
        ToolDefinition definition = definition("task.create.prepare", "校验单条任务并生成确认预览，不写入任务数据。", ToolRisk.R2, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            String title = ToolInputs.requiredText(input, "title");
            ObjectNode normalized = mapper.createObjectNode().put("title", title);
            copy(input, normalized, "listId", "description", "priority", "startAt", "dueAt", "timezone", "rrule", "recurrenceAnchor");
            copyRaw(input, normalized, "tagIds", "checklist", "parentId", "durationMinutes", "reminder");
            normalized.put("allDay", input.path("allDay").asBoolean(false));
            String zone = text(normalized, "timezone");
            if (zone == null) {
                var settings = tasks.settings();
                zone = settings == null || settings.timezone() == null ? "Asia/Shanghai" : settings.timezone();
            }
            normalizeTimes(normalized, zone, "startAt", "dueAt");
            validateReminder(normalized.path("reminder"), text(normalized, "dueAt"));
            return normalized;
        }, normalized -> {
            String title = normalized.path("title").asText("");
            String zone = text(normalized, "timezone");
            String listName = listName(tasks, text(normalized, "listId"));
            String due = readableTime(text(normalized, "dueAt"), zone);
            ObjectNode p = preview(mapper);
            row(p, "title", title);
            row(p, "listName", listName == null ? "收件箱" : listName);
            row(p, "startAt", readableTime(text(normalized, "startAt"), zone));
            row(p, "dueAt", due);
            if (normalized.path("allDay").asBoolean(false)) row(p, "allDay", "是");
            row(p, "priority", priorityLabel(text(normalized, "priority")));
            row(p, "description", text(normalized, "description"));
            row(p, "parentTitle", parentTitle(tasks, text(normalized, "parentId")));
            row(p, "durationMinutes", minutesLabel(normalized.path("durationMinutes")));
            row(p, "tagCount", countLabel(normalized.path("tagIds"), "个标签"));
            row(p, "checklistCount", countLabel(normalized.path("checklist"), "个检查项"));
            row(p, "reminder", reminderLabel(normalized.path("reminder")));
            String detail = java.util.stream.Stream.of(listName == null ? "收件箱" : listName, due)
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(" · "));
            return new Presentation("新建任务：" + title + (detail.isBlank() ? "" : "（" + detail + "）"),
                    mapper.createObjectNode().set("preview", p));
        });
    }

    @Bean public DomainTool taskCreateCommitTool(TaskService tasks, PendingActionService actions,
                                           CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.create.commit", "task.create.prepare", ToolRisk.R2, actions, currentUser, mapper,
                (snapshot, actionId) -> {
                    TaskModels.TaskItem created = tasks.create(command(snapshot), "agent-task-create-" + actionId);
                    applyReminder(tasks, created, snapshot.path("reminder"));
                    return created;
                });
    }

    /**
     * 提醒参数必须在 prepare 阶段校验完：commit 时事务已被标记回滚，
     * 抛出来的错误会被 UnexpectedRollbackException 盖掉，用户只看到 500。
     */
    private static void validateReminder(JsonNode reminder, String dueAt) {
        if (reminder == null || !reminder.isObject()) return;
        String kind = text(reminder, "kind");
        if (!"RELATIVE".equals(kind) && !"ABSOLUTE".equals(kind)) {
            throw new IllegalArgumentException("提醒 kind 只能是 RELATIVE 或 ABSOLUTE");
        }
        if ("RELATIVE".equals(kind)) {
            if (dueAt == null || dueAt.isBlank()) {
                throw new IllegalArgumentException("相对提醒要求任务有到期时间；没有到期时间时改用 ABSOLUTE，或先设置到期时间");
            }
            Integer offset = reminder.path("offsetMinutes").isInt() ? reminder.path("offsetMinutes").asInt() : null;
            if (offset == null || offset < 0 || offset > 10080) {
                throw new IllegalArgumentException("相对提醒需要 0-10080 分钟的提前量");
            }
        } else {
            String at = text(reminder, "remindAt");
            if (at == null || at.isBlank()) throw new IllegalArgumentException("绝对提醒需要 remindAt 时间");
            try { Instant.parse(at); } catch (RuntimeException ignored) {
                try { java.time.OffsetDateTime.parse(at); } catch (RuntimeException again) {
                    throw new IllegalArgumentException("remindAt 需要 ISO-8601 时间，例如 2026-11-12T07:00:00Z");
                }
            }
        }
    }

    /** 提醒没有单独的公开接口，复用批量操作的 REMINDER 动作新增一条站内提醒。 */
    private static void applyReminder(TaskService tasks, TaskModels.TaskItem task, JsonNode reminder) {
        if (reminder == null || !reminder.isObject()) return;
        String kind = text(reminder, "kind");
        if (kind == null) throw new IllegalArgumentException("提醒需要指定 kind：RELATIVE 或 ABSOLUTE");
        Integer offset = reminder.path("offsetMinutes").isInt() ? reminder.path("offsetMinutes").asInt() : null;
        tasks.batch(new TaskModels.BatchCommand(
                List.of(new TaskModels.BatchTarget(task.publicId(), task.revision())), "REMINDER", null, null,
                null, null, new TaskModels.ReminderCommand(kind, offset, utc(text(reminder, "remindAt"), null), "IN_APP",
                reminder.path("dailyUntilDone").asBoolean(false))));
    }

    @Bean public DomainTool taskCompletePrepareTool(TaskService tasks, PendingActionService actions,
                                              CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = idSchema(mapper); ToolDefinition definition = definition("task.complete.prepare",
                "校验单条任务完成操作并生成确认预览，不修改任务。", ToolRisk.R2, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var task = tasks.get(ToolInputs.requiredText(input,"taskId"));
            return taskSnapshot(mapper, task);
        }, normalized -> {
            String title = normalized.path("title").asText("");
            String listName = listName(tasks, text(normalized, "listId"));
            ObjectNode p = preview(mapper);
            row(p, "title", title);
            row(p, "listName", listName);
            row(p, "dueAt", readableTime(text(normalized, "dueAt"), text(normalized, "timezone")));
            return new Presentation("完成任务：" + title + (listName == null ? "" : "（" + listName + "）"),
                    mapper.createObjectNode().set("preview", p));
        });
    }

    @Bean public DomainTool taskCompleteCommitTool(TaskService tasks, PendingActionService actions,
                                             CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.complete.commit", "task.complete.prepare", ToolRisk.R2, actions, currentUser, mapper,
                (snapshot, actionId) -> tasks.complete(ToolInputs.requiredText(snapshot,"taskId"),
                        snapshot.path("revision").asText()));
    }

    @Bean public DomainTool taskReschedulePrepareTool(TaskService tasks, PendingActionService actions,
                                                CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = idSchema(mapper);
        ToolSchemas.stringProperty(schema,"startAt","新的开始时间 ISO-8601", "date-time");
        ToolSchemas.stringProperty(schema,"dueAt","新的到期时间 ISO-8601", "date-time");
        ToolSchemas.booleanProperty(schema,"allDay","是否全天");
        ToolDefinition definition = definition("task.reschedule.prepare", "校验单条任务改期并生成确认预览，不修改任务。", ToolRisk.R2, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var task = tasks.get(ToolInputs.requiredText(input,"taskId"));
            ObjectNode normalized = mapper.createObjectNode().put("taskId",task.publicId()).put("revision",task.revision())
                    .put("title",task.title()); copy(input, normalized, "startAt", "dueAt");
            normalized.put("allDay", input.has("allDay") ? input.path("allDay").asBoolean() : task.allDay());
            normalized.put("timezone", task.timezone());
            normalizeTimes(normalized, task.timezone(), "startAt", "dueAt");
            if (task.dueAt() != null) normalized.put("beforeDueAt", task.dueAt());
            if (task.startAt() != null) normalized.put("beforeStartAt", task.startAt());
            normalized.put("beforeAllDay", task.allDay());
            return normalized;
        }, normalized -> {
            String title = normalized.path("title").asText("");
            String zone = text(normalized, "timezone");
            String beforeDue = readableTime(text(normalized, "beforeDueAt"), zone);
            String afterDue = readableTime(text(normalized, "dueAt"), zone);
            String beforeStart = readableTime(text(normalized, "beforeStartAt"), zone);
            String afterStart = readableTime(text(normalized, "startAt"), zone);
            ObjectNode p = preview(mapper);
            row(p, "title", title);
            row(p, "listName", listName(tasks, text(normalized, "listId")));
            row(p, "startAt", afterStart);
            row(p, "dueAt", afterDue);
            if (normalized.path("allDay").asBoolean(false)) row(p, "allDay", "是");
            ObjectNode content = mapper.createObjectNode().set("preview", p);
            var diff = mapper.createArrayNode();
            if (!java.util.Objects.equals(beforeDue, afterDue)) {
                diff.add(mapper.createObjectNode().put("label", "到期时间")
                        .put("before", beforeDue == null ? "未设置" : beforeDue).put("after", afterDue == null ? "未设置" : afterDue));
            }
            if (!java.util.Objects.equals(beforeStart, afterStart)) {
                diff.add(mapper.createObjectNode().put("label", "开始时间")
                        .put("before", beforeStart == null ? "未设置" : beforeStart).put("after", afterStart == null ? "未设置" : afterStart));
            }
            if (diff.size() > 0) content.set("diff", diff);
            return new Presentation("任务改期：" + title, content);
        });
    }

    @Bean public DomainTool taskRescheduleCommitTool(TaskService tasks, PendingActionService actions,
                                               CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.reschedule.commit", "task.reschedule.prepare", ToolRisk.R2, actions, currentUser, mapper,
                (snapshot, actionId) -> tasks.reschedule(ToolInputs.requiredText(snapshot,"taskId"),
                        new TaskModels.RescheduleCommand(text(snapshot,"startAt"), text(snapshot,"dueAt"),
                                snapshot.path("allDay").asBoolean(), null), snapshot.path("revision").asText()));
    }

    @Bean public DomainTool taskUpdatePrepareTool(TaskService tasks, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema=idSchema(mapper);ToolSchemas.stringProperty(schema,"title","新标题",null);
        ToolSchemas.stringProperty(schema,"description","新描述",null);ToolSchemas.enumProperty(schema,"priority","新优先级","NONE","LOW","MEDIUM","HIGH");
        ToolSchemas.integerProperty(schema,"durationMinutes","新的预计时长（分钟）",30,0,1440);
        ToolSchemas.stringProperty(schema,"parentId","新的父任务公开 ID；传空串表示改为顶层任务",null);
        ToolSchemas.arrayProperty(schema,"tagIds","新的标签 ID 列表；提供时整体替换原标签",mapper.createObjectNode().put("type","string"),0,20);
        ToolSchemas.arrayProperty(schema,"checklist","新的检查项列表；提供时会整体替换原检查项，未提供的检查项会丢失",checklistSchema(mapper),0,50);
        ToolSchemas.objectProperty(schema,"reminder","新增一条站内提醒；只做新增，不覆盖已有提醒",reminderSchema(mapper));
        ToolDefinition definition=definition("task.update.prepare","校验单条任务字段修改并生成确认预览，不修改任务。",ToolRisk.R3,schema);
        return prepare(definition,actions,currentUser,mapper,input->{var task=tasks.get(ToolInputs.requiredText(input,"taskId"));
            String title=text(input,"title") == null ? task.title() : text(input,"title");String description=text(input,"description") == null ? task.description() : text(input,"description");
            String priority=text(input,"priority") == null ? task.priority() : text(input,"priority");
            // 三元两边一边 int 一边 Integer 会统一成 int，task.durationMinutes() 为 null 时直接拆箱 NPE
            Integer duration=input.path("durationMinutes").isInt() ? Integer.valueOf(input.path("durationMinutes").asInt()) : task.durationMinutes();
            String parentId=input.hasNonNull("parentId") ? text(input,"parentId") : task.parentId();
            List<String> tagIds=input.path("tagIds").isArray() ? stringList(input,"tagIds") : task.tagIds();
            List<TaskModels.ChecklistCommand> items=input.path("checklist").isArray() ? checklist(input)
                    : task.checklist().stream().map(item->new TaskModels.ChecklistCommand(item.publicId(),item.title(),item.completed(),item.sortOrder())).toList();
            TaskModels.TaskCommand command=new TaskModels.TaskCommand(task.listId(),title,description,priority,task.startAt(),task.dueAt(),task.allDay(),task.timezone(),duration,parentId,tagIds,items,
                    task.rrule(),task.recurrenceAnchor());
            ObjectNode normalized=mapper.createObjectNode().put("taskId",task.publicId()).put("revision",task.revision()).put("title",task.title());
            normalized.put("beforeTitle",task.title()); normalized.put("beforeDescription",task.description()); normalized.put("beforePriority",task.priority());
            normalized.put("beforeDurationMinutes",task.durationMinutes());
            normalized.put("beforeChecklistCount",task.checklist().size());
            normalized.put("beforeTagCount",task.tagIds().size());
            copyRaw(input, normalized, "reminder");
            validateReminder(normalized.path("reminder"), command.dueAt());
            return normalized.set("command",mapper.valueToTree(command));}, normalized -> {
            var command = normalized.path("command");
            String title = command.path("title").asText("");
            String description = command.path("description").isNull() ? null : command.path("description").asText(null);
            String priority = command.path("priority").isNull() ? null : command.path("priority").asText(null);
            String beforeTitle = text(normalized, "beforeTitle");
            String beforeDescription = text(normalized, "beforeDescription");
            String beforePriority = text(normalized, "beforePriority");
            ObjectNode p = preview(mapper);
            row(p, "title", title);
            row(p, "listName", listName(tasks, command.path("listId").asText(null)));
            row(p, "dueAt", readableTime(command.path("dueAt").isNull() ? null : command.path("dueAt").asText(null),
                    command.path("timezone").isNull() ? null : command.path("timezone").asText(null)));
            row(p, "priority", priorityLabel(priority));
            row(p, "description", description);
            row(p, "durationMinutes", minutesLabel(command.path("durationMinutes")));
            row(p, "tagCount", countLabel(command.path("tagIds"), "个标签"));
            row(p, "checklistCount", countLabel(command.path("checklist"), "个检查项"));
            row(p, "reminder", reminderLabel(normalized.path("reminder")));
            ObjectNode content = mapper.createObjectNode().set("preview", p);
            var diff = mapper.createArrayNode();
            if (!java.util.Objects.equals(beforeTitle, title)) diff.add(diffRow(mapper, "标题", beforeTitle, title));
            // 空描述有时是 null 有时是空串，直接比较会产出「未填写 → 未填写」这种噪音 diff
            if (!java.util.Objects.equals(blank(beforeDescription), blank(description))) {
                diff.add(diffRow(mapper, "描述", beforeDescription, description));
            }
            if (!java.util.Objects.equals(priorityLabel(beforePriority), priorityLabel(priority))) {
                diff.add(diffRow(mapper, "优先级", priorityLabel(beforePriority), priorityLabel(priority)));
            }
            int beforeChecklist = normalized.path("beforeChecklistCount").asInt(0);
            int afterChecklist = command.path("checklist").size();
            if (beforeChecklist != afterChecklist) {
                diff.add(diffRow(mapper, "检查项", beforeChecklist + " 项", afterChecklist + " 项"));
            }
            int beforeTags = normalized.path("beforeTagCount").asInt(0);
            int afterTags = command.path("tagIds").size();
            if (beforeTags != afterTags) diff.add(diffRow(mapper, "标签", beforeTags + " 个", afterTags + " 个"));
            String beforeDuration = minutesLabel(normalized.path("beforeDurationMinutes"));
            String afterDuration = minutesLabel(command.path("durationMinutes"));
            if (!java.util.Objects.equals(beforeDuration, afterDuration)) {
                diff.add(diffRow(mapper, "预计时长", beforeDuration, afterDuration));
            }
            if (diff.size() > 0) content.set("diff", diff);
            return new Presentation("修改任务：" + title, content);
        });
    }

    @Bean public DomainTool taskUpdateCommitTool(TaskService tasks, PendingActionService actions,
                                                 CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.update.commit","task.update.prepare",ToolRisk.R3,actions,currentUser,mapper,(snapshot,actionId)->{
            TaskModels.TaskItem updated = tasks.update(ToolInputs.requiredText(snapshot,"taskId"),
                    mapper.convertValue(snapshot.path("command"),TaskModels.TaskCommand.class),snapshot.path("revision").asText());
            applyReminder(tasks, updated, snapshot.path("reminder"));
            return updated;
        });
    }

    @Bean public DomainTool taskDeletePrepareTool(TaskService tasks, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema=idSchema(mapper);ToolDefinition definition=definition("task.delete.prepare","校验单条任务删除并生成确认预览，不修改任务。",ToolRisk.R3,schema);
        return prepare(definition,actions,currentUser,mapper,input->{
            var task=tasks.get(ToolInputs.requiredText(input,"taskId"));
            ObjectNode normalized=taskSnapshot(mapper,task);
            normalized.put("subtaskCount", task.totalSubtasks());
            return normalized;}, normalized -> {
            String title = normalized.path("title").asText("");
            String listName = listName(tasks, text(normalized, "listId"));
            ObjectNode p = preview(mapper);
            row(p, "title", title);
            row(p, "listName", listName);
            row(p, "dueAt", readableTime(text(normalized, "dueAt"), text(normalized, "timezone")));
            int subtasks = normalized.path("subtaskCount").asInt(0);
            if (subtasks > 0) row(p, "subtasks", subtasks + " 个子任务");
            return new Presentation("删除任务：" + title + (listName == null ? "" : "（" + listName + "）"),
                    mapper.createObjectNode().set("preview", p));
        });
    }

    @Bean public DomainTool taskDeleteCommitTool(TaskService tasks, PendingActionService actions,
                                                 CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.delete.commit","task.delete.prepare",ToolRisk.R3,actions,currentUser,mapper,(snapshot,actionId)->
                tasks.delete(ToolInputs.requiredText(snapshot,"taskId"),snapshot.path("revision").asText()));
    }

    @Bean public DomainTool taskReopenPrepareTool(TaskService tasks, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema=idSchema(mapper);
        ToolDefinition definition=definition("task.reopen.prepare",
                "把已完成或已放弃的任务改回未完成并生成确认预览，不修改任务。",ToolRisk.R2,schema);
        return prepare(definition,actions,currentUser,mapper,input->{
            var task=tasks.get(ToolInputs.requiredText(input,"taskId"));
            if("OPEN".equals(task.status())) throw new IllegalArgumentException("任务当前是未完成状态，不需要重开");
            ObjectNode normalized=taskSnapshot(mapper,task).put("status",task.status());
            if(task.completedAt()!=null) normalized.put("completedAt",task.completedAt());
            return normalized;
        }, normalized -> {
            String title=normalized.path("title").asText("");
            String listName=listName(tasks,text(normalized,"listId"));
            String zone=text(normalized,"timezone");
            String status=text(normalized,"status");
            ObjectNode p=preview(mapper);
            row(p,"title",title);
            row(p,"listName",listName);
            row(p,"status",statusLabel(status));
            row(p,"completedAt",readableTime(text(normalized,"completedAt"),zone));
            ObjectNode content=mapper.createObjectNode().set("preview",p);
            content.set("diff",mapper.createArrayNode().add(diffRow(mapper,"状态",statusLabel(status),"未完成")));
            return new Presentation("重开任务："+title+(listName==null?"":"（"+listName+"）"),content);
        });
    }

    @Bean public DomainTool taskReopenCommitTool(TaskService tasks, PendingActionService actions,
                                                 CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.reopen.commit","task.reopen.prepare",ToolRisk.R2,actions,currentUser,mapper,(snapshot,actionId)->
                tasks.reopen(ToolInputs.requiredText(snapshot,"taskId"),snapshot.path("revision").asText()));
    }

    @Bean public DomainTool taskAbandonPrepareTool(TaskService tasks, PendingActionService actions,
                                                   CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema=idSchema(mapper);
        ToolSchemas.stringProperty(schema,"reason","放弃原因（可选，仅用于预览展示）",null);
        ToolDefinition definition=definition("task.abandon.prepare",
                "把任务标记为已放弃（不做但不算逾期）并生成确认预览，不修改任务。放弃后可再用 task.reopen 恢复。",ToolRisk.R2,schema);
        return prepare(definition,actions,currentUser,mapper,input->{
            var task=tasks.get(ToolInputs.requiredText(input,"taskId"));
            if("ABANDONED".equals(task.status())) throw new IllegalArgumentException("任务已经是已放弃状态");
            ObjectNode normalized=taskSnapshot(mapper,task).put("status",task.status());
            copy(input,normalized,"reason");
            return normalized;
        }, normalized -> {
            String title=normalized.path("title").asText("");
            String listName=listName(tasks,text(normalized,"listId"));
            ObjectNode p=preview(mapper);
            row(p,"title",title);
            row(p,"listName",listName);
            row(p,"dueAt",readableTime(text(normalized,"dueAt"),text(normalized,"timezone")));
            row(p,"reason",text(normalized,"reason"));
            return new Presentation("放弃任务："+title+(listName==null?"":"（"+listName+"）"),
                    mapper.createObjectNode().set("preview",p));
        });
    }

    @Bean public DomainTool taskAbandonCommitTool(TaskService tasks, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.abandon.commit","task.abandon.prepare",ToolRisk.R2,actions,currentUser,mapper,(snapshot,actionId)->
                tasks.abandon(ToolInputs.requiredText(snapshot,"taskId"),snapshot.path("revision").asText()));
    }

    @Bean public DomainTool taskBatchCompletePrepareTool(TaskService tasks, PendingActionService actions,
                                                         CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema=ToolSchemas.object(mapper);
        ToolSchemas.arrayProperty(schema,"taskIds","要标记完成的任务公开 ID 列表，最多 100 个",mapper.createObjectNode().put("type","string"),1,100);
        ToolSchemas.required(schema,"taskIds");
        ToolDefinition definition=definition("task.batch.complete.prepare",
                "批量把多个任务标记完成并生成确认预览，不修改任务。已完成的会被跳过，不会重复完成。",ToolRisk.R2,schema);
        return prepare(definition,actions,currentUser,mapper,input->{
            var ids=new java.util.LinkedHashSet<String>(stringList(input,"taskIds"));
            if(ids.isEmpty()) throw new IllegalArgumentException("taskIds 不能为空");
            if(ids.size()>100) throw new IllegalArgumentException("一次最多完成 100 个任务");
            var items=mapper.createArrayNode();
            var titles=new java.util.ArrayList<String>();
            for(String id:ids){
                var task=tasks.get(id);
                // 已完成的任务再走一次 changeStatus 会让重复任务多生成一个下一实例，必须跳过
                if("COMPLETED".equals(task.status())) continue;
                items.add(mapper.createObjectNode().put("taskId",task.publicId()).put("revision",task.revision())
                        .put("title",task.title()).put("listId",task.listId()==null?"":task.listId()));
                titles.add(task.title());
            }
            if(items.isEmpty()) throw new IllegalArgumentException("这些任务都已完成，无需重复完成");
            ObjectNode normalized=mapper.createObjectNode().put("count",items.size());
            normalized.set("items",items);
            normalized.set("titles",mapper.valueToTree(titles));
            return normalized;
        }, normalized -> {
            var items=normalized.path("items");
            var titles=new java.util.ArrayList<String>();
            for(JsonNode item:items) titles.add(item.path("title").asText(""));
            String shown=String.join("、",titles.subList(0,Math.min(5,titles.size())))
                    +(titles.size()>5?" 等 "+titles.size()+" 个":"");
            ObjectNode p=preview(mapper);
            row(p,"count",items.size()+" 个任务");
            row(p,"tasks",shown);
            return new Presentation("批量完成 "+items.size()+" 个任务",
                    mapper.createObjectNode().set("preview",p));
        });
    }

    @Bean public DomainTool taskBatchCompleteCommitTool(TaskService tasks, PendingActionService actions,
                                                        CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.batch.complete.commit","task.batch.complete.prepare",ToolRisk.R2,actions,currentUser,mapper,
                (snapshot,actionId)->{
                    var targets=new java.util.ArrayList<TaskModels.BatchTarget>();
                    for(JsonNode item:snapshot.path("items")){
                        targets.add(new TaskModels.BatchTarget(item.path("taskId").asText(),item.path("revision").asLong()));
                    }
                    return tasks.batch(new TaskModels.BatchCommand(targets,"COMPLETE",null,null,null,null,null));
                });
    }

    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "COMPLETED" -> "已完成"; case "ABANDONED" -> "已放弃"; case "OPEN" -> "未完成"; default -> null;
        };
    }

    @Bean public DomainTool habitCheckinPrepareTool(TaskEfficiencyService efficiency, TaskService tasks,
                                                    PendingActionService actions, CurrentUserResolver currentUser,
                                                    ObjectMapper mapper) {
        ObjectNode schema=ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema,"habitId","习惯公开 ID；先用 habit.list 取得",null);
        ToolSchemas.stringProperty(schema,"date","打卡日期 yyyy-MM-dd，默认今天；不能早于习惯开始日期、不能是未来","date");
        ToolSchemas.enumProperty(schema,"status",
                "打卡状态：DONE 完成（默认）；SKIP 主动跳过（不断连击，不进完成率分母）；MISS 未完成（会断连击）；CLEAR 取消该日打卡（等价于没有记录，服务端不支持删除）",
                "DONE","SKIP","MISS","CLEAR");
        ToolSchemas.integerProperty(schema,"count","当天打卡次数，仅 DONE 有效，默认 1",1,1,999);
        ToolSchemas.integerProperty(schema,"amount","完成量，仅对按量习惯有效",0,0,999999);
        ToolSchemas.stringProperty(schema,"mood","心情，最长 16 字",null);
        ToolSchemas.stringProperty(schema,"note","心得，最长 500 字",null);
        ToolSchemas.stringProperty(schema,"skipReason","跳过原因，仅 SKIP 时保存",null);
        ToolSchemas.required(schema,"habitId");
        ToolDefinition definition=definition("habit.checkin.prepare",
                "给某个习惯打卡或改打卡状态并生成确认预览，不写入数据。取消打卡请用 status=CLEAR，不要尝试删除。",
                ToolRisk.R2,schema);
        return prepare(definition,actions,currentUser,mapper,input->{
            String habitId=ToolInputs.requiredText(input,"habitId");
            var habit=findHabit(efficiency,habitId);
            if(habit.archived()) throw new IllegalArgumentException("习惯「"+habit.name()+"」已归档，不能打卡");
            ZoneId zone=ZoneId.of(tasks.settings().timezone());
            LocalDate today=LocalDate.now(zone);
            String rawDate=text(input,"date");
            LocalDate date=rawDate==null?today:LocalDate.parse(rawDate);
            if(date.isAfter(today)) throw new IllegalArgumentException("不能给未来日期打卡");
            if(date.isBefore(LocalDate.parse(habit.startDate()))) throw new IllegalArgumentException("打卡日期不能早于习惯开始日期 "+habit.startDate());
            String status=text(input,"status")==null?"DONE":text(input,"status");
            ObjectNode normalized=mapper.createObjectNode().put("habitId",habitId).put("habitName",habit.name())
                    .put("date",date.toString()).put("status",status);
            copy(input,normalized,"mood","note","skipReason");
            copyRaw(input,normalized,"count","amount");
            return normalized;
        }, normalized -> {
            String status=text(normalized,"status");
            ObjectNode p=preview(mapper);
            row(p,"habit",text(normalized,"habitName"));
            row(p,"date",text(normalized,"date"));
            row(p,"status",checkinLabel(status));
            if("DONE".equals(status)){
                row(p,"count",normalized.path("count").isInt()?normalized.path("count").asInt()+" 次":null);
                row(p,"amount",normalized.path("amount").isInt()?String.valueOf(normalized.path("amount").asInt()):null);
            }
            row(p,"mood",text(normalized,"mood"));
            row(p,"note",text(normalized,"note"));
            row(p,"skipReason",text(normalized,"skipReason"));
            ObjectNode content=mapper.createObjectNode().set("preview",p);
            String summary=switch (status) {
                case "SKIP" -> "跳过打卡：" + text(normalized,"habitName");
                case "MISS" -> "记为未完成：" + text(normalized,"habitName");
                case "CLEAR" -> "取消打卡：" + text(normalized,"habitName");
                default -> "习惯打卡：" + text(normalized,"habitName") + "（" + text(normalized,"date") + "）";
            };
            return new Presentation(summary,content);
        });
    }

    @Bean public DomainTool habitCheckinCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                   CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("habit.checkin.commit","habit.checkin.prepare",ToolRisk.R2,actions,currentUser,mapper,
                (snapshot,actionId)->efficiency.checkin(ToolInputs.requiredText(snapshot,"habitId"),
                        new TaskEfficiencyModels.HabitCheckinCommand(text(snapshot,"date"),
                                snapshot.path("count").isInt()?snapshot.path("count").asInt():null,
                                text(snapshot,"status"),
                                snapshot.path("amount").isInt()?snapshot.path("amount").asInt():null,
                                text(snapshot,"mood"),text(snapshot,"note"),text(snapshot,"skipReason"))));
    }

    private static String checkinLabel(String status) {
        return switch (status == null ? "" : status) {
            case "DONE" -> "已完成"; case "SKIP" -> "跳过（不断连击）"; case "MISS" -> "未完成（断连击）";
            case "CLEAR" -> "取消打卡"; default -> status;
        };
    }

    /**
     * habits(boolean) 是「只看归档 / 只看未归档」，一次查不到，必须查两次才能区分「不存在」和「已归档」。
     */
    private static TaskEfficiencyModels.Habit findHabit(TaskEfficiencyService efficiency, String habitId) {
        for (var habit : efficiency.habits(false)) if (habitId.equals(habit.publicId())) return habit;
        for (var habit : efficiency.habits(true)) if (habitId.equals(habit.publicId())) return habit;
        throw new IllegalArgumentException("习惯不存在：" + habitId);
    }

    @Bean public DomainTool focusStartPrepareTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema=ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema,"taskId","关联任务公开 ID（可选）",null);
        ToolSchemas.stringProperty(schema,"habitId","关联习惯公开 ID（可选；与 taskId 同时给时以习惯为准）",null);
        ToolSchemas.integerProperty(schema,"plannedMinutes","计划时长（分钟），0 表示正计时；补记时必须大于 0",25,0,180);
        ToolSchemas.stringProperty(schema,"startedAt","补记时的开始时刻 ISO-8601；与 endedAt 一起给表示按真实起止时间补记","date-time");
        ToolSchemas.stringProperty(schema,"endedAt","补记时的结束时刻 ISO-8601","date-time");
        ToolSchemas.stringProperty(schema,"requestedDate","仅补记某天时给日期 yyyy-MM-dd","date");
        ToolSchemas.stringProperty(schema,"note","这轮专注要做的事（可选）",null);
        ToolDefinition definition=definition("focus.start.prepare",
                "开始一轮专注（或补记一段专注）并生成确认预览，不写入数据。同一时间只能有一个进行中的专注。",ToolRisk.R2,schema);
        return prepare(definition,actions,currentUser,mapper,input->{
            var running=efficiency.currentFocus();
            if(running!=null) throw new IllegalArgumentException("已有进行中的专注（"+focusTargetLabel(running)+"），请先结束再开始新的专注");
            // 自由专注也允许：两个都不传表示不关联对象，服务端支持
            String habitId=text(input,"habitId");
            String taskId=text(input,"taskId");
            if(habitId!=null){ findHabit(efficiency,habitId); taskId=null; }
            ObjectNode normalized=mapper.createObjectNode();
            if(taskId!=null) normalized.put("taskId",taskId);
            if(habitId!=null) normalized.put("habitId",habitId);
            copy(input,normalized,"startedAt","endedAt","requestedDate","note");
            copyRaw(input,normalized,"plannedMinutes");
            normalizeTimes(normalized, null, "startedAt", "endedAt");
            return normalized;
        }, normalized -> {
            ObjectNode p=preview(mapper);
            row(p,"target",focusTargetLabel(normalized));
            row(p,"plannedMinutes",minutesLabel(normalized.path("plannedMinutes")));
            String startedAt=text(normalized,"startedAt"),endedAt=text(normalized,"endedAt");
            if(startedAt!=null&&endedAt!=null){
                row(p,"period",readableTime(startedAt,null)+" → "+readableTime(endedAt,null));
            } else if(text(normalized,"requestedDate")!=null){
                row(p,"date",text(normalized,"requestedDate")+"（补记）");
            } else {
                row(p,"mode","正计时".equals(minutesLabel(normalized.path("plannedMinutes")))?"立即开始":"立即开始计时");
            }
            row(p,"note",text(normalized,"note"));
            String summary=startedAt!=null&&endedAt!=null?"补记专注："+focusTargetLabel(normalized)
                    :"开始专注："+focusTargetLabel(normalized);
            return new Presentation(summary,mapper.createObjectNode().set("preview",p));
        });
    }

    @Bean public DomainTool focusStartCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                 CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("focus.start.commit","focus.start.prepare",ToolRisk.R2,actions,currentUser,mapper,
                (snapshot,actionId)->efficiency.startFocus(new TaskEfficiencyModels.FocusStartCommand(
                        text(snapshot,"taskId"),text(snapshot,"habitId"),
                        snapshot.path("plannedMinutes").isInt()?snapshot.path("plannedMinutes").asInt():null,
                        null,text(snapshot,"requestedDate"),text(snapshot,"startedAt"),text(snapshot,"endedAt"),
                        text(snapshot,"note")),"agent-focus-start-"+actionId));
    }

    @Bean public DomainTool focusFinishPrepareTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                   CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema=ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema,"sessionId","要结束的专注记录公开 ID；不填则结束当前进行中的专注",null);
        ToolSchemas.enumProperty(schema,"status","COMPLETED 正常完成（默认）/ ABORTED 中断，不计入完成","COMPLETED","ABORTED");
        ToolDefinition definition=definition("focus.finish.prepare",
                "结束当前进行中的专注并生成确认预览，不写入数据。没有进行中的专注时会直接报错。",ToolRisk.R2,schema);
        return prepare(definition,actions,currentUser,mapper,input->{
            var running=efficiency.currentFocus();
            if(running==null) throw new IllegalArgumentException("当前没有进行中的专注");
            String sessionId=text(input,"sessionId");
            if(sessionId!=null&&!sessionId.equals(running.publicId())) throw new IllegalArgumentException("该专注记录已经结束，或不是进行中的那一条");
            ObjectNode normalized=mapper.createObjectNode().put("sessionId",running.publicId())
                    .put("revision",running.revision()).put("status",text(input,"status")==null?"COMPLETED":text(input,"status"));
            normalized.put("target",focusTargetLabel(running));
            normalized.put("startedAt",running.startedAt());
            normalized.put("elapsedMinutes",elapsedMinutes(running.startedAt()));
            if(running.plannedMinutes()>0) normalized.put("plannedMinutes",running.plannedMinutes());
            return normalized;
        }, normalized -> {
            ObjectNode p=preview(mapper);
            row(p,"target",text(normalized,"target"));
            row(p,"startedAt",readableTime(text(normalized,"startedAt"),null));
            row(p,"elapsed",normalized.path("elapsedMinutes").asInt()+" 分钟");
            row(p,"plannedMinutes",minutesLabel(normalized.path("plannedMinutes")));
            row(p,"status","ABORTED".equals(text(normalized,"status"))?"中断":"完成");
            return new Presentation(("ABORTED".equals(text(normalized,"status"))?"中断专注：":"结束专注：")+text(normalized,"target"),
                    mapper.createObjectNode().set("preview",p));
        });
    }

    @Bean public DomainTool focusFinishCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("focus.finish.commit","focus.finish.prepare",ToolRisk.R2,actions,currentUser,mapper,
                (snapshot,actionId)->efficiency.finishFocus(ToolInputs.requiredText(snapshot,"sessionId"),
                        new TaskEfficiencyModels.FocusFinishCommand(text(snapshot,"status")),
                        String.valueOf(snapshot.path("revision").asLong())));
    }

    /** 专注关联对象展示：优先习惯，其次任务。 */
    private static String focusTargetLabel(TaskEfficiencyModels.FocusSession session) {
        if (session.habitTitle() != null) return "习惯 " + session.habitTitle();
        if (session.taskTitle() != null) return "任务 " + session.taskTitle();
        return "自由专注";
    }

    private static String focusTargetLabel(JsonNode normalized) {
        String habitId = text(normalized, "habitId");
        if (habitId != null) return "关联习惯";
        String taskId = text(normalized, "taskId");
        return taskId == null ? "自由专注" : "关联任务";
    }

    private static int elapsedMinutes(String startedAt) {
        try {
            long seconds = Duration.between(Instant.parse(startedAt), Instant.now()).getSeconds();
            return (int) Math.max(0, seconds / 60);
        } catch (RuntimeException ignored) { return 0; }
    }

    // ================= P3 管理类：回收站 / 批量改动 / 评论 / 习惯 / 倒数日 / 专注记录 / 任务设置 =================

    @Bean public DomainTool taskRestorePrepareTool(TaskService tasks, PendingActionService actions,
                                                   CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = idSchema(mapper);
        ToolDefinition definition = definition("task.restore.prepare",
                "把回收站里的任务恢复到原清单（子任务一并恢复）并生成确认预览，不修改数据。"
                        + "只对已删除的任务有效；未删除的任务会直接报错，不要用这个工具做别的事。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var task = tasks.getDeleted(ToolInputs.requiredText(input, "taskId"));
            return taskSnapshot(mapper, task).put("status", task.status())
                    .put("deletedAt", task.deletedAt() == null ? "" : task.deletedAt());
        }, normalized -> {
            String title = normalized.path("title").asText("");
            Card card = card(mapper)
                    .text("title", "标题", title)
                    .text("listName", "恢复到清单", listName(tasks, text(normalized, "listId")))
                    .time("dueAt", "到期时间", text(normalized, "dueAt"))
                    .text("deletedAt", "删除时间", readableTime(text(normalized, "deletedAt"), null))
                    .text("hint", "说明", "子任务会一起恢复");
            return new Presentation("恢复任务：" + title, card.content());
        });
    }

    @Bean public DomainTool taskRestoreCommitTool(TaskService tasks, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.restore.commit", "task.restore.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> tasks.restore(ToolInputs.requiredText(snapshot, "taskId"),
                        String.valueOf(snapshot.path("revision").asLong())));
    }

    @Bean public DomainTool taskBatchMovePrepareTool(TaskService tasks, PendingActionService actions,
                                                     CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.arrayProperty(schema, "taskIds", "要修改的任务公开 ID 列表，最多 100 个",
                mapper.createObjectNode().put("type", "string"), 1, 100);
        ToolSchemas.enumProperty(schema, "action",
                "改动类型：MOVE 换清单；RESCHEDULE 改到期时间；PRIORITY 改优先级；TAGS 追加标签（只增不减）",
                "MOVE", "RESCHEDULE", "PRIORITY", "TAGS");
        ToolSchemas.stringProperty(schema, "listId", "action=MOVE 时的目标清单公开 ID；先用 task.lists.list 取得", null);
        ToolSchemas.stringProperty(schema, "dueAt", "action=RESCHEDULE 时的新到期时间 ISO-8601", "date-time");
        ToolSchemas.enumProperty(schema, "priority", "action=PRIORITY 时的新优先级", "NONE", "LOW", "MEDIUM", "HIGH");
        ToolSchemas.arrayProperty(schema, "tagIds", "action=TAGS 时要追加的标签公开 ID；先用 task.tags.list 取得",
                mapper.createObjectNode().put("type", "string"), 1, 20);
        ToolSchemas.required(schema, "taskIds", "action");
        ToolDefinition definition = definition("task.batch.move.prepare",
                "批量改多个任务的清单、到期时间、优先级或追加标签并生成确认预览，不修改数据。一次最多 100 个；"
                        + "TAGS 只追加标签，不会移除已有标签；只改一个任务时也可以用这个工具。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            String action = text(input, "action");
            var ids = new java.util.LinkedHashSet<>(stringList(input, "taskIds"));
            if (ids.isEmpty()) throw new IllegalArgumentException("taskIds 不能为空");
            if (ids.size() > 100) throw new IllegalArgumentException("一次最多修改 100 个任务");
            String listId = text(input, "listId");
            String dueAt = text(input, "dueAt");
            String priority = text(input, "priority");
            List<String> tagIds = stringList(input, "tagIds");
            String targetLabel;
            switch (action == null ? "" : action) {
                case "MOVE" -> {
                    if (listId == null) throw new IllegalArgumentException("换清单必须提供 listId");
                    String name = listName(tasks, listId);
                    if (name == null) throw new IllegalArgumentException("清单不存在：" + listId);
                    targetLabel = name;
                }
                case "RESCHEDULE" -> {
                    if (dueAt == null) throw new IllegalArgumentException("改期必须提供 dueAt");
                    dueAt = utc(dueAt, tasks.settings().timezone());
                    targetLabel = readableTime(dueAt, null);
                }
                case "PRIORITY" -> {
                    if (priority == null) throw new IllegalArgumentException("改优先级必须提供 priority");
                    targetLabel = priorityLabel(priority);
                }
                case "TAGS" -> {
                    if (tagIds.isEmpty()) throw new IllegalArgumentException("追加标签必须提供 tagIds");
                    var known = tasks.listTags();
                    var names = new java.util.ArrayList<String>();
                    for (String tagId : tagIds) {
                        String name = known.stream().filter(tag -> tagId.equals(tag.publicId()))
                                .map(TaskModels.TaskTag::name).findFirst().orElse(null);
                        if (name == null) throw new IllegalArgumentException("标签不存在：" + tagId);
                        names.add(name);
                    }
                    targetLabel = String.join("、", names);
                }
                default -> throw new IllegalArgumentException("action 只能是 MOVE / RESCHEDULE / PRIORITY / TAGS");
            }
            var items = mapper.createArrayNode();
            var titles = new java.util.ArrayList<String>();
            for (String id : ids) {
                var task = tasks.get(id);
                items.add(mapper.createObjectNode().put("taskId", task.publicId()).put("revision", task.revision())
                        .put("title", task.title()));
                titles.add(task.title());
            }
            ObjectNode normalized = mapper.createObjectNode().put("action", action)
                    .put("count", items.size()).put("targetLabel", targetLabel)
                    .put("titles", joined(titles));
            normalized.set("items", items);
            if (listId != null) normalized.put("listId", listId);
            if (dueAt != null) normalized.put("dueAt", dueAt);
            if (priority != null) normalized.put("priority", priority);
            if (!tagIds.isEmpty()) normalized.set("tagIds", mapper.valueToTree(tagIds));
            return normalized;
        }, normalized -> {
            String action = text(normalized, "action");
            Card card = card(mapper)
                    .text("action", "改动", batchActionLabel(action))
                    .text("count", "数量", normalized.path("count").asInt() + " 个任务")
                    .text("target", "改到", text(normalized, "targetLabel"))
                    .text("tasks", "任务", text(normalized, "titles"));
            return new Presentation(batchActionLabel(action) + " " + normalized.path("count").asInt() + " 个任务",
                    card.content());
        });
    }

    @Bean public DomainTool taskBatchMoveCommitTool(TaskService tasks, PendingActionService actions,
                                                    CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.batch.move.commit", "task.batch.move.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> {
                    var targets = new java.util.ArrayList<TaskModels.BatchTarget>();
                    for (JsonNode item : snapshot.path("items")) {
                        targets.add(new TaskModels.BatchTarget(item.path("taskId").asText(),
                                item.path("revision").asLong()));
                    }
                    var tagIds = new java.util.ArrayList<String>();
                    snapshot.path("tagIds").forEach(node -> {
                        if (node.isTextual() && !node.asText().isBlank()) tagIds.add(node.asText());
                    });
                    return tasks.batch(new TaskModels.BatchCommand(targets, text(snapshot, "action"),
                            text(snapshot, "listId"), text(snapshot, "dueAt"),
                            tagIds.isEmpty() ? null : tagIds, text(snapshot, "priority"), null));
                });
    }

    private static String batchActionLabel(String action) {
        return switch (action == null ? "" : action) {
            case "MOVE" -> "移动到清单";
            case "RESCHEDULE" -> "批量改期";
            case "PRIORITY" -> "批量改优先级";
            case "TAGS" -> "批量加标签";
            default -> action;
        };
    }

    @Bean public DomainTool taskCommentCreatePrepareTool(TaskService tasks, TaskCommentService comments,
                                                         PendingActionService actions, CurrentUserResolver currentUser,
                                                         ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "taskId", "任务公开 ID", null);
        ToolSchemas.stringProperty(schema, "bodyMarkdown", "评论内容，支持 Markdown，最长 20000 字", null);
        ToolSchemas.required(schema, "taskId", "bodyMarkdown");
        ToolDefinition definition = definition("task.comment.create.prepare",
                "给任务写一条评论并生成确认预览，不写入数据。只支持新增评论，修改或删除评论不开放。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var task = tasks.get(ToolInputs.requiredText(input, "taskId"));
            String body = ToolInputs.requiredText(input, "bodyMarkdown").trim();
            if (body.isEmpty()) throw new IllegalArgumentException("评论内容不能为空");
            if (body.length() > 20000) throw new IllegalArgumentException("评论内容不能超过 20000 字");
            return mapper.createObjectNode().put("taskId", task.publicId()).put("taskTitle", task.title())
                    .put("bodyMarkdown", body);
        }, normalized -> {
            String body = text(normalized, "bodyMarkdown");
            Card card = card(mapper)
                    .text("task", "任务", text(normalized, "taskTitle"))
                    .text("body", "内容", body)
                    .text("length", "字数", body == null ? null : body.length() + " 字");
            return new Presentation("给「" + text(normalized, "taskTitle") + "」写评论", card.content());
        });
    }

    @Bean public DomainTool taskCommentCreateCommitTool(TaskCommentService comments, PendingActionService actions,
                                                        CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.comment.create.commit", "task.comment.create.prepare", ToolRisk.R3, actions, currentUser,
                mapper, (snapshot, actionId) -> comments.create(ToolInputs.requiredText(snapshot, "taskId"),
                        new TaskModels.CommentCommand(text(snapshot, "bodyMarkdown")), "agent-comment-" + actionId));
    }

    @Bean public DomainTool habitCreatePrepareTool(TaskEfficiencyService efficiency, TaskService tasks,
                                                   PendingActionService actions, CurrentUserResolver currentUser,
                                                   ObjectMapper mapper) {
        ObjectNode schema = habitSchema(mapper);
        ToolSchemas.required(schema, "name");
        ToolDefinition definition = definition("habit.create.prepare",
                "新建一个习惯并生成确认预览，不写入数据。frequency 默认 DAILY；CUSTOM 必须给 customDays（1=周一 … 7=周日）；"
                        + "WEEKLY_N / MONTHLY_N 用 targetCount 表示次数。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            String name = ToolInputs.requiredText(input, "name").trim();
            if (name.isEmpty()) throw new IllegalArgumentException("习惯名称不能为空");
            if (name.length() > 120) throw new IllegalArgumentException("习惯名称不能超过 120 字");
            String frequency = text(input, "frequency") == null ? "DAILY" : text(input, "frequency");
            List<Integer> days = customDays(input);
            if ("CUSTOM".equals(frequency) && days.isEmpty()) throw new IllegalArgumentException("自定义频率至少要选一天");
            int count = input.path("targetCount").isInt() ? input.path("targetCount").asInt() : 1;
            if (count < 1 || count > 999) throw new IllegalArgumentException("目标次数必须在 1 到 999 之间");
            String remindAt = remindTimes(text(input, "remindAt"));
            String startDate = text(input, "startDate") == null
                    ? LocalDate.now(ZoneId.of(tasks.settings().timezone())).toString()
                    : LocalDate.parse(text(input, "startDate")).toString();
            String groupKey = habitGroupKey(text(input, "groupKey"), "other");
            ObjectNode normalized = mapper.createObjectNode().put("name", name).put("frequency", frequency)
                    .put("targetCount", count).put("startDate", startDate).put("groupKey", groupKey)
                    .put("frequencyLabel", habitFrequencyLabel(frequency, count, days))
                    .put("remindAt", remindAt);
            normalized.set("customDays", mapper.valueToTree(days));
            return normalized;
        }, normalized -> {
            Card card = card(mapper)
                    .text("name", "习惯", text(normalized, "name"))
                    .text("frequency", "频率", text(normalized, "frequencyLabel"))
                    .text("targetCount", "目标次数", "WEEKLY_N".equals(text(normalized, "frequency"))
                            || "MONTHLY_N".equals(text(normalized, "frequency"))
                            ? normalized.path("targetCount").asInt() + " 次" : null)
                    .text("remindAt", "提醒", text(normalized, "remindAt"))
                    .text("startDate", "开始日期", text(normalized, "startDate"))
                    .text("groupKey", "分组", text(normalized, "groupKey"));
            return new Presentation("新建习惯：" + text(normalized, "name"), card.content());
        });
    }

    @Bean public DomainTool habitCreateCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("habit.create.commit", "habit.create.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> efficiency.createHabit(new TaskEfficiencyModels.HabitCommand(
                        text(snapshot, "name"), null, null, text(snapshot, "frequency"),
                        snapshot.path("targetCount").asInt(), intList(snapshot, "customDays"),
                        text(snapshot, "remindAt"), text(snapshot, "startDate"), false, 0,
                        text(snapshot, "groupKey"), null, null, null, null, null, null, null, null, null, null)));
    }

    @Bean public DomainTool habitUpdatePrepareTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = habitSchema(mapper);
        ToolSchemas.stringProperty(schema, "habitId", "习惯公开 ID；先用 habit.list 取得", null);
        ToolSchemas.required(schema, "habitId");
        ToolDefinition definition = definition("habit.update.prepare",
                "修改习惯的名称、频率、目标次数、提醒、开始日期或分组并生成确认预览，不写入数据。"
                        + "只传要改的字段，没传的保持原样；归档请到站内操作。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            String habitId = ToolInputs.requiredText(input, "habitId");
            var habit = findHabit(efficiency, habitId);
            var changed = new java.util.ArrayList<String>();
            ObjectNode normalized = mapper.createObjectNode().put("habitId", habit.publicId())
                    .put("revision", habit.revision()).put("habitName", habit.name());
            if (input.hasNonNull("name")) {
                String name = input.path("name").asText("").trim();
                if (name.isEmpty()) throw new IllegalArgumentException("习惯名称不能为空");
                if (name.length() > 120) throw new IllegalArgumentException("习惯名称不能超过 120 字");
                normalized.put("name", name);
                changed.add("name");
            }
            String frequency = text(input, "frequency");
            if (frequency != null) {
                normalized.put("frequency", frequency);
                if (!frequency.equals(habit.frequency())) changed.add("frequency");
            }
            if (input.path("targetCount").isInt()) {
                int count = input.path("targetCount").asInt();
                if (count < 1 || count > 999) throw new IllegalArgumentException("目标次数必须在 1 到 999 之间");
                normalized.put("targetCount", count);
                if (count != habit.targetCount()) changed.add("targetCount");
            }
            List<Integer> days = customDays(input);
            String effectiveFrequency = frequency == null ? habit.frequency() : frequency;
            if (!days.isEmpty()) {
                normalized.set("customDays", mapper.valueToTree(days));
                if (!days.equals(habit.customDays())) changed.add("customDays");
            }
            if ("CUSTOM".equals(effectiveFrequency)) {
                List<Integer> effectiveDays = days.isEmpty() ? habit.customDays() : days;
                if (effectiveDays.isEmpty()) throw new IllegalArgumentException("自定义频率至少要选一天");
            }
            if (input.hasNonNull("remindAt")) {
                String remindAt = remindTimes(input.path("remindAt").asText(""));
                normalized.put("remindAt", remindAt);
                if (!remindAt.equals(habit.remindAt() == null ? "" : habit.remindAt())) changed.add("remindAt");
            }
            if (input.hasNonNull("startDate")) {
                String startDate = LocalDate.parse(input.path("startDate").asText()).toString();
                normalized.put("startDate", startDate);
                if (!startDate.equals(habit.startDate())) changed.add("startDate");
            }
            if (input.hasNonNull("groupKey")) {
                String groupKey = habitGroupKey(input.path("groupKey").asText(""), habit.groupKey());
                normalized.put("groupKey", groupKey);
                if (!groupKey.equals(habit.groupKey())) changed.add("groupKey");
            }
            if (changed.isEmpty()) throw new IllegalArgumentException("没有要修改的字段");
            normalized.put("changedFields", String.join(",", changed));
            normalized.put("beforeName", habit.name());
            normalized.put("beforeFrequency", habitFrequencyLabel(habit.frequency(), habit.targetCount(), habit.customDays()));
            normalized.put("beforeTargetCount", habit.targetCount());
            normalized.put("beforeRemindAt", habit.remindAt() == null ? "" : habit.remindAt());
            normalized.put("beforeStartDate", habit.startDate());
            normalized.put("beforeGroupKey", habit.groupKey());
            int effectiveCount = normalized.path("targetCount").isInt() ? normalized.path("targetCount").asInt() : habit.targetCount();
            normalized.put("frequencyLabel", habitFrequencyLabel(effectiveFrequency, effectiveCount,
                    days.isEmpty() ? habit.customDays() : days));
            return normalized;
        }, normalized -> {
            ObjectNode content = mapper.createObjectNode();
            var changed = java.util.Arrays.asList(text(normalized, "changedFields").split(","));
            if (changed.contains("name"))
                diffRow(content, mapper, "名称", text(normalized, "beforeName"), text(normalized, "name"));
            if (changed.contains("frequency") || changed.contains("targetCount") || changed.contains("customDays"))
                diffRow(content, mapper, "频率", text(normalized, "beforeFrequency"),
                        text(normalized, "frequencyLabel"));
            if (changed.contains("remindAt"))
                diffRow(content, mapper, "提醒", text(normalized, "beforeRemindAt"), text(normalized, "remindAt"));
            if (changed.contains("startDate"))
                diffRow(content, mapper, "开始日期", text(normalized, "beforeStartDate"), text(normalized, "startDate"));
            if (changed.contains("groupKey"))
                diffRow(content, mapper, "分组", text(normalized, "beforeGroupKey"), text(normalized, "groupKey"));
            content.set("preview", mapper.createObjectNode().put("name", text(normalized, "habitName")));
            return new Presentation("修改习惯：" + text(normalized, "habitName"), content);
        });
    }

    @Bean public DomainTool habitUpdateCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("habit.update.commit", "habit.update.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> efficiency.updateHabit(ToolInputs.requiredText(snapshot, "habitId"),
                        new TaskEfficiencyModels.HabitCommand(text(snapshot, "name"), null, null,
                                text(snapshot, "frequency"),
                                snapshot.path("targetCount").isInt() ? snapshot.path("targetCount").asInt() : null,
                                snapshot.has("customDays") ? intList(snapshot, "customDays") : null,
                                raw(snapshot, "remindAt"), text(snapshot, "startDate"), null, null,
                                text(snapshot, "groupKey"), null, null, null, null, null, null, null, null, null, null),
                        String.valueOf(snapshot.path("revision").asLong())));
    }

    @Bean public DomainTool habitDeletePrepareTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                   CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "habitId", "要删除的习惯公开 ID；先用 habit.list 取得", null);
        ToolSchemas.required(schema, "habitId");
        ToolDefinition definition = definition("habit.delete.prepare",
                "删除一个习惯（软删除，打卡记录一并不可见）并生成确认预览，不修改数据。删了就找不回来，"
                        + "只是不想再打卡请考虑用 habit.update 或站内归档。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var habit = findHabit(efficiency, ToolInputs.requiredText(input, "habitId"));
            var stats = efficiency.habitStats(habit.publicId(), null, null);
            return mapper.createObjectNode().put("habitId", habit.publicId()).put("revision", habit.revision())
                    .put("name", habit.name()).put("frequencyLabel",
                            habitFrequencyLabel(habit.frequency(), habit.targetCount(), habit.customDays()))
                    .put("groupKey", habit.groupKey()).put("totalCompleted", stats.totalCompleted())
                    .put("currentStreak", stats.currentStreak());
        }, normalized -> {
            Card card = card(mapper)
                    .text("name", "习惯", text(normalized, "name"))
                    .text("frequency", "频率", text(normalized, "frequencyLabel"))
                    .text("groupKey", "分组", text(normalized, "groupKey"))
                    .text("totalCompleted", "累计打卡", normalized.path("totalCompleted").asInt() + " 次")
                    .text("currentStreak", "当前连续", normalized.path("currentStreak").asInt() + " 天")
                    .text("hint", "影响", "删除后该习惯与它的打卡记录都不再显示，无法撤销");
            return new Presentation("删除习惯：" + text(normalized, "name"), card.content());
        });
    }

    @Bean public DomainTool habitDeleteCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("habit.delete.commit", "habit.delete.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> {
                    efficiency.deleteHabit(ToolInputs.requiredText(snapshot, "habitId"),
                            String.valueOf(snapshot.path("revision").asLong()));
                    return mapper.createObjectNode().put("habitId", snapshot.path("habitId").asText()).put("deleted", true);
                });
    }

    // ---------- 倒数日 ----------

    @Bean public DomainTool countdownCreatePrepareTool(TaskEfficiencyService efficiency, TaskService tasks,
                                                       PendingActionService actions, CurrentUserResolver currentUser,
                                                       ObjectMapper mapper) {
        ObjectNode schema = countdownSchema(mapper);
        ToolSchemas.required(schema, "title", "targetDate");
        ToolDefinition definition = definition("countdown.create.prepare",
                "新建一个倒数日/纪念日并生成确认预览，不写入数据。groupId 不给时用第一个分组；"
                        + "repeatRule 支持 NONE/DAILY/WEEKLY/MONTHLY/YEARLY 或 CUSTOM:N（每隔 N 天）。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            String title = ToolInputs.requiredText(input, "title").trim();
            if (title.isEmpty()) throw new IllegalArgumentException("标题不能为空");
            if (title.length() > 160) throw new IllegalArgumentException("标题不能超过 160 字");
            LocalDate target = LocalDate.parse(ToolInputs.requiredText(input, "targetDate"));
            var group = resolveCountdownGroup(efficiency, text(input, "groupId"));
            String repeatRule = countdownRepeatRule(text(input, "repeatRule"));
            boolean precise = Boolean.TRUE.equals(input.path("preciseSeconds").asBoolean(false));
            String preciseTime = countdownClock(text(input, "preciseTime"));
            if (!preciseTime.isEmpty()) precise = true;
            String unit = countdownUnit(text(input, "unit"));
            ObjectNode normalized = mapper.createObjectNode().put("title", title).put("targetDate", target.toString())
                    .put("groupId", group.publicId()).put("groupName", group.name())
                    .put("repeatRule", repeatRule).put("repeatLabel", countdownRepeatLabel(repeatRule))
                    .put("unit", unit).put("preciseSeconds", precise).put("preciseTime", preciseTime)
                    .put("pinned", input.path("pinned").asBoolean(false))
                    .put("interval", countdownInterval(target, ZoneId.of(tasks.settings().timezone())));
            if (input.hasNonNull("note")) normalized.put("note", input.path("note").asText());
            return normalized;
        }, normalized -> {
            Card card = card(mapper)
                    .text("title", "标题", text(normalized, "title"))
                    .text("targetDate", "目标日期", text(normalized, "targetDate") + "（" + text(normalized, "interval") + "）")
                    .text("groupName", "分组", text(normalized, "groupName"))
                    .text("repeatLabel", "重复", text(normalized, "repeatLabel"))
                    .text("preciseTime", "提醒时刻", text(normalized, "preciseTime"))
                    .text("note", "备注", text(normalized, "note"));
            return new Presentation("新建倒数日：" + text(normalized, "title"), card.content());
        });
    }

    @Bean public DomainTool countdownCreateCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                      CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("countdown.create.commit", "countdown.create.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> efficiency.createCountdown(new TaskEfficiencyModels.CountdownCommand(
                        text(snapshot, "title"), text(snapshot, "targetDate"), null, text(snapshot, "groupId"),
                        null, snapshot.path("pinned").asBoolean(false), null, text(snapshot, "note"),
                        null, null, null, null, null, text(snapshot, "unit"),
                        snapshot.path("preciseSeconds").asBoolean(false), text(snapshot, "preciseTime"),
                        null, text(snapshot, "repeatRule"), null, null, null, null, null)));
    }

    @Bean public DomainTool countdownUpdatePrepareTool(TaskEfficiencyService efficiency, TaskService tasks,
                                                       PendingActionService actions, CurrentUserResolver currentUser,
                                                       ObjectMapper mapper) {
        ObjectNode schema = countdownSchema(mapper);
        ToolSchemas.stringProperty(schema, "countdownId", "倒数日公开 ID；先用 countdown.list 取得", null);
        ToolSchemas.required(schema, "countdownId");
        ToolDefinition definition = definition("countdown.update.prepare",
                "修改倒数日的标题、目标日期、分组、重复规则、提醒时刻或备注并生成确认预览，不写入数据。"
                        + "只传要改的字段；系统内置项（如发薪日）不能改，会被直接拒绝。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var item = findCountdown(efficiency, ToolInputs.requiredText(input, "countdownId"));
            if (item.builtinKey() != null && !item.builtinKey().isBlank())
                throw new IllegalArgumentException("「" + item.title() + "」是系统内置倒数日，不能修改");
            var changed = new java.util.ArrayList<String>();
            ObjectNode normalized = mapper.createObjectNode().put("countdownId", item.publicId())
                    .put("revision", item.revision()).put("beforeTitle", item.title())
                    .put("beforeTargetDate", item.targetDate()).put("beforeGroupId", item.groupId())
                    .put("beforeRepeatRule", item.repeatRule()).put("beforePreciseTime", item.preciseTime())
                    .put("beforeNote", item.note() == null ? "" : item.note());
            if (input.hasNonNull("title")) {
                String title = input.path("title").asText("").trim();
                if (title.isEmpty()) throw new IllegalArgumentException("标题不能为空");
                if (title.length() > 160) throw new IllegalArgumentException("标题不能超过 160 字");
                normalized.put("title", title);
                if (!title.equals(item.title())) changed.add("title");
            }
            if (input.hasNonNull("targetDate")) {
                String targetDate = LocalDate.parse(input.path("targetDate").asText()).toString();
                normalized.put("targetDate", targetDate);
                if (!targetDate.equals(item.targetDate())) changed.add("targetDate");
            }
            if (input.hasNonNull("groupId")) {
                var group = resolveCountdownGroup(efficiency, input.path("groupId").asText());
                normalized.put("groupId", group.publicId()).put("groupName", group.name());
                if (!group.publicId().equals(item.groupId())) changed.add("groupId");
            }
            if (input.hasNonNull("repeatRule")) {
                String repeatRule = countdownRepeatRule(input.path("repeatRule").asText());
                normalized.put("repeatRule", repeatRule).put("repeatLabel", countdownRepeatLabel(repeatRule));
                if (!repeatRule.equals(item.repeatRule())) changed.add("repeatRule");
            }
            if (input.hasNonNull("preciseTime")) {
                String preciseTime = countdownClock(input.path("preciseTime").asText(""));
                normalized.put("preciseTime", preciseTime).put("preciseSeconds", !preciseTime.isEmpty());
                if (!preciseTime.equals(item.preciseTime())) changed.add("preciseTime");
            }
            if (input.hasNonNull("unit")) {
                String unit = countdownUnit(input.path("unit").asText());
                normalized.put("unit", unit);
                if (!unit.equals(item.unit())) changed.add("unit");
            }
            if (input.hasNonNull("note")) {
                String note = input.path("note").asText("");
                normalized.put("note", note);
                if (!note.equals(item.note() == null ? "" : item.note())) changed.add("note");
            }
            if (input.hasNonNull("pinned")) {
                boolean pinned = input.path("pinned").asBoolean(false);
                normalized.put("pinned", pinned);
                if (pinned != item.pinned()) changed.add("pinned");
            }
            if (changed.isEmpty()) throw new IllegalArgumentException("没有要修改的字段");
            normalized.put("changedFields", String.join(",", changed));
            String effectiveDate = normalized.has("targetDate") ? text(normalized, "targetDate") : item.targetDate();
            normalized.put("interval", countdownInterval(LocalDate.parse(effectiveDate),
                    ZoneId.of(tasks.settings().timezone())));
            return normalized;
        }, normalized -> {
            ObjectNode content = mapper.createObjectNode();
            var changed = java.util.Arrays.asList(text(normalized, "changedFields").split(","));
            if (changed.contains("title"))
                diffRow(content, mapper, "标题", text(normalized, "beforeTitle"), text(normalized, "title"));
            if (changed.contains("targetDate"))
                diffRow(content, mapper, "目标日期", text(normalized, "beforeTargetDate"),
                        text(normalized, "targetDate") + "（" + text(normalized, "interval") + "）");
            if (changed.contains("groupId"))
                diffRow(content, mapper, "分组", countdownGroupName(efficiency, text(normalized, "beforeGroupId")),
                        text(normalized, "groupName"));
            if (changed.contains("repeatRule"))
                diffRow(content, mapper, "重复", countdownRepeatLabel(text(normalized, "beforeRepeatRule")),
                        text(normalized, "repeatLabel"));
            if (changed.contains("preciseTime"))
                diffRow(content, mapper, "提醒时刻", text(normalized, "beforePreciseTime"), text(normalized, "preciseTime"));
            if (changed.contains("note"))
                diffRow(content, mapper, "备注", text(normalized, "beforeNote"), text(normalized, "note"));
            if (changed.contains("pinned"))
                diffRow(content, mapper, "置顶", "否", normalized.path("pinned").asBoolean(false) ? "是" : "否");
            content.set("preview", mapper.createObjectNode().put("title", text(normalized, "beforeTitle")));
            return new Presentation("修改倒数日：" + text(normalized, "beforeTitle"), content);
        });
    }

    @Bean public DomainTool countdownUpdateCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                      CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("countdown.update.commit", "countdown.update.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> efficiency.updateCountdown(ToolInputs.requiredText(snapshot, "countdownId"),
                        new TaskEfficiencyModels.CountdownCommand(text(snapshot, "title"), text(snapshot, "targetDate"),
                                null, text(snapshot, "groupId"), null,
                                snapshot.has("pinned") ? snapshot.path("pinned").asBoolean(false) : null,
                                null, raw(snapshot, "note"), null, null, null, null, null,
                                text(snapshot, "unit"),
                                snapshot.has("preciseTime") ? snapshot.path("preciseSeconds").asBoolean(false) : null,
                                raw(snapshot, "preciseTime"), null, text(snapshot, "repeatRule"),
                                null, null, null, null, null),
                        String.valueOf(snapshot.path("revision").asLong())));
    }

    @Bean public DomainTool countdownDeletePrepareTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                       CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "countdownId", "要删除的倒数日公开 ID；先用 countdown.list 取得", null);
        ToolSchemas.required(schema, "countdownId");
        ToolDefinition definition = definition("countdown.delete.prepare",
                "删除一个倒数日并生成确认预览，不修改数据。内置倒数日被删除后系统不再补建，删了就没有了。",
                ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var item = findCountdown(efficiency, ToolInputs.requiredText(input, "countdownId"));
            return mapper.createObjectNode().put("countdownId", item.publicId()).put("revision", item.revision())
                    .put("title", item.title()).put("targetDate", item.targetDate())
                    .put("groupName", countdownGroupName(efficiency, item.groupId()))
                    .put("builtin", item.builtinKey() != null && !item.builtinKey().isBlank());
        }, normalized -> {
            Card card = card(mapper)
                    .text("title", "标题", text(normalized, "title"))
                    .text("targetDate", "目标日期", text(normalized, "targetDate"))
                    .text("groupName", "分组", text(normalized, "groupName"))
                    .text("hint", "影响", normalized.path("builtin").asBoolean(false)
                            ? "这是系统内置项，删除后不会再自动补建" : "删除后无法恢复");
            return new Presentation("删除倒数日：" + text(normalized, "title"), card.content());
        });
    }

    @Bean public DomainTool countdownDeleteCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                      CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("countdown.delete.commit", "countdown.delete.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> {
                    efficiency.deleteCountdown(ToolInputs.requiredText(snapshot, "countdownId"),
                            String.valueOf(snapshot.path("revision").asLong()));
                    return mapper.createObjectNode().put("countdownId", snapshot.path("countdownId").asText())
                            .put("deleted", true);
                });
    }

    // ---------- 专注记录 ----------

    @Bean public DomainTool focusUpdatePrepareTool(TaskEfficiencyService efficiency, TaskService tasks,
                                                   PendingActionService actions, CurrentUserResolver currentUser,
                                                   ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "sessionId", "专注记录公开 ID；先用 focus.sessions.list 取得", null);
        ToolSchemas.stringProperty(schema, "taskId", "改成关联这个任务；与 habitId 只能二选一", null);
        ToolSchemas.stringProperty(schema, "habitId", "改成关联这个习惯；与 taskId 只能二选一", null);
        ToolSchemas.booleanProperty(schema, "unlink", "设为 true 表示解除关联，变成自由专注");
        ToolSchemas.stringProperty(schema, "startedAt", "新的开始时刻 ISO-8601；与 endedAt 必须同时给，时长会按差值重算", "date-time");
        ToolSchemas.stringProperty(schema, "endedAt", "新的结束时刻 ISO-8601；与 startedAt 必须同时给", "date-time");
        ToolSchemas.stringProperty(schema, "note", "这轮专注做了什么（想法）", null);
        ToolSchemas.required(schema, "sessionId");
        ToolDefinition definition = definition("focus.update.prepare",
                "修改一条专注记录的关联对象、起止时间或想法并生成确认预览，不写入数据。"
                        + "改起止时间会把状态改成已完成并重算时长；改关联任务不会回溯已计入旧任务的专注时长。",
                ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            String sessionId = ToolInputs.requiredText(input, "sessionId");
            var session = efficiency.getFocus(sessionId);
            String taskId = text(input, "taskId");
            String habitId = text(input, "habitId");
            boolean unlink = input.path("unlink").asBoolean(false);
            if (unlink && (taskId != null || habitId != null))
                throw new IllegalArgumentException("解除关联时不要再给 taskId / habitId");
            if (taskId != null && habitId != null)
                throw new IllegalArgumentException("任务和习惯只能关联一个");
            String startedAt = text(input, "startedAt");
            String endedAt = text(input, "endedAt");
            if ((startedAt == null) != (endedAt == null))
                throw new IllegalArgumentException("修改时长必须同时提供 startedAt 与 endedAt");
            ObjectNode normalized = mapper.createObjectNode().put("sessionId", session.publicId())
                    .put("revision", session.revision())
                    .put("beforeTarget", focusTargetLabel(session))
                    .put("beforeStartedAt", session.startedAt())
                    .put("beforeNote", session.note() == null ? "" : session.note());
            if (unlink) {
                normalized.put("taskId", "").put("habitId", "");
            } else if (habitId != null) {
                var habit = findHabit(efficiency, habitId);
                normalized.put("habitId", habit.publicId()).put("afterTarget", "习惯 " + habit.name());
            } else if (taskId != null) {
                var task = tasks.get(taskId);
                normalized.put("taskId", task.publicId()).put("afterTarget", "任务 " + task.title());
            }
            if (startedAt != null) {
                normalized.put("startedAt", utc(startedAt, tasks.settings().timezone()));
                normalized.put("endedAt", utc(endedAt, tasks.settings().timezone()));
            }
            if (input.hasNonNull("note")) normalized.put("note", input.path("note").asText());
            return normalized;
        }, normalized -> {
            ObjectNode content = mapper.createObjectNode();
            if (normalized.has("taskId") || normalized.has("habitId"))
                diffRow(content, mapper, "关联对象", text(normalized, "beforeTarget"),
                        text(normalized, "afterTarget") == null ? "自由专注" : text(normalized, "afterTarget"));
            if (normalized.has("startedAt"))
                diffRow(content, mapper, "起止时间",
                        readableTime(text(normalized, "beforeStartedAt"), null),
                        readableTime(text(normalized, "startedAt"), null) + " → " + readableTime(text(normalized, "endedAt"), null));
            if (normalized.has("note"))
                diffRow(content, mapper, "想法", text(normalized, "beforeNote"), text(normalized, "note"));
            content.set("preview", mapper.createObjectNode().put("target", text(normalized, "beforeTarget")));
            return new Presentation("修改专注记录：" + text(normalized, "beforeTarget"), content);
        });
    }

    @Bean public DomainTool focusUpdateCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("focus.update.commit", "focus.update.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> efficiency.updateFocus(ToolInputs.requiredText(snapshot, "sessionId"),
                        new TaskEfficiencyModels.FocusSessionUpdateCommand(raw(snapshot, "taskId"),
                                raw(snapshot, "habitId"), raw(snapshot, "note"),
                                text(snapshot, "startedAt"), text(snapshot, "endedAt")),
                        String.valueOf(snapshot.path("revision").asLong())));
    }

    @Bean public DomainTool focusDeletePrepareTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                   CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "sessionId", "要删除的专注记录公开 ID；先用 focus.sessions.list 取得", null);
        ToolSchemas.required(schema, "sessionId");
        ToolDefinition definition = definition("focus.delete.prepare",
                "删除一条专注记录并生成确认预览，不修改数据。进行中的专注要先结束才能删除。", ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var session = efficiency.getFocus(ToolInputs.requiredText(input, "sessionId"));
            if ("RUNNING".equals(session.status()))
                throw new IllegalArgumentException("这轮专注还在进行中，请先用 focus.finish 结束再删除");
            return mapper.createObjectNode().put("sessionId", session.publicId()).put("revision", session.revision())
                    .put("target", focusTargetLabel(session)).put("startedAt", session.startedAt())
                    .put("actualMinutes", session.actualMinutes()).put("status", session.status());
        }, normalized -> {
            Card card = card(mapper)
                    .text("target", "关联对象", text(normalized, "target"))
                    .text("startedAt", "开始时间", readableTime(text(normalized, "startedAt"), null))
                    .text("actualMinutes", "时长", normalized.path("actualMinutes").asInt() + " 分钟")
                    .text("hint", "影响", "删除后这段专注时长会从统计里消失");
            return new Presentation("删除专注记录：" + text(normalized, "target"), card.content());
        });
    }

    @Bean public DomainTool focusDeleteCommitTool(TaskEfficiencyService efficiency, PendingActionService actions,
                                                  CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("focus.delete.commit", "focus.delete.prepare", ToolRisk.R3, actions, currentUser, mapper,
                (snapshot, actionId) -> {
                    efficiency.deleteFocus(ToolInputs.requiredText(snapshot, "sessionId"),
                            String.valueOf(snapshot.path("revision").asLong()));
                    return mapper.createObjectNode().put("sessionId", snapshot.path("sessionId").asText())
                            .put("deleted", true);
                });
    }

    // ---------- 任务设置 ----------

    @Bean public DomainTool taskSettingsUpdatePrepareTool(TaskService tasks, PendingActionService actions,
                                                          CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "defaultListId", "默认清单公开 ID；先用 task.lists.list 取得", null);
        ToolSchemas.enumProperty(schema, "defaultView", "打开任务页时默认视图", "INBOX", "TODAY", "CALENDAR", "BOARD");
        ToolSchemas.integerProperty(schema, "defaultReminderMinutes", "默认提前提醒分钟数，0-10080", 0, 0, 10080);
        ToolSchemas.enumProperty(schema, "defaultDueDate", "新建任务的默认到期日", "TODAY", "NONE");
        ToolSchemas.stringProperty(schema, "defaultDueTime", "默认到期时刻：NEXT_HOUR 或本地 HH:mm", null);
        ToolSchemas.integerProperty(schema, "weekStart", "一周起始日：0 周日 / 1 周一", 1, 0, 1);
        ToolDefinition definition = definition("task.settings.update.prepare",
                "修改任务域默认设置（默认清单、默认视图、默认提醒、默认到期时间、周起始日）并生成确认预览，不写入数据。"
                        + "只传要改的字段；通知时段、日历叠加等界面偏好不在这里改。要先知道当前值请用 task.settings.get。",
                ToolRisk.R3, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            var before = tasks.settings();
            var changed = new java.util.ArrayList<String>();
            ObjectNode normalized = mapper.createObjectNode().put("revision", before.revision());
            if (input.hasNonNull("defaultListId")) {
                String listId = input.path("defaultListId").asText();
                String name = listName(tasks, listId);
                if (name == null) throw new IllegalArgumentException("清单不存在：" + listId);
                normalized.put("defaultListId", listId);
                if (!listId.equals(before.defaultListId())) changed.add("defaultListId");
            }
            if (input.hasNonNull("defaultView")) {
                String view = input.path("defaultView").asText();
                normalized.put("defaultView", view);
                if (!view.equals(before.defaultView())) changed.add("defaultView");
            }
            if (input.path("defaultReminderMinutes").isInt()) {
                int minutes = input.path("defaultReminderMinutes").asInt();
                if (minutes < 0 || minutes > 10080) throw new IllegalArgumentException("默认提醒必须在 0-10080 分钟");
                normalized.put("defaultReminderMinutes", minutes);
                if (before.defaultReminderMinutes() == null || minutes != before.defaultReminderMinutes())
                    changed.add("defaultReminderMinutes");
            }
            if (input.hasNonNull("defaultDueDate")) {
                String dueDate = input.path("defaultDueDate").asText();
                normalized.put("defaultDueDate", dueDate);
                if (!dueDate.equals(before.defaultDueDate())) changed.add("defaultDueDate");
            }
            if (input.hasNonNull("defaultDueTime")) {
                String dueTime = input.path("defaultDueTime").asText().trim();
                if (!"NEXT_HOUR".equals(dueTime) && !dueTime.matches("([01]\\d|2[0-3]):[0-5]\\d"))
                    throw new IllegalArgumentException("默认到期时刻必须是 NEXT_HOUR 或 HH:mm");
                normalized.put("defaultDueTime", dueTime);
                if (!dueTime.equals(before.defaultDueTime())) changed.add("defaultDueTime");
            }
            if (input.path("weekStart").isInt()) {
                int weekStart = input.path("weekStart").asInt();
                if (weekStart != 0 && weekStart != 1) throw new IllegalArgumentException("一周起始日只能是 0 或 1");
                normalized.put("weekStart", weekStart);
                if (weekStart != before.weekStart()) changed.add("weekStart");
            }
            if (changed.isEmpty()) throw new IllegalArgumentException("没有要修改的字段");
            normalized.put("changedFields", String.join(",", changed));
            normalized.put("beforeDefaultListName", listName(tasks, before.defaultListId()));
            normalized.put("beforeDefaultView", before.defaultView());
            normalized.put("beforeDefaultReminderMinutes", before.defaultReminderMinutes() == null
                    ? "不提醒" : before.defaultReminderMinutes() + " 分钟");
            normalized.put("beforeDefaultDueDate", "NONE".equals(before.defaultDueDate()) ? "不设到期日" : "今天");
            normalized.put("beforeDefaultDueTime", before.defaultDueTime());
            normalized.put("beforeWeekStart", before.weekStart() == 0 ? "周日" : "周一");
            normalized.put("afterDefaultListName", normalized.has("defaultListId")
                    ? listName(tasks, text(normalized, "defaultListId")) : null);
            normalized.put("afterDefaultDueDate", "NONE".equals(text(normalized, "defaultDueDate")) ? "不设到期日" : "今天");
            normalized.put("afterWeekStart", normalized.has("weekStart")
                    ? (normalized.path("weekStart").asInt() == 0 ? "周日" : "周一") : null);
            normalized.put("afterDefaultReminderMinutes", normalized.path("defaultReminderMinutes").isInt()
                    ? normalized.path("defaultReminderMinutes").asInt() + " 分钟" : null);
            return normalized;
        }, normalized -> {
            ObjectNode content = mapper.createObjectNode();
            var changed = java.util.Arrays.asList(text(normalized, "changedFields").split(","));
            if (changed.contains("defaultListId"))
                diffRow(content, mapper, "默认清单", text(normalized, "beforeDefaultListName"),
                        text(normalized, "afterDefaultListName"));
            if (changed.contains("defaultView"))
                diffRow(content, mapper, "默认视图", text(normalized, "beforeDefaultView"), text(normalized, "defaultView"));
            if (changed.contains("defaultReminderMinutes"))
                diffRow(content, mapper, "默认提醒", text(normalized, "beforeDefaultReminderMinutes"),
                        text(normalized, "afterDefaultReminderMinutes"));
            if (changed.contains("defaultDueDate"))
                diffRow(content, mapper, "默认到期日", text(normalized, "beforeDefaultDueDate"),
                        text(normalized, "afterDefaultDueDate"));
            if (changed.contains("defaultDueTime"))
                diffRow(content, mapper, "默认到期时刻", text(normalized, "beforeDefaultDueTime"),
                        text(normalized, "defaultDueTime"));
            if (changed.contains("weekStart"))
                diffRow(content, mapper, "一周起始", text(normalized, "beforeWeekStart"),
                        text(normalized, "afterWeekStart"));
            content.set("preview", mapper.createObjectNode().put("changed", changed.size() + " 项设置"));
            return new Presentation("修改任务设置（" + changed.size() + " 项）", content);
        });
    }

    @Bean public DomainTool taskSettingsUpdateCommitTool(TaskService tasks, PendingActionService actions,
                                                         CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.settings.update.commit", "task.settings.update.prepare", ToolRisk.R3, actions,
                currentUser, mapper, (snapshot, actionId) -> tasks.updateSettings(new TaskModels.SettingsCommand(
                        text(snapshot, "defaultListId"), text(snapshot, "defaultView"),
                        snapshot.path("defaultReminderMinutes").isInt() ? snapshot.path("defaultReminderMinutes").asInt() : null,
                        text(snapshot, "defaultDueDate"), text(snapshot, "defaultDueTime"),
                        snapshot.path("weekStart").isInt() ? snapshot.path("weekStart").asInt() : null,
                        null, null, null, null, null, null, null, null, null, null, null, null),
                        String.valueOf(snapshot.path("revision").asLong())));
    }

    /** 只读工具的摘要名词：用于生成「今天的任务 3 条」这类可读结果，而不是把工具说明当结果。 */
    private static final Map<String, String> READ_NOUNS = Map.ofEntries(
            Map.entry("task.today", "今天的任务"),
            Map.entry("task.overdue", "逾期任务"),
            Map.entry("task.stats.summary", "任务统计"),
            Map.entry("task.get", "任务"),
            Map.entry("task.search", "匹配任务"),
            Map.entry("task.calendar", "日历任务"),
            Map.entry("task.settings.get", "任务设置"),
            Map.entry("task.filters.list", "智能清单"),
            Map.entry("habit.list", "习惯"),
            Map.entry("habit.stats", "习惯统计"),
            Map.entry("habit.calendar", "习惯日历"),
            Map.entry("habit.checkin.query", "打卡记录"),
            Map.entry("focus.current", "当前专注"),
            Map.entry("focus.sessions.list", "专注记录"),
            Map.entry("focus.stats.query", "专注统计"),
            Map.entry("countdown.list", "倒数日"));

    private DomainTool read(String name, String description, ObjectNode schema, ObjectMapper mapper, Reader reader) {
        ToolDefinition definition = definition(name, description, ToolRisk.R1, schema);
        return new DomainTool() { public ToolDefinition definition(){return definition;} public ToolResult execute(JsonNode input){
            Object value=reader.read(input==null?mapper.createObjectNode():input);
            JsonNode content=mapper.valueToTree(value);
            // 摘要不能用 description：模型与 MCP 客户端会把工具说明当成查询结果，
            // 每次查询都回一整段「查询当前用户今天到期且未完成的任务…」，既浪费上下文，
            // 也容易让模型误判「已经拿到了结果」而不再读明细。
            return ToolResult.completed(summarize(name, content), content); }};
    }

    private static String summarize(String name, JsonNode content) {
        String noun = READ_NOUNS.getOrDefault(name, "结果");
        JsonNode list = content != null && content.isObject() ? firstList(content) : content;
        if (list != null && list.isArray()) return list.isEmpty() ? "没有" + noun : noun + " " + list.size() + " 条";
        if (content == null || content.isNull() || content.isMissingNode()) return "没有" + noun;
        return "已返回" + noun;
    }

    private static JsonNode firstList(JsonNode content) {
        for (String key : List.of("items", "tasks", "lists", "habits", "groups", "records", "sessions")) {
            JsonNode node = content.get(key);
            if (node != null && node.isArray()) return node;
        }
        return null;
    }

    private DomainTool prepare(ToolDefinition definition, PendingActionService actions, CurrentUserResolver currentUser,
                               ObjectMapper mapper, Normalizer normalizer, Presenter presenter) {
        return new DomainTool() { public ToolDefinition definition(){return definition;} public ToolResult execute(JsonNode input){
            ObjectNode normalized=normalizer.normalize(input==null?mapper.createObjectNode():input);
            Presentation presentation=presenter.present(normalized);
            ObjectNode content=presentation.content();
            content.put("actionType",definition.name().replace(".prepare",""));
            PendingAction action=actions.prepare(currentUser.id(),definition,normalized,
                    normalized.has("revision")?normalized.path("revision").asLong():null,false,Duration.ofMinutes(15));
            return ToolResult.needsConfirmation(presentation.summary(),content,action.id(),action.expiresAt().toString()); }};
    }

    /** 卡片展示用的预览行：键值对直接给前端渲染，避免前端按账本字段猜。 */
    private ObjectNode preview(ObjectMapper mapper) { return mapper.createObjectNode(); }

    private static void row(ObjectNode preview, String key, String value) { if (value != null && !value.isBlank()) preview.put(key, value); }

    /** 空串与 null 视为同一件事，diff 比较前先归一，避免「未填写 → 未填写」。 */
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }

    /**
     * 服务端只接受 UTC ISO-8601（TaskService.validate 会拒绝 +08:00 这类带偏移的写法），
     * 而模型习惯按用户时区给时间。prepare 阶段统一转 UTC，避免卡片生成成功、确认时才发现格式不对。
     */
    private static String utc(String iso, String zone) {
        if (iso == null || iso.isBlank()) return iso;
        ZoneId target = ZoneId.of(zone == null || zone.isBlank() ? "Asia/Shanghai" : zone);
        try { return Instant.parse(iso).toString(); } catch (RuntimeException ignored) { }
        try { return java.time.OffsetDateTime.parse(iso).toInstant().toString(); } catch (RuntimeException ignored) { }
        try { return java.time.LocalDateTime.parse(iso).atZone(target).toInstant().toString(); } catch (RuntimeException ignored) { }
        try { return LocalDate.parse(iso).atStartOfDay(target).toInstant().toString(); } catch (RuntimeException ignored) { }
        throw new IllegalArgumentException("时间格式无法识别：" + iso + "，请用 ISO-8601，例如 2026-11-12T07:00:00Z");
    }

    private static void normalizeTimes(ObjectNode normalized, String zone, String... fields) {
        for (String field : fields) {
            String value = text(normalized, field);
            if (value != null) normalized.put(field, utc(value, zone));
        }
    }

    private static String priorityLabel(String priority) {        return switch (priority == null ? "" : priority) {
            case "LOW" -> "低"; case "MEDIUM" -> "中"; case "HIGH" -> "高"; case "NONE" -> "无"; default -> null;
        };
    }

    /** ISO-8601 转用户可读时间；解析失败时原样返回，不吞掉模型给的原始值。 */
    static String readableTime(String iso, String zone) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return java.time.OffsetDateTime.parse(iso)
                    .atZoneSameInstant(ZoneId.of(zone == null || zone.isBlank() ? "Asia/Shanghai" : zone))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (RuntimeException ignored) { }
        try {
            return Instant.parse(iso).atZone(ZoneId.of(zone == null || zone.isBlank() ? "Asia/Shanghai" : zone))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (RuntimeException ignored) { return iso; }
    }

    private static String listName(TaskService tasks, String listId) {
        if (listId == null || listId.isBlank()) return null;
        return tasks.listLists().stream().filter(item -> listId.equals(item.publicId()))
                .map(TaskModels.TaskList::name).findFirst().orElse(null);
    }

    private static String parentTitle(TaskService tasks, String parentId) {
        if (parentId == null || parentId.isBlank()) return null;
        try { return "子任务 · " + tasks.get(parentId).title(); } catch (RuntimeException ignored) { return null; }
    }

    private static String minutesLabel(JsonNode node) {
        return node != null && node.isInt() ? minutesLabel(node.asInt()) : null;
    }

    private static String minutesLabel(Integer minutes) {
        if (minutes == null) return null;
        if (minutes <= 0) return "未设置";
        if (minutes < 60) return minutes + " 分钟";
        int hours = minutes / 60, rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
    }

    private static String countLabel(JsonNode array, String unit) {
        if (array == null || !array.isArray() || array.isEmpty()) return null;
        return array.size() + " " + unit;
    }

    private static String reminderLabel(JsonNode reminder) {
        if (reminder == null || !reminder.isObject()) return null;
        String kind = text(reminder, "kind");
        if ("RELATIVE".equals(kind)) {
            return reminder.path("offsetMinutes").isInt() ? "到期前 " + reminder.path("offsetMinutes").asInt() + " 分钟" : "按到期时间提醒";
        }
        if ("ABSOLUTE".equals(kind)) {
            String at = readableTime(text(reminder, "remindAt"), null);
            return at == null ? "指定时刻提醒" : at + " 提醒";
        }
        return "新增提醒";
    }

    /** prepare 快照里附带的展示信息（commit 只读 taskId/revision，多余字段无副作用）。 */
    private static ObjectNode taskSnapshot(ObjectMapper mapper, TaskModels.TaskItem task) {
        ObjectNode snapshot = mapper.createObjectNode().put("taskId", task.publicId()).put("revision", task.revision())
                .put("title", task.title());
        if (task.listId() != null) snapshot.put("listId", task.listId());
        if (task.dueAt() != null) snapshot.put("dueAt", task.dueAt());
        if (task.startAt() != null) snapshot.put("startAt", task.startAt());
        if (task.timezone() != null) snapshot.put("timezone", task.timezone());
        return snapshot;
    }

    private static ObjectNode diffRow(ObjectMapper mapper, String label, String before, String after) {
        return mapper.createObjectNode().put("label", label)
                .put("before", before == null || before.isBlank() ? "未填写" : before)
                .put("after", after == null || after.isBlank() ? "未填写" : after);
    }

    /** 把一行差异追加进 content.diff（前端直接渲染 before → after）。 */
    private static void diffRow(ObjectNode content, ObjectMapper mapper, String label, String before, String after) {
        content.withArray("diff").add(diffRow(mapper, label, before, after));
    }

    private static Card card(ObjectMapper mapper) { return new Card(mapper); }

    /**
     * 卡片行构造器：写入时同时产出 preview（camelCase，供接口与测试断言）和 previewRows（中文标签，
     * 前端 AgentChatView 优先按 previewRows 渲染）。新接入的动作只要用 Card，就不必再改前端分支。
     */
    private static final class Card {
        private final ObjectMapper mapper;
        private final ObjectNode preview;
        private final com.fasterxml.jackson.databind.node.ArrayNode rows;

        private Card(ObjectMapper mapper) {
            this.mapper = mapper;
            this.preview = mapper.createObjectNode();
            this.rows = mapper.createArrayNode();
        }

        Card text(String key, String label, String value) {
            if (value == null || value.isBlank()) return this;
            preview.put(key, value);
            rows.add(mapper.createObjectNode().put("label", label).put("value", value));
            return this;
        }

        Card time(String key, String label, String iso) { return text(key, label, readableTime(iso, null)); }

        ObjectNode content() {
            ObjectNode content = mapper.createObjectNode();
            content.set("preview", preview);
            if (!rows.isEmpty()) content.set("previewRows", rows);
            return content;
        }
    }

    private ObjectNode habitSchema(ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "name", "习惯名称，最长 120 字", null);
        ToolSchemas.enumProperty(schema, "frequency", "频率：DAILY 每天 / WEEKLY_N 每周 N 次 / MONTHLY_N 每月 N 次 / CUSTOM 指定星期",
                "DAILY", "WEEKLY_N", "MONTHLY_N", "CUSTOM");
        ToolSchemas.integerProperty(schema, "targetCount", "频率对应的目标次数（WEEKLY_N / MONTHLY_N 用），默认 1", 1, 1, 999);
        ToolSchemas.arrayProperty(schema, "customDays", "CUSTOM 频率下的星期，1=周一 … 7=周日",
                mapper.createObjectNode().put("type", "integer"), 1, 7);
        ToolSchemas.stringProperty(schema, "remindAt", "提醒时间 HH:mm；多个用逗号分隔，如 07:00,20:30", null);
        ToolSchemas.stringProperty(schema, "startDate", "开始日期 yyyy-MM-dd，默认今天", "date");
        ToolSchemas.stringProperty(schema, "groupKey", "分组名，最长 16 字；默认 other", null);
        return schema;
    }

    private ObjectNode countdownSchema(ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "title", "标题，最长 160 字", null);
        ToolSchemas.stringProperty(schema, "targetDate", "目标日期 yyyy-MM-dd（公历）", "date");
        ToolSchemas.stringProperty(schema, "groupId", "分组公开 ID；先用 countdown.list 或 countdown.groups.list 取得，不传用第一个分组", null);
        ToolSchemas.stringProperty(schema, "repeatRule",
                "重复规则：NONE/DAILY/WEEKLY/MONTHLY/YEARLY，或 CUSTOM:N 表示每隔 N 天（1-365）", null);
        ToolSchemas.booleanProperty(schema, "preciseSeconds", "是否按精确时刻显示");
        ToolSchemas.stringProperty(schema, "preciseTime", "精确时刻 HH:mm；给了就自动按精确时刻显示", null);
        ToolSchemas.enumProperty(schema, "unit", "展示单位", "DAY", "WEEK", "MONTH", "YEAR", "SECOND", "MINUTE", "HOUR");
        ToolSchemas.booleanProperty(schema, "pinned", "是否置顶");
        ToolSchemas.stringProperty(schema, "note", "备注，最长 1000 字", null);
        return schema;
    }

    private static List<Integer> customDays(JsonNode input) {
        var days = new java.util.ArrayList<Integer>();
        if (!input.path("customDays").isArray()) return days;
        for (JsonNode node : input.path("customDays")) {
            if (!node.isInt()) continue;
            int day = node.asInt();
            if (day < 1 || day > 7) throw new IllegalArgumentException("星期必须在 1 到 7 之间");
            if (!days.contains(day)) days.add(day);
        }
        return days;
    }

    private static List<Integer> intList(JsonNode node, String name) {
        var values = new java.util.ArrayList<Integer>();
        if (!node.path(name).isArray()) return values;
        for (JsonNode item : node.path(name)) if (item.isInt()) values.add(item.asInt());
        return values;
    }

    /** 提醒时间支持多个，用逗号分隔（如 07:00,20:30）；空串表示不提醒。 */
    private static String remindTimes(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "";
        for (String part : value.split(",")) {
            if (!part.trim().matches("([01]\\d|2[0-3]):[0-5]\\d"))
                throw new IllegalArgumentException("提醒时间必须形如 HH:mm，多个用逗号分隔");
        }
        return value;
    }

    private static String habitGroupKey(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        if (!value.matches("[\\p{L}\\p{N}_-]{1,16}"))
            throw new IllegalArgumentException("分组名只能包含文字、数字、下划线和短横线，最长 16 个字符");
        return value;
    }

    private static String habitFrequencyLabel(String frequency, int targetCount, List<Integer> days) {
        return switch (frequency == null ? "" : frequency) {
            case "DAILY" -> "每天";
            case "WEEKLY_N" -> "每周 " + targetCount + " 次";
            case "MONTHLY_N" -> "每月 " + targetCount + " 次";
            case "CUSTOM" -> "每周 " + days.stream().map(TaskToolsConfiguration::weekdayLabel)
                    .collect(java.util.stream.Collectors.joining("、"));
            case "INTERVAL" -> "每隔 " + targetCount + " 天";
            default -> frequency;
        };
    }

    private static String weekdayLabel(int day) {
        return switch (day) {
            case 1 -> "周一"; case 2 -> "周二"; case 3 -> "周三"; case 4 -> "周四";
            case 5 -> "周五"; case 6 -> "周六"; default -> "周日";
        };
    }

    private static TaskEfficiencyModels.Countdown findCountdown(TaskEfficiencyService efficiency, String countdownId) {
        return efficiency.countdowns().stream().filter(item -> countdownId.equals(item.publicId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("倒数日不存在：" + countdownId));
    }

    /** groupId 为空时落到第一个分组（与服务端「必须有一个分组」的约束一致）。 */
    private static TaskEfficiencyModels.CountdownGroup resolveCountdownGroup(TaskEfficiencyService efficiency,
                                                                             String groupId) {
        var groups = efficiency.countdownGroups();
        if (groups.isEmpty()) throw new IllegalArgumentException("还没有倒数日分组，请先在站内创建一个");
        if (groupId == null || groupId.isBlank()) return groups.get(0);
        return groups.stream().filter(group -> groupId.equals(group.publicId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("倒数日分组不存在：" + groupId));
    }

    private static String countdownGroupName(TaskEfficiencyService efficiency, String groupId) {
        if (groupId == null || groupId.isBlank()) return null;
        return efficiency.countdownGroups().stream().filter(group -> groupId.equals(group.publicId()))
                .map(TaskEfficiencyModels.CountdownGroup::name).findFirst().orElse(null);
    }

    private static String countdownRepeatRule(String raw) {
        String value = raw == null || raw.isBlank() ? "NONE" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (value.matches("CUSTOM:\\d{1,3}")) {
            int every = Integer.parseInt(value.substring(7));
            if (every >= 1 && every <= 365) return value;
            throw new IllegalArgumentException("自定义重复间隔必须在 1 到 365 天之间");
        }
        if (!List.of("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY", "CUSTOM").contains(value))
            throw new IllegalArgumentException("repeatRule 不支持：" + raw);
        return value;
    }

    private static String countdownRepeatLabel(String repeatRule) {
        if (repeatRule == null) return null;
        if (repeatRule.startsWith("CUSTOM:")) return "每 " + repeatRule.substring(7) + " 天";
        return switch (repeatRule) {
            case "NONE" -> "不重复"; case "DAILY" -> "每天"; case "WEEKLY" -> "每周";
            case "MONTHLY" -> "每月"; case "YEARLY" -> "每年"; default -> repeatRule;
        };
    }

    private static String countdownClock(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "";
        if (!value.matches("([01]\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException("精确时刻必须形如 HH:mm");
        return value;
    }

    private static String countdownUnit(String raw) {
        String value = raw == null || raw.isBlank() ? "DAY" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("DAY", "WEEK", "MONTH", "YEAR", "SECOND", "MINUTE", "HOUR").contains(value))
            throw new IllegalArgumentException("展示单位不支持：" + raw);
        return value;
    }

    private static String countdownInterval(LocalDate target, ZoneId zone) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(zone), target);
        if (days == 0) return "就是今天";
        return days > 0 ? "还有 " + days + " 天" : "已过 " + (-days) + " 天";
    }

    private static String joined(List<String> values) {
        if (values.isEmpty()) return null;
        return String.join("、", values.subList(0, Math.min(5, values.size())))
                + (values.size() > 5 ? " 等 " + values.size() + " 个" : "");
    }

    private DomainTool commit(String name, String prepareName, ToolRisk risk, PendingActionService actions,
                              CurrentUserResolver currentUser, ObjectMapper mapper, Committer committer) {
        ObjectNode schema=ToolSchemas.object(mapper);ToolSchemas.stringProperty(schema,"actionId","已批准 action ID",null);ToolSchemas.required(schema,"actionId");
        ToolDefinition definition=definition(name,"提交已批准操作；一个 action 只执行一次。",risk,schema);
        return new DomainTool(){public ToolDefinition definition(){return definition;} @Transactional public ToolResult execute(JsonNode input){
            String actionId=ToolInputs.requiredText(input,"actionId");long user=currentUser.id();PendingAction action=actions.getForUser(actionId,user);
            if(!prepareName.equals(action.toolName()))throw new IllegalArgumentException("action 与当前工具不匹配");
            if(action.status()==ActionStatus.COMPLETED)return ToolResult.completed("任务操作已完成（幂等重放）",mapper.createObjectNode().put("actionId",actionId).put("replayed",true));
            try{actions.beginCommit(actionId,user);JsonNode snapshot=mapper.readTree(action.inputSnapshot());Object result=committer.commit(snapshot,actionId);actions.transition(actionId,user,ActionStatus.COMPLETED);return ToolResult.completed("任务操作已完成",mapper.valueToTree(result));}
            catch(RuntimeException exception){markFailed(actions,actionId,user);return ToolResult.failed(exception.getMessage(),mapper.createObjectNode().put("actionId",actionId),actionId);}
            catch(Exception exception){markFailed(actions,actionId,user);return ToolResult.failed("无法读取 action 参数",mapper.createObjectNode().put("actionId",actionId),actionId);}}};
    }

    /**
     * beginCommit 之前就失败时 action 还停在待确认，直接 transition 会抛「不允许的状态转换」，
     * 把真正的失败原因盖掉。这里让状态流转尽力而为，错误信息必须保留。
     */
    private static void markFailed(PendingActionService actions, String actionId, long userId) {
        try { actions.transition(actionId, userId, ActionStatus.FAILED); } catch (RuntimeException ignored) { }
    }

    private ToolDefinition definition(String name,String description,ToolRisk risk,ObjectNode schema){return new ToolDefinition(name,1,description,risk,Set.of(risk==ToolRisk.R1?"task:read":"task:write"),schema);}
    private ObjectNode idSchema(ObjectMapper mapper){ObjectNode schema=ToolSchemas.object(mapper);ToolSchemas.stringProperty(schema,"taskId","任务公开 ID",null);ToolSchemas.required(schema,"taskId");return schema;}
    private ObjectNode taskSchema(ObjectMapper mapper){
        ObjectNode schema=ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema,"title","任务标题",null);
        ToolSchemas.stringProperty(schema,"description","任务描述",null);
        ToolSchemas.stringProperty(schema,"listId","清单公开 ID",null);
        ToolSchemas.enumProperty(schema,"priority","优先级","NONE","LOW","MEDIUM","HIGH");
        ToolSchemas.stringProperty(schema,"startAt","开始时间 ISO-8601","date-time");
        ToolSchemas.stringProperty(schema,"dueAt","到期时间 ISO-8601","date-time");
        ToolSchemas.booleanProperty(schema,"allDay","是否全天");
        ToolSchemas.stringProperty(schema,"timezone","IANA 时区",null);
        ToolSchemas.stringProperty(schema,"rrule","重复规则 RRULE",null);
        ToolSchemas.enumProperty(schema,"recurrenceAnchor","重复基准","DUE_DATE","COMPLETION_DATE");
        ToolSchemas.integerProperty(schema,"durationMinutes","预计时长（分钟）",30,0,1440);
        ToolSchemas.stringProperty(schema,"parentId","父任务公开 ID；填了就是创建子任务，子任务必须与父任务同清单",null);
        ToolSchemas.arrayProperty(schema,"tagIds","标签公开 ID 列表；先用 task.tags.list 取得真实 ID",mapper.createObjectNode().put("type","string"),0,20);
        ToolSchemas.arrayProperty(schema,"checklist","检查项列表；提供时整体替换原检查项",checklistSchema(mapper),0,50);
        ToolSchemas.objectProperty(schema,"reminder","新增一条站内提醒；只做新增，不覆盖已有提醒。RELATIVE 按到期时间提前 N 分钟，ABSOLUTE 直接给时刻",reminderSchema(mapper));
        return schema;
    }

    private static ObjectNode checklistSchema(ObjectMapper mapper){
        ObjectNode item=mapper.createObjectNode().put("type","object");
        ObjectNode properties=item.putObject("properties");
        properties.putObject("title").put("type","string").put("description","检查项内容");
        properties.putObject("completed").put("type","boolean").put("description","是否已完成");
        return ToolSchemas.required(item,"title");
    }

    /** 新增提醒：RELATIVE 按到期时间提前 N 分钟，ABSOLUTE 直接给时刻。 */
    private static ObjectNode reminderSchema(ObjectMapper mapper){
        ObjectNode reminder=mapper.createObjectNode().put("type","object")
                .put("description","新增一条站内提醒；只做新增，不覆盖已有提醒");
        ObjectNode properties=reminder.putObject("properties");
        properties.putObject("kind").put("type","string").put("description","RELATIVE 相对到期时间 / ABSOLUTE 绝对时间");
        properties.putObject("offsetMinutes").put("type","integer").put("description","RELATIVE 时提前的分钟数，0-10080");
        properties.putObject("remindAt").put("type","string").put("description","ABSOLUTE 时的提醒时刻 ISO-8601");
        properties.putObject("dailyUntilDone").put("type","boolean").put("description","完成后是否停止每日重复提醒");
        return reminder;
    }

    private List<String> stringList(JsonNode value,String name){
        if(!value.path(name).isArray())return List.of();
        var values=new java.util.ArrayList<String>();
        value.path(name).forEach(item->{String text=item.asText("");if(!text.isBlank())values.add(text);});
        return values;
    }

    private List<TaskModels.ChecklistCommand> checklist(JsonNode value){
        if(!value.path("checklist").isArray())return List.of();
        var items=new java.util.ArrayList<TaskModels.ChecklistCommand>();
        int order=0;
        for(JsonNode item:value.path("checklist")){
            String title=item.path("title").asText("").trim();
            if(title.isEmpty())continue;
            items.add(new TaskModels.ChecklistCommand(null,title,item.path("completed").asBoolean(false),order++));
        }
        return items;
    }

    private TaskModels.TaskCommand command(JsonNode value){
        return new TaskModels.TaskCommand(text(value,"listId"),ToolInputs.requiredText(value,"title"),
                text(value,"description"),text(value,"priority"),text(value,"startAt"),text(value,"dueAt"),
                value.path("allDay").asBoolean(false),text(value,"timezone"),
                value.path("durationMinutes").isInt()?value.path("durationMinutes").asInt():null,
                text(value,"parentId"),stringList(value,"tagIds"),checklist(value),
                text(value,"rrule"),text(value,"recurrenceAnchor"));
    }
    private static String text(JsonNode node,String name){String value=ToolInputs.optionalText(node,name);return value==null?null:value;}
    /**
     * 区分「没传」与「传了空串」：没传返回 null（保持原值），传了空串返回 ""（清空该字段）。
     * 更新类 commit 必须用它，否则「清空提醒/备注/解除关联」会被 optionalText 的 trim-空→null 吞掉。
     */
    private static String raw(JsonNode node,String name){
        if(!node.has(name)||node.get(name).isNull())return null;
        return node.path(name).asText();
    }
    private static int integer(JsonNode node,String name,int fallback){return node.path(name).isInt()?node.path(name).asInt():fallback;}
    private static void copy(JsonNode from,ObjectNode to,String...names){for(String name:names)if(from.hasNonNull(name))to.put(name,from.path(name).asText());}
    /** 数组/对象字段必须整节点拷贝，用 put 会把它们变成字符串。 */
    private static void copyRaw(JsonNode from,ObjectNode to,String...names){for(String name:names)if(from.hasNonNull(name))to.set(name,from.get(name));}
    @FunctionalInterface private interface Reader{Object read(JsonNode input);}
    @FunctionalInterface private interface Normalizer{ObjectNode normalize(JsonNode input);}
    @FunctionalInterface private interface Presenter{Presentation present(ObjectNode normalized);}
    /** 卡片标题（人类可读）+ 结构化预览内容（actionType/preview/diff 由前端按域渲染）。 */
    private record Presentation(String summary,ObjectNode content){}
    @FunctionalInterface private interface Committer{Object commit(JsonNode snapshot,String actionId);}
}
