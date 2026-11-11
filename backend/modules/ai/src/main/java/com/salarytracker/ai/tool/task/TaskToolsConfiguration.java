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

    private DomainTool read(String name, String description, ObjectNode schema, ObjectMapper mapper, Reader reader) {
        ToolDefinition definition = definition(name, description, ToolRisk.R1, schema);
        return new DomainTool() { public ToolDefinition definition(){return definition;} public ToolResult execute(JsonNode input){
            Object value=reader.read(input==null?mapper.createObjectNode():input); return ToolResult.completed(description,mapper.valueToTree(value)); }};
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
