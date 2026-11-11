package com.salarytracker.ai.tool.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.approval.AgentApproval;
import com.salarytracker.ai.approval.AgentApprovalService;
import com.salarytracker.ai.approval.TaskApprovalExecutor;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolInputs;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.task.TaskEnhancementModels;
import com.salarytracker.task.TaskEnhancementService;
import com.salarytracker.task.TaskModels;
import com.salarytracker.task.TaskService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * P4：任务域最高风险操作（R4）。四个动作都只能生成站内审批，模型侧不可见
 * （{@code AgentPromptPolicy.modelVisible} 直接挡掉 R4），执行只发生在
 * {@code /approvals} 里由用户再次确认之后。
 *
 * <p>与 P3 的差别：prepare 不再返回可点的 action 卡片，而是
 * {@code commitAvailable=false} + {@code confirmationUrl=/approvals/{id}}；
 * commit 由 {@link TaskApprovalExecutor} 承接，审批中心批准后直接调用执行器，
 * 不经过模型。
 */
@Configuration
public class TaskR4ToolsConfiguration {
    /** R4 涉及不可逆操作，确认窗口比 R3 长，但也必须过期，避免几个月前的快照被执行。 */
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_BATCH = 100;

    // ---------------------------------------------------------------- 批量删除

    @Bean public DomainTool taskBatchDeletePrepareTool(TaskService tasks, PendingActionService actions,
                                                       AgentApprovalService approvals, CurrentUserResolver currentUser,
                                                       ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.arrayProperty(schema, "taskIds", "要删除的任务公开 ID，最多 " + MAX_BATCH + " 个",
                mapper.createObjectNode().put("type", "string"), 1, MAX_BATCH);
        ToolSchemas.required(schema, "taskIds");
        return prepare("task.batch.delete.prepare",
                "批量删除任务（最高风险，R4）。冻结每个任务的当前版本并创建站内审批，不直接删除。", schema,
                actions, approvals, currentUser, mapper, input -> {
                    LinkedHashSet<String> ids = new LinkedHashSet<>();
                    JsonNode raw = input.path("taskIds");
                    if (!raw.isArray() || raw.isEmpty()) throw new IllegalArgumentException("taskIds 至少 1 个任务");
                    raw.forEach(node -> {
                        String id = node.asText("").trim();
                        if (!id.isEmpty()) ids.add(id);
                    });
                    if (ids.isEmpty()) throw new IllegalArgumentException("taskIds 至少 1 个任务");
                    if (ids.size() > MAX_BATCH) {
                        throw new IllegalArgumentException("一次最多删除 " + MAX_BATCH + " 个任务，请分批");
                    }
                    ObjectNode normalized = mapper.createObjectNode();
                    ArrayNode targets = normalized.putArray("targets");
                    List<String> titles = new ArrayList<>();
                    int subtasks = 0;
                    for (String id : ids) {
                        TaskModels.TaskItem task = tasks.get(id);
                        targets.add(mapper.createObjectNode().put("taskId", task.publicId())
                                .put("revision", task.revision()));
                        titles.add(task.title());
                        subtasks += task.totalSubtasks();
                    }
                    normalized.put("count", ids.size());
                    normalized.put("subtaskCount", subtasks);
                    normalized.put("titles", titles.size() <= 8 ? String.join("、", titles)
                            : String.join("、", titles.subList(0, 8)) + " 等 " + titles.size() + " 个");
                    return normalized;
                }, normalized -> {
                    int count = normalized.path("count").asInt();
                    int subtasks = normalized.path("subtaskCount").asInt();
                    Rows rows = new Rows(mapper)
                            .number("count", count).number("subtaskCount", subtasks)
                            .row("数量", count + " 个任务")
                            .text("titles", "任务", normalized.path("titles").asText())
                            .row("子任务", subtasks > 0 ? subtasks + " 个（随父任务一起删除）" : null);
                    rows.stats(stat(count, "任务"), stat(subtasks, "含子任务"))
                            .labels("批量删除任务", "批准并删除 " + count + " 个任务", "/tasks")
                            .effect(count + " 个任务会移入回收站，30 天内可以从回收站恢复")
                            .effect("子任务会随父任务一起删除")
                            .effect("审批只删除当前冻结的 " + count + " 个任务；审批期间被修改的任务会因版本冲突失败");
                    return new R4Card("批量删除 " + count + " 个任务", rows.content());
                });
    }

    @Bean public DomainTool taskBatchDeleteCommitTool(TaskApprovalExecutor executor, ObjectMapper mapper) {
        return commit("task.batch.delete.commit", "批量删除任务", executor, mapper);
    }

    // ---------------------------------------------------------------- 删除清单

