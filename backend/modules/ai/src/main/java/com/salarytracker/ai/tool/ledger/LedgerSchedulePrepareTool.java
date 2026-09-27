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
import com.salarytracker.ledger.LedgerModels.Account;
import com.salarytracker.ledger.LedgerModels.Book;
import com.salarytracker.ledger.LedgerModels.CalendarRule;
import com.salarytracker.ledger.LedgerModels.Category;
import com.salarytracker.ledger.LedgerModels.CategoryKind;
import com.salarytracker.ledger.LedgerModels.Member;
import com.salarytracker.ledger.LedgerModels.NamedResource;
import com.salarytracker.ledger.LedgerModels.ScheduledTask;
import com.salarytracker.ledger.LedgerModels.ScheduledTaskCommand;
import com.salarytracker.ledger.LedgerModels.TransactionCommand;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import com.salarytracker.ledger.LedgerScheduledTaskService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class LedgerSchedulePrepareTool implements DomainTool {
    private final LedgerScheduleToolMode mode;
    private final LedgerScheduledTaskService schedules;
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerSchedulePrepareTool(LedgerScheduleToolMode mode, LedgerScheduledTaskService schedules,
                              LedgerBookService books, PendingActionService actions,
                              CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.mode = mode;
        this.schedules = schedules;
        this.books = books;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "bookName", "账本名称", null);
        if (mode != LedgerScheduleToolMode.CREATE) {
            ToolSchemas.stringProperty(schema, "taskId", "周期任务公开 ID", null);
            ToolSchemas.stringProperty(schema, "taskName", "周期任务名称", null);
        }
        if (mode.editable()) addEditableSchema(schema);
        ToolRisk risk = mode == LedgerScheduleToolMode.CREATE ? ToolRisk.R2 : ToolRisk.R3;
        definition = new ToolDefinition(mode.actionType() + ".prepare", 1,
                mode.label() + "前读取真实账本资源、生成完整预览，不立即写入业务数据。",
                risk, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        ObjectNode normalized = mapper.createObjectNode();
        ArrayNode missing = mapper.createArrayNode();
        ArrayNode fields = mapper.createArrayNode();
        ObjectNode entityMatches = mapper.createObjectNode();
        List<Book> availableBooks = books.books();
        Match<Book> bookMatch = match(availableBooks, ToolInputs.optionalText(input, "bookId"),
                ToolInputs.optionalText(input, "bookName"), Book::id, Book::name, true);
        put(normalized, "bookId", selectedId(bookMatch, Book::id));
        entityField(fields, "bookId", "账本", true, bookOptions(availableBooks));
        writeMatch(entityMatches, "bookId", ToolInputs.optionalText(input, "bookName"), bookMatch, bookOptions(availableBooks));
        if (bookMatch.selected() == null) missing.add("bookId");

        ScheduledTask before = null;
        Long revision = null;
        if (bookMatch.selected() != null) {
            String bookId = bookMatch.selected().id();
            List<ScheduledTask> taskOptions = schedules.list(bookId, false);
            if (mode == LedgerScheduleToolMode.CREATE) {
                normalizeEditable(input, normalized, missing, fields, entityMatches, bookId, null);
            } else {
                Match<ScheduledTask> taskMatch = match(taskOptions, ToolInputs.optionalText(input, "taskId"),
                        ToolInputs.optionalText(input, "taskName"), ScheduledTask::id, ScheduledTask::name, true);
                put(normalized, "taskId", selectedId(taskMatch, ScheduledTask::id));
                entityField(fields, "taskId", "周期任务", true, taskOptions(taskOptions));
                writeMatch(entityMatches, "taskId", ToolInputs.optionalText(input, "taskName"), taskMatch, taskOptions(taskOptions));
                if (taskMatch.selected() == null) missing.add("taskId");
                else {
                    before = taskMatch.selected();
                    revision = before.revision();
                    if (mode.editable()) normalizeEditable(input, normalized, missing, fields, entityMatches, bookId, before);
                }
            }
        }

        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, revision,
                !missing.isEmpty(), Duration.ofMinutes(15));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", mode.actionType());
        content.set("input", normalized);
        content.set("fields", fields);
        content.set("missingFields", missing);
        content.set("entityMatches", entityMatches);
        content.set("preview", preview(normalized, before));
        if (mode == LedgerScheduleToolMode.UPDATE && before != null) content.set("diff", diff(before, normalized));
        if (mode == LedgerScheduleToolMode.DELETE && before != null) content.set("effects", effects(before, false));
        if (mode == LedgerScheduleToolMode.RUN && before != null) content.set("effects", effects(before, true));
        if (!missing.isEmpty()) {
            return ToolResult.needsInput("请补充" + mode.label() + "所需信息", content,
                    action.id(), action.expiresAt().toString());
        }
        return ToolResult.needsConfirmation("请确认" + mode.label(), content,
                action.id(), action.expiresAt().toString());
    }

    private void addEditableSchema(ObjectNode schema) {
        ToolSchemas.stringProperty(schema, "name", "周期任务名称", null);
        ToolSchemas.booleanProperty(schema, "enabled", "是否启用");
        ToolSchemas.enumProperty(schema, "scheduleMode", "执行方式", "CALENDAR", "INTERVAL");
        ToolSchemas.enumProperty(schema, "frequency", "执行频率", "ONCE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY");
        ToolSchemas.integerProperty(schema, "intervalValue", "间隔数量", 1, 1, 365);
        ToolSchemas.stringProperty(schema, "startOn", "生效日期 yyyy-MM-dd", "date");
        ToolSchemas.stringProperty(schema, "endOn", "结束日期 yyyy-MM-dd", "date");
        ToolSchemas.integerProperty(schema, "maxRuns", "最大执行次数", 1, 1, 10000);
        ToolSchemas.enumProperty(schema, "monthlyMode", "每月规则", "DAY_OF_MONTH", "NTH_WEEKDAY");
        ToolSchemas.integerProperty(schema, "weekOfMonth", "每月第几周", 1, 1, 5);
        ToolSchemas.integerProperty(schema, "dayOfWeek", "星期一至星期日对应 1-7", 1, 1, 7);
        ToolSchemas.integerProperty(schema, "dayOfMonth", "每月几号", 1, 1, 31);
        ToolSchemas.integerProperty(schema, "month", "月份", 1, 1, 12);
        ToolSchemas.enumProperty(schema, "kind", "周期流水类型", "EXPENSE", "INCOME");
        ToolSchemas.numberProperty(schema, "amount", "周期流水金额", 0D);
        ToolSchemas.stringProperty(schema, "accountId", "账户公开 ID", null);
        ToolSchemas.stringProperty(schema, "accountName", "账户名称", null);
        ToolSchemas.stringProperty(schema, "categoryId", "二级分类公开 ID", null);
        ToolSchemas.stringProperty(schema, "categoryName", "二级分类名称或完整路径", null);
        ToolSchemas.stringProperty(schema, "merchantId", "商家公开 ID", null);
        ToolSchemas.stringProperty(schema, "merchantName", "商家名称", null);
        ToolSchemas.stringProperty(schema, "memberId", "成员公开 ID", null);
        ToolSchemas.stringProperty(schema, "memberName", "成员名称或用户名", null);
        ToolSchemas.stringProperty(schema, "projectId", "项目公开 ID", null);
        ToolSchemas.stringProperty(schema, "projectName", "项目名称", null);
        ToolSchemas.stringProperty(schema, "note", "备注", null);
    }

    private void normalizeEditable(JsonNode input, ObjectNode out, ArrayNode missing, ArrayNode fields,
                                   ObjectNode matches, String bookId, ScheduledTask before) {
        TransactionCommand payload = before == null ? null : before.payload();
        String kind = value(input, "kind", payload == null || payload.kind() == null ? "EXPENSE" : payload.kind().name()).toUpperCase();
        out.put("name", value(input, "name", before == null ? null : before.name()));
        out.put("enabled", booleanValue(input, "enabled", before == null || before.enabled()));
        String frequency = value(input, "frequency", before == null ? "MONTHLY" : before.frequency()).toUpperCase();
        String defaultMode = before == null
                ? (Set.of("ONCE", "DAILY").contains(frequency) ? "INTERVAL" : "CALENDAR")
                : before.scheduleMode();
        out.put("scheduleMode", value(input, "scheduleMode", defaultMode).toUpperCase());
        out.put("frequency", frequency);
        out.put("intervalValue", intValue(input, "intervalValue", before == null ? 1 : before.intervalValue()));
        out.put("startOn", value(input, "startOn", before == null ? LocalDate.now().toString() : before.startOn().toString()));
        nullable(out, "endOn", value(input, "endOn", before == null || before.endOn() == null ? null : before.endOn().toString()));
        nullable(out, "maxRuns", integerValue(input, "maxRuns", before == null ? null : before.maxRuns()));
        CalendarRule rule = before == null ? null : before.calendarRule();
        out.put("monthlyMode", value(input, "monthlyMode", rule == null ? "DAY_OF_MONTH" : rule.monthlyMode()).toUpperCase());
        nullable(out, "weekOfMonth", integerValue(input, "weekOfMonth", rule == null ? null : rule.weekOfMonth()));
        nullable(out, "dayOfWeek", integerValue(input, "dayOfWeek", rule == null ? null : rule.dayOfWeek()));
        nullable(out, "dayOfMonth", integerValue(input, "dayOfMonth", rule == null ? null : rule.dayOfMonth()));
        nullable(out, "month", integerValue(input, "month", rule == null ? null : rule.month()));
        out.put("kind", kind);
        nullable(out, "amount", decimalValue(input, "amount", payload == null ? null : payload.amount()));
        nullable(out, "note", value(input, "note", payload == null ? null : payload.note()));

        List<Account> accounts = books.accounts(bookId, false);
        List<Category> allCategories = books.categories(bookId, false);
        CategoryKind categoryKind = "INCOME".equals(kind) ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        List<Category> categories = allCategories.stream().filter(item -> item.parentId() != null && item.kind() == categoryKind).toList();
        List<NamedResource> merchants = books.merchants(bookId, false);
        List<NamedResource> projects = books.projects(bookId, false);
        List<Member> members = books.members(bookId);

        String accountId = resolve(matches, "accountId", accounts,
                ToolInputs.optionalText(input, "accountId"), ToolInputs.optionalText(input, "accountName"),
                payload == null ? null : payload.accountId(), Account::id, Account::name, accountOptions(accounts), true);
        String categoryId = resolveCategory(matches, categories, allCategories,
                ToolInputs.optionalText(input, "categoryId"), ToolInputs.optionalText(input, "categoryName"),
                payload == null ? null : payload.categoryId());
        String merchantId = resolve(matches, "merchantId", merchants,
                ToolInputs.optionalText(input, "merchantId"), ToolInputs.optionalText(input, "merchantName"),
                payload == null ? null : payload.merchantId(), NamedResource::id, NamedResource::name,
                namedOptions(merchants), false);
        String projectId = resolve(matches, "projectId", projects,
                ToolInputs.optionalText(input, "projectId"), ToolInputs.optionalText(input, "projectName"),
                payload == null ? null : payload.projectId(), NamedResource::id, NamedResource::name,
                namedOptions(projects), false);
        String memberId = resolveMember(matches, members, input, payload == null ? null : payload.memberId());
        nullable(out, "accountId", accountId); nullable(out, "categoryId", categoryId);
        nullable(out, "merchantId", merchantId); nullable(out, "memberId", memberId); nullable(out, "projectId", projectId);

        editableFields(fields, accounts, categories, allCategories, merchants, members, projects);
        if (!out.hasNonNull("name")) missing.add("name");
        if (!out.hasNonNull("amount") || out.path("amount").decimalValue().compareTo(BigDecimal.ZERO) <= 0) missing.add("amount");
        if (accountId == null) missing.add("accountId");
        if (categoryId == null) missing.add("categoryId");
        String scheduleMode = out.path("scheduleMode").asText();
        if ("CALENDAR".equals(scheduleMode)) {
            if ("WEEKLY".equals(frequency) && !out.hasNonNull("dayOfWeek")) missing.add("dayOfWeek");
            if ("MONTHLY".equals(frequency) && "DAY_OF_MONTH".equals(out.path("monthlyMode").asText()) && !out.hasNonNull("dayOfMonth")) missing.add("dayOfMonth");
            if ("MONTHLY".equals(frequency) && "NTH_WEEKDAY".equals(out.path("monthlyMode").asText())) {
                if (!out.hasNonNull("weekOfMonth")) missing.add("weekOfMonth");
                if (!out.hasNonNull("dayOfWeek")) missing.add("dayOfWeek");
            }
            if ("YEARLY".equals(frequency)) {
                if (!out.hasNonNull("month")) missing.add("month");
                if (!out.hasNonNull("dayOfMonth")) missing.add("dayOfMonth");
            }
        }
        if (missing.isEmpty()) schedules.previewFirstRun(command(out));
    }

    private void editableFields(ArrayNode fields, List<Account> accounts, List<Category> categories,
                                List<Category> allCategories, List<NamedResource> merchants,
                                List<Member> members, List<NamedResource> projects) {
        field(fields, "name", "任务名称", "text", true);
        select(fields, "enabled", "任务状态", true, new String[][]{{"true", "启用"}, {"false", "暂停"}});
        select(fields, "scheduleMode", "执行方式", true, new String[][]{{"CALENDAR", "固定日期"}, {"INTERVAL", "间隔执行"}});
        select(fields, "frequency", "执行频率", true, new String[][]{{"ONCE", "仅一次"}, {"DAILY", "每日"}, {"WEEKLY", "每周"}, {"MONTHLY", "每月"}, {"YEARLY", "每年"}});
        field(fields, "intervalValue", "间隔数量", "number", true);
        field(fields, "startOn", "生效日期", "date", true);
        field(fields, "endOn", "结束日期", "date", false);
        field(fields, "maxRuns", "最大执行次数", "number", false);
        select(fields, "monthlyMode", "每月规则", false, new String[][]{{"DAY_OF_MONTH", "指定日期"}, {"NTH_WEEKDAY", "第 N 个星期几"}});
        field(fields, "weekOfMonth", "第几周", "number", false);
        select(fields, "dayOfWeek", "星期", false, new String[][]{{"1", "星期一"}, {"2", "星期二"}, {"3", "星期三"}, {"4", "星期四"}, {"5", "星期五"}, {"6", "星期六"}, {"7", "星期日"}});
        field(fields, "dayOfMonth", "日期", "number", false);
        field(fields, "month", "月份", "number", false);
        select(fields, "kind", "流水类型", true, new String[][]{{"EXPENSE", "支出"}, {"INCOME", "收入"}});
        field(fields, "amount", "金额", "money", true);
        entityField(fields, "accountId", "账户", true, accountOptions(accounts));
        entityField(fields, "categoryId", "二级分类", true, categoryOptions(categories, allCategories));
        entityField(fields, "merchantId", "商家", false, namedOptions(merchants));
        entityField(fields, "memberId", "成员", false, memberOptions(members));
        entityField(fields, "projectId", "项目", false, namedOptions(projects));
        field(fields, "note", "备注", "textarea", false);
    }

    private ObjectNode preview(ObjectNode values, ScheduledTask before) {
        ObjectNode preview = mapper.createObjectNode();
        preview.put("operation", mode.label());
        if (before != null) preview.set("before", mapper.valueToTree(before));
        if (mode.editable() && values.hasNonNull("name")) {
            ObjectNode after = values.deepCopy();
            if (values.hasNonNull("bookId") && complete(values)) {
                after.put("nextRunOn", schedules.previewFirstRun(command(values)).toString());
                addResourceNames(after, values.path("bookId").asText());
            }
            preview.set("after", after);
        }
        if (before != null && mode == LedgerScheduleToolMode.RUN) {
            preview.put("dueOn", before.nextRunOn().toString());
            preview.put("amount", before.payload().amount());
            preview.put("kind", before.payload().kind().name());
        }
        return preview;
    }

    private ArrayNode diff(ScheduledTask before, ObjectNode values) {
        ArrayNode rows = mapper.createArrayNode();
        change(rows, "任务名称", before.name(), values.path("name").asText());
        change(rows, "状态", before.enabled() ? "启用" : "暂停", values.path("enabled").asBoolean() ? "启用" : "暂停");
        change(rows, "执行方式", before.scheduleMode(), values.path("scheduleMode").asText());
        change(rows, "执行频率", before.frequency(), values.path("frequency").asText());
        change(rows, "金额", String.valueOf(before.payload().amount()), values.path("amount").asText());
        change(rows, "生效日期", String.valueOf(before.startOn()), values.path("startOn").asText());
        change(rows, "结束日期", String.valueOf(before.endOn()), values.path("endOn").asText());
        return rows;
    }

    private ArrayNode effects(ScheduledTask task, boolean run) {
        ArrayNode effects = mapper.createArrayNode();
        if (run) {
            effects.add("确认后将立即生成一笔真实流水并刷新账户余额与报表");
            effects.add("同一任务和到期日已执行时将返回重复，不会再次记账");
        } else {
            effects.add("删除后任务将停止自动执行");
            effects.add("已经生成的 " + task.runCount() + " 笔历史流水不会被删除");
        }
        return effects;
    }

    private ScheduledTaskCommand command(JsonNode values) {
        CalendarRule rule = new CalendarRule(optional(values, "monthlyMode"), integer(values, "weekOfMonth"),
                integer(values, "dayOfWeek"), integer(values, "dayOfMonth"), integer(values, "month"));
        TransactionCommand payload = new TransactionCommand(null, required(values, "accountId"), null,
                optional(values, "categoryId"), optional(values, "merchantId"), optional(values, "memberId"),
                optional(values, "projectId"), TransactionKind.valueOf(required(values, "kind")),
                values.path("amount").decimalValue(), "CNY", null, null, null, null,
                optional(values, "note"), "scheduled-task", null, null, null);
        return new ScheduledTaskCommand(null, "RECURRING_TRANSACTION", required(values, "name"),
                values.path("enabled").asBoolean(true), required(values, "scheduleMode"), required(values, "frequency"),
                values.path("intervalValue").asInt(1), rule, LocalDate.parse(required(values, "startOn")),
                date(values, "endOn"), integer(values, "maxRuns"), payload);
    }

    private void addResourceNames(ObjectNode after, String bookId) {
        String accountId = optional(after, "accountId");
        String categoryId = optional(after, "categoryId");
        String merchantId = optional(after, "merchantId");
        String memberId = optional(after, "memberId");
        String projectId = optional(after, "projectId");
        nullable(after, "accountName", name(books.accounts(bookId, false), accountId, Account::id, Account::name));
        nullable(after, "categoryName", name(books.categories(bookId, false), categoryId, Category::id, Category::name));
        nullable(after, "merchantName", name(books.merchants(bookId, false), merchantId, NamedResource::id, NamedResource::name));
        nullable(after, "memberName", name(books.members(bookId), memberId, Member::id, Member::displayName));
        nullable(after, "projectName", name(books.projects(bookId, false), projectId, NamedResource::id, NamedResource::name));
    }

    private String resolveCategory(ObjectNode matches, List<Category> options, List<Category> all,
                                   String id, String query, String fallbackId) {
        if (id == null && query == null) id = fallbackId;
        Match<Category> match;
        if (id != null) match = match(options, id, null, Category::id, Category::name, true);
        else {
            String compact = compact(query);
            List<Category> candidates = options.stream().filter(item -> compact(item.name()).contains(compact)
                    || compact(categoryLabel(item, all)).contains(compact) || compact.contains(compact(item.name()))).toList();
            match = candidates.size() == 1 ? new Match<>("suggested", candidates.get(0), candidates)
                    : new Match<>(candidates.isEmpty() ? "missing" : "ambiguous", null, candidates);
        }
        writeMatch(matches, "categoryId", query, match, categoryOptions(options, all));
        return selectedId(match, Category::id);
    }

    private String resolveMember(ObjectNode matches, List<Member> members, JsonNode input, String fallbackId) {
        String id = ToolInputs.optionalText(input, "memberId");
        String query = ToolInputs.optionalText(input, "memberName");
        if (id == null && query == null) {
            id = fallbackId;
            if (id == null) {
                Member current = members.stream().filter(item -> item.userId() == currentUser.id()).findFirst().orElse(null);
                id = current == null ? null : current.id();
            }
        }
        return resolve(matches, "memberId", members, id, query, fallbackId, Member::id,
                item -> item.displayName() + " " + item.username(), memberOptions(members), false);
    }

    private <T> String resolve(ObjectNode matches, String field, List<T> options, String id, String query,
                               String fallbackId, Function<T, String> idOf, Function<T, String> labelOf,
                               ArrayNode optionNodes, boolean required) {
        if (id == null && query == null) id = fallbackId;
        Match<T> match = match(options, id, query, idOf, labelOf, required);
        writeMatch(matches, field, query, match, optionNodes);
        return selectedId(match, idOf);
    }

    private <T> Match<T> match(List<T> options, String id, String query,
                               Function<T, String> idOf, Function<T, String> labelOf, boolean suggestOnly) {
        if (id != null) {
            T selected = options.stream().filter(item -> idOf.apply(item).equals(id)).findFirst().orElse(null);
            return new Match<>(selected == null ? "missing" : "exact", selected,
                    selected == null ? List.of() : List.of(selected));
        }
        if (query == null) {
            if (suggestOnly && options.size() == 1) return new Match<>("suggested", options.get(0), options);
            return new Match<>(options.isEmpty() ? "missing" : "ambiguous", null, options);
        }
        String compact = compact(query);
        List<T> candidates = options.stream().filter(item -> compact(labelOf.apply(item)).contains(compact)
                || compact.contains(compact(labelOf.apply(item)))).toList();
        return candidates.size() == 1 ? new Match<>("suggested", candidates.get(0), candidates)
                : new Match<>(candidates.isEmpty() ? "missing" : "ambiguous", null, candidates);
    }

    private <T> void writeMatch(ObjectNode matches, String field, String query, Match<T> match, ArrayNode options) {
        ObjectNode node = matches.putObject(field);
        node.put("status", match.status());
        if (query != null) node.put("query", query);
        ArrayNode candidates = node.putArray("candidates");
        Set<String> ids = match.candidates().stream().map(item -> {
            if (item instanceof Book value) return value.id();
            if (item instanceof ScheduledTask value) return value.id();
            if (item instanceof Account value) return value.id();
            if (item instanceof Category value) return value.id();
            if (item instanceof NamedResource value) return value.id();
            if (item instanceof Member value) return value.id();
            return "";
        }).collect(java.util.stream.Collectors.toSet());
        options.forEach(option -> { if (ids.contains(option.path("value").asText())) candidates.add(option); });
    }

    private boolean complete(JsonNode value) {
        return value.hasNonNull("name") && value.hasNonNull("amount") && value.hasNonNull("accountId")
                && value.hasNonNull("categoryId") && value.hasNonNull("startOn");
    }

    private ArrayNode bookOptions(List<Book> values) { return options(values, Book::id, Book::name, item -> item.currency()); }
    private ArrayNode taskOptions(List<ScheduledTask> values) { return options(values, ScheduledTask::id, ScheduledTask::name, item -> scheduleLabel(item)); }
    private ArrayNode accountOptions(List<Account> values) { return options(values, Account::id, Account::name, item -> item.accountType() + " · " + item.currency()); }
    private ArrayNode namedOptions(List<NamedResource> values) { return options(values, NamedResource::id, NamedResource::name, item -> item.note()); }
    private ArrayNode memberOptions(List<Member> values) { return options(values, Member::id, Member::displayName, item -> item.username() + " · " + item.roleName()); }
    private ArrayNode categoryOptions(List<Category> values, List<Category> all) { return options(values, Category::id, item -> categoryLabel(item, all), item -> item.kind().name()); }

    private <T> ArrayNode options(List<T> values, Function<T, String> id, Function<T, String> label,
                                  Function<T, String> description) {
        ArrayNode result = mapper.createArrayNode();
        values.forEach(value -> {
            ObjectNode option = result.addObject().put("value", id.apply(value)).put("label", label.apply(value));
            String detail = description.apply(value);
            if (detail != null && !detail.isBlank()) option.put("description", detail);
        });
        return result;
    }

    private String categoryLabel(Category value, List<Category> all) {
        if (value.parentId() == null) return value.name();
        return all.stream().filter(item -> item.id().equals(value.parentId())).findFirst()
                .map(parent -> parent.name() + " / " + value.name()).orElse(value.name());
    }

    private String scheduleLabel(ScheduledTask task) {
        return task.frequency() + " · 下次 " + task.nextRunOn() + " · rev " + task.revision();
    }

    private void field(ArrayNode fields, String name, String label, String type, boolean required) {
        fields.addObject().put("name", name).put("label", label).put("type", type).put("required", required);
    }

    private void select(ArrayNode fields, String name, String label, boolean required, String[][] options) {
        ObjectNode field = fields.addObject().put("name", name).put("label", label).put("type", "select").put("required", required);
        ArrayNode values = field.putArray("options");
        for (String[] option : options) values.addObject().put("value", option[0]).put("label", option[1]);
    }

    private void entityField(ArrayNode fields, String name, String label, boolean required, ArrayNode options) {
        fields.addObject().put("name", name).put("label", label).put("type", "entity-picker")
                .put("required", required).set("options", options);
    }

    private void change(ArrayNode rows, String label, String before, String after) {
        if (java.util.Objects.equals(before, after)) return;
        rows.addObject().put("label", label).put("before", before == null ? "" : before).put("after", after == null ? "" : after);
    }

    private <T> String name(List<T> values, String id, Function<T, String> idOf, Function<T, String> nameOf) {
        if (id == null) return null;
        return values.stream().filter(item -> idOf.apply(item).equals(id)).findFirst().map(nameOf).orElse(id);
    }

    private <T> String selectedId(Match<T> match, Function<T, String> idOf) {
        return match.selected() == null ? null : idOf.apply(match.selected());
    }

    private String value(JsonNode input, String field, String fallback) {
        String value = ToolInputs.optionalText(input, field);
        return value == null ? fallback : value;
    }

    private boolean booleanValue(JsonNode input, String field, boolean fallback) {
        Boolean value = ToolInputs.optionalBoolean(input, field);
        return value == null ? fallback : value;
    }

    private int intValue(JsonNode input, String field, int fallback) {
        JsonNode value = input.get(field);
        return value == null || value.isNull() ? fallback : value.asInt();
    }

    private Integer integerValue(JsonNode input, String field, Integer fallback) {
        JsonNode value = input.get(field);
        return value == null || value.isNull() ? fallback : Integer.valueOf(value.asInt());
    }

    private BigDecimal decimalValue(JsonNode input, String field, BigDecimal fallback) {
        JsonNode value = input.get(field);
        return value == null || value.isNull() ? fallback : value.decimalValue();
    }

    private String required(JsonNode values, String field) {
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

    private LocalDate date(JsonNode values, String field) {
        String value = optional(values, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private void put(ObjectNode node, String field, String value) { if (value == null) node.putNull(field); else node.put(field, value); }
    private void nullable(ObjectNode node, String field, String value) { if (value == null) node.putNull(field); else node.put(field, value); }
    private void nullable(ObjectNode node, String field, Integer value) { if (value == null) node.putNull(field); else node.put(field, value); }
    private void nullable(ObjectNode node, String field, BigDecimal value) { if (value == null) node.putNull(field); else node.put(field, value); }
    private String compact(String value) { return value == null ? "" : value.toLowerCase().replaceAll("[\\s/／_-]+", ""); }

    private record Match<T>(String status, T selected, List<T> candidates) { }
}
