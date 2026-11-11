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
    @Bean public DomainTool taskListQueryTool(TaskService tasks, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "listId", "任务清单公开 ID", null);
        ToolSchemas.enumProperty(schema, "status", "状态", "OPEN", "COMPLETED");
        ToolSchemas.enumProperty(schema, "view", "视图", "TODAY", "NEXT7", "INBOX", "ALL", "COMPLETED");
        ToolSchemas.stringProperty(schema, "tagId", "标签公开 ID", null);
        ToolSchemas.enumProperty(schema, "priority", "优先级", "NONE", "LOW", "MEDIUM", "HIGH");
        ToolSchemas.stringProperty(schema, "from", "开始日期 yyyy-MM-dd；与 to 同时提供时按日期范围查询", "date");
        ToolSchemas.stringProperty(schema, "to", "结束日期 yyyy-MM-dd（含）；与 from 同时提供", "date");
        ToolSchemas.integerProperty(schema, "page", "页码", 0, 0, 1000);
        ToolSchemas.integerProperty(schema, "size", "每页数量", 50, 1, 200);
        return read("task.list.query", "查询当前用户任务，可按清单、状态、视图、标签或日期范围筛选。", schema, mapper,
                input -> {
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
                    return tasks.listTasks(text(input,"listId"), text(input,"status"), text(input,"view"),
                            text(input,"tagId"), text(input,"priority"), dueFrom, dueTo,
                            integer(input,"page",0), integer(input,"size",50));
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
        return read("habit.checkin.query", "查询当前用户习惯清单，或按习惯查询打卡统计。", schema, mapper,
                input -> text(input, "habitId") == null ? efficiency.habits(false)
                        : efficiency.habitStats(text(input, "habitId"), text(input, "from"), text(input, "to")));
    }

    @Bean public DomainTool focusStatsQueryTool(TaskEfficiencyService efficiency, ObjectMapper mapper) {
        return read("focus.stats.query", "查询当前用户今日、本周、本月专注统计。", ToolSchemas.object(mapper), mapper,
                input -> efficiency.focusStats());
    }

    @Bean public DomainTool taskCreatePrepareTool(TaskService tasks, PendingActionService actions,
                                            CurrentUserResolver currentUser, ObjectMapper mapper) {
        ObjectNode schema = taskSchema(mapper); ToolSchemas.required(schema, "title");
        ToolDefinition definition = definition("task.create.prepare", "校验单条任务并生成确认预览，不写入任务数据。", ToolRisk.R2, schema);
        return prepare(definition, actions, currentUser, mapper, input -> {
            String title = ToolInputs.requiredText(input, "title");
            ObjectNode normalized = mapper.createObjectNode().put("title", title);
            copy(input, normalized, "listId", "description", "priority", "startAt", "dueAt", "timezone", "rrule", "recurrenceAnchor");
            normalized.put("allDay", input.path("allDay").asBoolean(false));
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
            String detail = java.util.stream.Stream.of(listName == null ? "收件箱" : listName, due)
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(" · "));
            return new Presentation("新建任务：" + title + (detail.isBlank() ? "" : "（" + detail + "）"),
                    mapper.createObjectNode().set("preview", p));
        });
    }

    @Bean public DomainTool taskCreateCommitTool(TaskService tasks, PendingActionService actions,
                                           CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.create.commit", "task.create.prepare", ToolRisk.R2, actions, currentUser, mapper,
                (snapshot, actionId) -> tasks.create(command(snapshot), "agent-task-create-" + actionId));
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
        ToolDefinition definition=definition("task.update.prepare","校验单条任务字段修改并生成确认预览，不修改任务。",ToolRisk.R3,schema);
        return prepare(definition,actions,currentUser,mapper,input->{var task=tasks.get(ToolInputs.requiredText(input,"taskId"));
            String title=text(input,"title") == null ? task.title() : text(input,"title");String description=text(input,"description") == null ? task.description() : text(input,"description");
            String priority=text(input,"priority") == null ? task.priority() : text(input,"priority");
            TaskModels.TaskCommand command=new TaskModels.TaskCommand(task.listId(),title,description,priority,task.startAt(),task.dueAt(),task.allDay(),task.timezone(),task.durationMinutes(),task.parentId(),task.tagIds(),
                    task.checklist().stream().map(item->new TaskModels.ChecklistCommand(item.publicId(),item.title(),item.completed(),item.sortOrder())).toList(),task.rrule(),task.recurrenceAnchor());
            ObjectNode normalized=mapper.createObjectNode().put("taskId",task.publicId()).put("revision",task.revision()).put("title",task.title());
            normalized.put("beforeTitle",task.title()); normalized.put("beforeDescription",task.description()); normalized.put("beforePriority",task.priority());
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
            ObjectNode content = mapper.createObjectNode().set("preview", p);
            var diff = mapper.createArrayNode();
            if (!java.util.Objects.equals(beforeTitle, title)) diff.add(diffRow(mapper, "标题", beforeTitle, title));
            if (!java.util.Objects.equals(beforeDescription, description)) diff.add(diffRow(mapper, "描述", beforeDescription, description));
            if (!java.util.Objects.equals(priorityLabel(beforePriority), priorityLabel(priority))) {
                diff.add(diffRow(mapper, "优先级", priorityLabel(beforePriority), priorityLabel(priority)));
            }
            if (diff.size() > 0) content.set("diff", diff);
            return new Presentation("修改任务：" + title, content);
        });
    }

    @Bean public DomainTool taskUpdateCommitTool(TaskService tasks, PendingActionService actions,
                                                 CurrentUserResolver currentUser, ObjectMapper mapper) {
        return commit("task.update.commit","task.update.prepare",ToolRisk.R3,actions,currentUser,mapper,(snapshot,actionId)->
                tasks.update(ToolInputs.requiredText(snapshot,"taskId"),mapper.convertValue(snapshot.path("command"),TaskModels.TaskCommand.class),snapshot.path("revision").asText()));
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

    private static String priorityLabel(String priority) {
        return switch (priority == null ? "" : priority) {
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
            catch(RuntimeException exception){actions.transition(actionId,user,ActionStatus.FAILED);return ToolResult.failed(exception.getMessage(),mapper.createObjectNode().put("actionId",actionId),actionId);}
            catch(Exception exception){actions.transition(actionId,user,ActionStatus.FAILED);return ToolResult.failed("无法读取 action 参数",mapper.createObjectNode().put("actionId",actionId),actionId);}}};
    }

    private ToolDefinition definition(String name,String description,ToolRisk risk,ObjectNode schema){return new ToolDefinition(name,1,description,risk,Set.of(risk==ToolRisk.R1?"task:read":"task:write"),schema);}
    private ObjectNode idSchema(ObjectMapper mapper){ObjectNode schema=ToolSchemas.object(mapper);ToolSchemas.stringProperty(schema,"taskId","任务公开 ID",null);ToolSchemas.required(schema,"taskId");return schema;}
    private ObjectNode taskSchema(ObjectMapper mapper){ObjectNode schema=ToolSchemas.object(mapper);ToolSchemas.stringProperty(schema,"title","任务标题",null);ToolSchemas.stringProperty(schema,"description","任务描述",null);ToolSchemas.stringProperty(schema,"listId","清单公开 ID",null);ToolSchemas.enumProperty(schema,"priority","优先级","NONE","LOW","MEDIUM","HIGH");ToolSchemas.stringProperty(schema,"startAt","开始时间 ISO-8601","date-time");ToolSchemas.stringProperty(schema,"dueAt","到期时间 ISO-8601","date-time");ToolSchemas.booleanProperty(schema,"allDay","是否全天");ToolSchemas.stringProperty(schema,"timezone","IANA 时区",null);ToolSchemas.stringProperty(schema,"rrule","重复规则 RRULE",null);ToolSchemas.enumProperty(schema,"recurrenceAnchor","重复基准","DUE_DATE","COMPLETION_DATE");return schema;}
    private TaskModels.TaskCommand command(JsonNode value){return new TaskModels.TaskCommand(text(value,"listId"),ToolInputs.requiredText(value,"title"),text(value,"description"),text(value,"priority"),text(value,"startAt"),text(value,"dueAt"),value.path("allDay").asBoolean(false),text(value,"timezone"),null,null,List.of(),List.of(),text(value,"rrule"),text(value,"recurrenceAnchor"));}
    private static String text(JsonNode node,String name){String value=ToolInputs.optionalText(node,name);return value==null?null:value;}
    private static int integer(JsonNode node,String name,int fallback){return node.path(name).isInt()?node.path(name).asInt():fallback;}
    private static void copy(JsonNode from,ObjectNode to,String...names){for(String name:names)if(from.hasNonNull(name))to.put(name,from.path(name).asText());}
    @FunctionalInterface private interface Reader{Object read(JsonNode input);}
    @FunctionalInterface private interface Normalizer{ObjectNode normalize(JsonNode input);}
    @FunctionalInterface private interface Presenter{Presentation present(ObjectNode normalized);}
    /** 卡片标题（人类可读）+ 结构化预览内容（actionType/preview/diff 由前端按域渲染）。 */
    private record Presentation(String summary,ObjectNode content){}
    @FunctionalInterface private interface Committer{Object commit(JsonNode snapshot,String actionId);}
}