    @Bean public DomainTool taskListDeletePrepareTool(TaskService tasks, PendingActionService actions,
                                                      AgentApprovalService approvals, CurrentUserResolver currentUser,
                                                      ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "listId", "清单公开 ID；先用 task.lists.list 取得", null);
        ToolSchemas.required(schema, "listId");
        return prepare("task.lists.delete.prepare",
                "删除清单（最高风险，R4）。预览清单内任务数量并创建站内审批，不直接删除。", schema,
                actions, approvals, currentUser, mapper, input -> {
                    String listId = ToolInputs.requiredText(input, "listId");
                    TaskModels.TaskList list = tasks.listLists().stream()
                            .filter(value -> value.publicId().equals(listId)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("清单不存在"));
                    if ("INBOX".equals(list.systemKey())) {
                        throw new IllegalArgumentException("收集箱是系统清单，不能删除");
                    }
                    long taskCount = tasks.listTasks(listId, null, 0, 1).total();
                    return mapper.createObjectNode().put("listId", listId).put("name", list.name())
                            .put("revision", list.revision()).put("taskCount", taskCount)
                            .put("archived", list.archived());
                }, normalized -> {
                    long taskCount = normalized.path("taskCount").asLong();
                    Rows rows = new Rows(mapper)
                            .text("name", "清单", normalized.path("name").asText())
                            .number("taskCount", taskCount)
                            .row("清单内任务", taskCount + " 个")
                            .row("归档状态", normalized.path("archived").asBoolean(false) ? "已归档" : null);
                    rows.stats(stat(taskCount, "清单内任务"))
                            .labels("删除清单", "批准并删除清单", "/tasks")
                            .effect("清单会软删除，可以从清单回收站恢复")
                            .effect(taskCount + " 个任务不会被删除，但会一起从任务列表和智能清单里消失")
                            .effect("审批执行前会核对清单版本，清单已被修改时执行会失败");
                    return new R4Card("删除清单：" + normalized.path("name").asText(), rows.content());
                });
    }

    @Bean public DomainTool taskListDeleteCommitTool(TaskApprovalExecutor executor, ObjectMapper mapper) {
        return commit("task.lists.delete.commit", "删除清单", executor, mapper);
    }

    // ---------------------------------------------------------------- 永久清除

    @Bean public DomainTool taskPurgePrepareTool(TaskService tasks, PendingActionService actions,
                                                 AgentApprovalService approvals, CurrentUserResolver currentUser,
                                                 ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "taskId", "回收站里的任务公开 ID", null);
        ToolSchemas.stringProperty(schema, "confirmation",
                "可选确认字符串，必须是 DELETE:任务ID；不传则由服务端按同一规则冻结", null);
        ToolSchemas.required(schema, "taskId");
        return prepare("task.purge.prepare",
                "永久清除回收站里的单个任务（最高风险，R4）。冻结快照并创建站内审批，不直接清除。", schema,
                actions, approvals, currentUser, mapper, input -> {
                    String taskId = ToolInputs.requiredText(input, "taskId");
                    String confirmation = ToolInputs.optionalText(input, "confirmation");
                    String expected = "DELETE:" + taskId;
                    if (confirmation != null && !confirmation.isBlank() && !expected.equals(confirmation.trim())) {
                        throw new IllegalArgumentException("确认字符串必须是 " + expected);
                    }
                    TaskModels.TaskItem task = trashed(tasks, taskId);
                    String zone = zone(tasks, task);
                    return mapper.createObjectNode().put("taskId", taskId).put("revision", task.revision())
                            .put("title", task.title()).put("listName", listName(tasks, task.listId()))
                            .put("deletedAt", readableTime(task.deletedAt(), zone))
                            .put("subtaskCount", task.totalSubtasks())
                            .put("confirmation", expected);
                }, normalized -> {
                    int subtasks = normalized.path("subtaskCount").asInt();
                    Rows rows = new Rows(mapper)
                            .text("title", "标题", normalized.path("title").asText())
                            .text("listName", "清单", normalized.path("listName").asText())
                            .text("deletedAt", "删除时间", normalized.path("deletedAt").asText())
                            .number("subtaskCount", subtasks)
                            .row("子任务", subtasks > 0 ? subtasks + " 个（不会一起清除）" : null);
                    rows.stats(stat(1, "永久清除"))
                            .labels("永久清除任务", "批准并永久清除", "/tasks/trash")
                            .effect("永久清除后无法恢复，回收站里也不会再出现")
                            .effect("子任务不会被清除，会脱离父任务变成独立任务")
                            .effect("审批只清除这一个任务，不包含审批期间新删除的任务");
                    return new R4Card("永久清除任务：" + normalized.path("title").asText(), rows.content());
                });
    }

    @Bean public DomainTool taskPurgeCommitTool(TaskApprovalExecutor executor, ObjectMapper mapper) {
        return commit("task.purge.commit", "永久清除任务", executor, mapper);
    }

    // ---------------------------------------------------------------- 导入确认

    @Bean public DomainTool taskImportConfirmPrepareTool(TaskEnhancementService enhancement, PendingActionService actions,
                                                         AgentApprovalService approvals, CurrentUserResolver currentUser,
                                                         ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "previewId", "导入预览 ID；不传则取最近一次未提交的预览", null);
        ToolSchemas.booleanProperty(schema, "skipDuplicates", "是否跳过重复行，默认 true");
        return prepare("task.import.confirm.prepare",
                "确认一次已经解析好的任务导入（最高风险，R4）。不能创建预览，只能确认网页里真实存在的预览批次。",
                schema, actions, approvals, currentUser, mapper, input -> {
                    String previewId = ToolInputs.optionalText(input, "previewId");
                    TaskEnhancementModels.ImportBatchView batch = previewId == null || previewId.isBlank()
                            ? latestPending(enhancement) : enhancement.importBatch(previewId.trim());
                    if (batch.committed()) throw new IllegalArgumentException("该预览已经提交过，不会重复导入");
                    boolean skipDuplicates = !input.has("skipDuplicates") || input.path("skipDuplicates").asBoolean(true);
                    return mapper.createObjectNode().put("previewId", batch.previewId())
                            .put("fileName", batch.fileName()).put("valid", batch.valid())
                            .put("duplicates", batch.duplicates()).put("errors", batch.errors())
                            .put("skipDuplicates", skipDuplicates);
                }, normalized -> {
                    int valid = normalized.path("valid").asInt();
                    int duplicates = normalized.path("duplicates").asInt();
                    int errors = normalized.path("errors").asInt();
                    Rows rows = new Rows(mapper)
                            .text("fileName", "文件", normalized.path("fileName").asText())
                            .number("valid", valid).number("duplicates", duplicates).number("errors", errors)
                            .row("预计写入", valid + " 条任务")
                            .row("重复行", duplicates + (normalized.path("skipDuplicates").asBoolean(true) ? " 条（跳过）" : " 条（导入）"))
                            .row("错误行", errors + " 条（跳过）");
                    rows.stats(stat(valid, "预计写入"), stat(duplicates, "重复跳过"), stat(errors, "错误跳过"))
                            .labels("导入任务", "批准并导入 " + valid + " 条", "/tasks")
                            .effect("有效行会创建为任务，重复行按策略处理，错误行始终跳过")
                            .effect("同一个预览只能提交一次，重复批准不会重复导入")
                            .effect("文件里不存在的清单和标签会按名称自动新建");
                    return new R4Card("导入任务：" + normalized.path("fileName").asText(), rows.content());
                });
    }

    @Bean public DomainTool taskImportConfirmCommitTool(TaskApprovalExecutor executor, ObjectMapper mapper) {
        return commit("task.import.confirm.commit", "导入任务", executor, mapper);
    }

    // ---------------------------------------------------------------- 内部实现

    private DomainTool prepare(String name, String description, ObjectNode schema, PendingActionService actions,
                               AgentApprovalService approvals, CurrentUserResolver currentUser,
                               ObjectMapper mapper, Normalizer normalizer, Presenter presenter) {
        ToolDefinition definition = new ToolDefinition(name, 1, description, ToolRisk.R4, Set.of("task:write"), schema);
        return new DomainTool() {
            @Override public ToolDefinition definition() { return definition; }

            @Override public ToolResult execute(JsonNode input) {
                ObjectNode normalized = normalizer.normalize(input == null ? mapper.createObjectNode() : input);
                R4Card card = presenter.present(normalized);
                ObjectNode content = card.content();
                content.put("actionType", name.substring(0, name.length() - ".prepare".length()));
                content.put("commitAvailable", false);
                content.put("webApprovalRequired", true);
                content.set("input", normalized);
                Long revision = normalized.hasNonNull("revision") ? normalized.path("revision").asLong() : null;
                PendingAction action = actions.prepare(currentUser.id(), definition, normalized, revision, false, TTL);
                AgentApproval approval = approvals.create(action, null, card.summary(), content);
                content.put("approvalId", approval.id());
                return new ToolResult(ToolStatus.NEEDS_CONFIRMATION, card.summary(), content, action.id(),
                        "/approvals/" + approval.id(), action.expiresAt().toString(), null);
            }
        };
    }

    private DomainTool commit(String name, String label, TaskApprovalExecutor executor, ObjectMapper mapper) {
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "actionId", "已由站内审批批准的 action ID", null);
        ToolSchemas.required(schema, "actionId");
        ToolDefinition definition = new ToolDefinition(name, 1, "执行已通过站内审批的" + label + "操作。",
                ToolRisk.R4, Set.of("task:write"), schema);
        return new DomainTool() {
            @Override public ToolDefinition definition() { return definition; }

            @Override public ToolResult execute(JsonNode input) {
                return executor.commit(ToolInputs.requiredText(input, "actionId"));
            }
        };
    }

    /** 回收站里的任务：get() 只查未删除的，永久清除前必须先确认它在回收站。 */
    private static TaskModels.TaskItem trashed(TaskService tasks, String taskId) {
        try {
            return tasks.getDeleted(taskId);
        } catch (NotFoundException exception) {
            try {
                tasks.get(taskId);
            } catch (NotFoundException missing) {
                throw new IllegalArgumentException("任务不存在");
            }
            throw new IllegalArgumentException("任务不在回收站，永久清除前必须先删除");
        }
    }

    private static TaskEnhancementModels.ImportBatchView latestPending(TaskEnhancementService enhancement) {
        return enhancement.pendingImports().stream().filter(batch -> !batch.committed()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "没有待确认的导入预览，请先在任务页上传文件并完成预览"));
    }

    private static String listName(TaskService tasks, String listId) {
        if (listId == null || listId.isBlank()) return null;
        return tasks.listLists().stream().filter(list -> list.publicId().equals(listId))
                .map(TaskModels.TaskList::name).findFirst().orElse(null);
    }

    private static String zone(TaskService tasks, TaskModels.TaskItem task) {
        String zone = task.timezone();
        if (zone != null && !zone.isBlank()) return zone;
        TaskModels.Settings settings = tasks.settings();
        return settings == null || settings.timezone() == null || settings.timezone().isBlank()
                ? "Asia/Shanghai" : settings.timezone();
    }

    private static String readableTime(String iso, String zone) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.of(zone == null || zone.isBlank() ? "Asia/Shanghai" : zone))
                    .format(Instant.parse(iso));
        } catch (RuntimeException exception) {
            return iso;
        }
    }

    private static ObjectNode stat(long value, String label) {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("value", value).put("label", label);
    }

    /** 与 P3 的 Card 一致：一次写入同时产出 preview（camelCase）与 previewRows（中文标签）。 */
    private static final class Rows {
        private final ObjectMapper mapper;
        private final ObjectNode preview;
        private final ArrayNode rows;
        private final ArrayNode stats;
        private final ArrayNode effects;
        private String typeLabel;
        private String approveLabel;
        private String returnPath;

        private Rows(ObjectMapper mapper) {
            this.mapper = mapper;
            this.preview = mapper.createObjectNode();
            this.rows = mapper.createArrayNode();
            this.stats = mapper.createArrayNode();
            this.effects = mapper.createArrayNode();
        }

        /** 字符串字段：同时进 preview 和卡片行。 */
        Rows text(String key, String label, String value) {
            if (value == null || value.isBlank()) return this;
            preview.put(key, value);
            rows.add(mapper.createObjectNode().put("label", label).put("value", value));
            return this;
        }

        /** 数值字段只进 preview：preview 里要给接口/测试留真实数字，不能写「2 个任务」这种展示串。 */
        Rows number(String key, long value) { preview.put(key, value); return this; }

        /** 纯展示行：只在卡片上出现，不污染 preview 的键。 */
        Rows row(String label, String value) {
            if (value != null && !value.isBlank()) {
                rows.add(mapper.createObjectNode().put("label", label).put("value", value));
            }
            return this;
        }

        Rows stats(ObjectNode... values) {
            for (ObjectNode value : values) if (value.path("value").asLong() > 0) stats.add(value);
            return this;
        }

        Rows effect(String value) { effects.add(value); return this; }

        Rows labels(String typeLabel, String approveLabel, String returnPath) {
            this.typeLabel = typeLabel;
            this.approveLabel = approveLabel;
            this.returnPath = returnPath;
            return this;
        }

        ObjectNode content() {
            ObjectNode content = mapper.createObjectNode();
            content.set("preview", preview);
            if (!rows.isEmpty()) content.set("previewRows", rows);
            if (!stats.isEmpty()) content.set("stats", stats);
            content.set("effects", effects);
            content.put("typeLabel", typeLabel);
            content.put("approveLabel", approveLabel);
            content.put("returnPath", returnPath);
            return content;
        }
    }

    private record R4Card(String summary, ObjectNode content) { }

    @FunctionalInterface private interface Normalizer { ObjectNode normalize(JsonNode input); }

    @FunctionalInterface private interface Presenter { R4Card present(ObjectNode normalized); }
}
