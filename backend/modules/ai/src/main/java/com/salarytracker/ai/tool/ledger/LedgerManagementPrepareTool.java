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
import com.salarytracker.ledger.LedgerModels.Category;

import java.time.Duration;
import java.util.List;
import java.util.Set;

final class LedgerManagementPrepareTool implements DomainTool {
    private final LedgerManagementToolMode mode;
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    LedgerManagementPrepareTool(LedgerManagementToolMode mode, LedgerBookService books,
                                PendingActionService actions, CurrentUserResolver currentUser,
                                ObjectMapper mapper) {
        this.mode = mode;
        this.books = books;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        if (!mode.equals(LedgerManagementToolMode.BOOK_CREATE)) {
            ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        }
        if ((mode.account() || mode.category()) && !mode.create()) {
            ToolSchemas.stringProperty(schema, "resourceId", mode.resourceLabel() + "公开 ID", null);
        }
        if (!mode.delete()) addEditableSchema(schema);
        ToolRisk risk = mode == LedgerManagementToolMode.BOOK_DELETE ? ToolRisk.R4
                : mode.delete() ? ToolRisk.R3 : ToolRisk.R2;
        definition = new ToolDefinition(mode.actionType() + ".prepare", 1,
                mode.actionLabel() + "前生成完整预览，不立即写入业务数据。",
                risk, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        List<Book> availableBooks = books.books();
        ObjectNode normalized = mapper.createObjectNode();
        ArrayNode missing = mapper.createArrayNode();
        ArrayNode fields = mapper.createArrayNode();
        Long revision = null;
        Object before = null;

        if (mode == LedgerManagementToolMode.BOOK_CREATE) {
            normalizeBookCreate(input, normalized, missing, fields, availableBooks);
        } else {
            String bookId = ToolInputs.optionalText(input, "bookId");
            if (bookId == null && availableBooks.size() == 1) bookId = availableBooks.get(0).id();
            put(normalized, "bookId", bookId);
            if (bookId == null) {
                missing.add("bookId");
                entityField(fields, "bookId", "账本", true, bookOptions(availableBooks));
            } else if (mode.book()) {
                Book book = findBook(availableBooks, bookId);
                before = book;
                revision = book.revision();
                if (mode.update()) normalizeBookUpdate(input, normalized, fields, book);
            } else if (mode.account()) {
                List<Account> accounts = books.accounts(bookId, true);
                if (mode.create()) normalizeAccountCreate(input, normalized, missing, fields, findBook(availableBooks, bookId));
                else {
                    String resourceId = ToolInputs.optionalText(input, "resourceId");
                    put(normalized, "resourceId", resourceId);
                    if (resourceId == null) {
                        missing.add("resourceId");
                        entityField(fields, "resourceId", "账户", true, accountOptions(accounts));
                    } else {
                        Account account = findAccount(accounts, resourceId);
                        before = account;
                        revision = account.revision();
                        if (mode.update()) normalizeAccountUpdate(input, normalized, fields, account);
                    }
                }
            } else {
                List<Category> categories = books.categories(bookId, true);
                if (mode.create()) normalizeCategoryCreate(input, normalized, missing, fields, categories);
                else {
                    String resourceId = ToolInputs.optionalText(input, "resourceId");
                    put(normalized, "resourceId", resourceId);
                    if (resourceId == null) {
                        missing.add("resourceId");
                        entityField(fields, "resourceId", "分类", true, categoryOptions(categories, false));
                    } else {
                        Category category = findCategory(categories, resourceId);
                        before = category;
                        revision = category.revision();
                        if (mode.update()) normalizeCategoryUpdate(input, normalized, fields, category, categories);
                    }
                }
            }
        }

        if (mode.delete() && before != null) addDeleteFields(fields, normalized);
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, revision,
                !missing.isEmpty(), Duration.ofMinutes(15));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", mode.actionType());
        content.set("input", normalized);
        content.set("preview", preview(before, normalized));
        content.set("fields", fields);
        content.set("missingFields", missing);
        if (mode.update() && before != null) content.set("diff", diff(before, normalized));
        if (mode.delete() && before != null) content.set("effects", deleteEffects(before));
        if (mode == LedgerManagementToolMode.BOOK_DELETE) {
            content.put("webApprovalRequired", true);
            content.put("commitAvailable", false);
        }
        if (!missing.isEmpty()) {
            return ToolResult.needsInput("请补充" + mode.actionLabel() + "所需信息", content,
                    action.id(), action.expiresAt().toString());
        }
        return ToolResult.needsConfirmation(
                mode == LedgerManagementToolMode.BOOK_DELETE
                        ? "账本删除属于高风险操作，当前仅生成影响预览"
                        : "请确认" + mode.actionLabel(),
                content, action.id(), action.expiresAt().toString());
    }

    private void addEditableSchema(ObjectNode schema) {
        if (mode.book()) {
            ToolSchemas.stringProperty(schema, "name", "账本名称", null);
            ToolSchemas.stringProperty(schema, "currency", "三位货币代码", null);
            ToolSchemas.enumProperty(schema, "mode", "新账本初始化方式", "EMPTY", "SYSTEM_TEMPLATE", "COPY");
            ToolSchemas.stringProperty(schema, "sourceBookId", "复制来源账本 ID", null);
            ToolSchemas.booleanProperty(schema, "archived", "是否归档");
        } else if (mode.account()) {
            ToolSchemas.stringProperty(schema, "name", "账户名称", null);
            ToolSchemas.stringProperty(schema, "icon", "账户图标代码", null);
            ToolSchemas.enumProperty(schema, "accountType", "账户类型", "cash", "bank", "card", "wallet", "other");
            ToolSchemas.stringProperty(schema, "currency", "三位货币代码", null);
            ToolSchemas.numberProperty(schema, "openingBalance", "初始余额", -1000000000000D);
            ToolSchemas.booleanProperty(schema, "hidden", "是否停用或隐藏");
        } else {
            ToolSchemas.stringProperty(schema, "name", "分类名称", null);
            ToolSchemas.stringProperty(schema, "icon", "分类图标代码", null);
            ToolSchemas.enumProperty(schema, "kind", "分类类型", "INCOME", "EXPENSE");
            ToolSchemas.stringProperty(schema, "parentId", "父分类公开 ID", null);
            ToolSchemas.stringProperty(schema, "color", "分类颜色", null);
            ToolSchemas.booleanProperty(schema, "hidden", "是否停用或隐藏");
        }
    }

    private void normalizeBookCreate(JsonNode input, ObjectNode out, ArrayNode missing, ArrayNode fields,
                                     List<Book> availableBooks) {
        put(out, "name", ToolInputs.optionalText(input, "name"));
        out.put("currency", value(input, "currency", "CNY").toUpperCase());
        out.put("mode", value(input, "mode", "SYSTEM_TEMPLATE").toUpperCase());
        put(out, "sourceBookId", ToolInputs.optionalText(input, "sourceBookId"));
        if (!out.hasNonNull("name")) missing.add("name");
        if ("COPY".equals(out.path("mode").asText()) && !out.hasNonNull("sourceBookId")) missing.add("sourceBookId");
        field(fields, "name", "账本名称", "text", true);
        field(fields, "currency", "币种", "text", true);
        select(fields, "mode", "初始化方式", true,
                new String[][]{{"EMPTY", "空账本"}, {"SYSTEM_TEMPLATE", "使用系统模板"}, {"COPY", "复制现有账本"}});
        entityField(fields, "sourceBookId", "复制来源", false, bookOptions(availableBooks));
    }

    private void normalizeBookUpdate(JsonNode input, ObjectNode out, ArrayNode fields, Book before) {
        out.put("name", value(input, "name", before.name()));
        out.put("currency", value(input, "currency", before.currency()).toUpperCase());
        out.put("archived", bool(input, "archived", before.archived()));
        field(fields, "name", "账本名称", "text", true);
        field(fields, "currency", "币种", "text", true);
        select(fields, "archived", "归档状态", true, new String[][]{{"false", "正常"}, {"true", "已归档"}});
    }

    private void normalizeAccountCreate(JsonNode input, ObjectNode out, ArrayNode missing, ArrayNode fields, Book book) {
        put(out, "name", ToolInputs.optionalText(input, "name"));
        out.put("icon", value(input, "icon", "wallet"));
        out.put("accountType", value(input, "accountType", "cash").toLowerCase());
        out.put("currency", value(input, "currency", book.currency()).toUpperCase());
        out.put("openingBalance", decimal(input, "openingBalance", "0"));
        out.put("hidden", bool(input, "hidden", false));
        if (!out.hasNonNull("name")) missing.add("name");
        addAccountFields(fields);
    }

    private void normalizeAccountUpdate(JsonNode input, ObjectNode out, ArrayNode fields, Account before) {
        out.put("name", value(input, "name", before.name()));
        out.put("icon", value(input, "icon", before.icon()));
        out.put("accountType", value(input, "accountType", before.accountType()).toLowerCase());
        out.put("currency", value(input, "currency", before.currency()).toUpperCase());
        out.put("openingBalance", decimal(input, "openingBalance", before.openingBalance().toPlainString()));
        out.put("hidden", bool(input, "hidden", before.hidden()));
        addAccountFields(fields);
    }

    private void addAccountFields(ArrayNode fields) {
        field(fields, "name", "账户名称", "text", true);
        field(fields, "icon", "图标", "text", true);
        select(fields, "accountType", "账户类型", true,
                new String[][]{{"cash", "现金"}, {"bank", "银行账户"}, {"card", "银行卡"}, {"wallet", "电子钱包"}, {"other", "其他"}});
        field(fields, "currency", "币种", "text", true);
        field(fields, "openingBalance", "初始余额", "money", true);
        select(fields, "hidden", "使用状态", true, new String[][]{{"false", "启用"}, {"true", "停用"}});
    }

    private void normalizeCategoryCreate(JsonNode input, ObjectNode out, ArrayNode missing, ArrayNode fields,
                                         List<Category> categories) {
        put(out, "name", ToolInputs.optionalText(input, "name"));
        out.put("icon", value(input, "icon", "tag"));
        out.put("kind", value(input, "kind", "EXPENSE").toUpperCase());
        put(out, "parentId", ToolInputs.optionalText(input, "parentId"));
        put(out, "color", ToolInputs.optionalText(input, "color"));
        out.put("hidden", bool(input, "hidden", false));
        if (!out.hasNonNull("name")) missing.add("name");
        addCategoryFields(fields, categories);
    }

    private void normalizeCategoryUpdate(JsonNode input, ObjectNode out, ArrayNode fields, Category before,
                                         List<Category> categories) {
        out.put("name", value(input, "name", before.name()));
        out.put("icon", value(input, "icon", before.icon()));
        out.put("kind", value(input, "kind", before.kind().name()).toUpperCase());
        if (input != null && input.has("parentId")) put(out, "parentId", ToolInputs.optionalText(input, "parentId"));
        else put(out, "parentId", before.parentId());
        out.put("color", value(input, "color", before.color()));
        out.put("hidden", bool(input, "hidden", before.hidden()));
        addCategoryFields(fields, categories.stream().filter(item -> !item.id().equals(before.id())).toList());
    }

    private void addCategoryFields(ArrayNode fields, List<Category> categories) {
        field(fields, "name", "分类名称", "text", true);
        field(fields, "icon", "图标", "text", true);
        select(fields, "kind", "收支类型", true, new String[][]{{"EXPENSE", "支出"}, {"INCOME", "收入"}});
        entityField(fields, "parentId", "父分类", false, categoryOptions(categories, true));
        field(fields, "color", "颜色", "text", false);
        select(fields, "hidden", "使用状态", true, new String[][]{{"false", "启用"}, {"true", "停用"}});
    }

    private void addDeleteFields(ArrayNode fields, ObjectNode normalized) {
        if (fields.isEmpty()) {
            if (mode.book()) entityField(fields, "bookId", "账本", true, bookOptions(books.books()));
            else field(fields, "resourceId", mode.resourceLabel(), "text", true);
        }
    }

    private ObjectNode preview(Object before, ObjectNode normalized) {
        ObjectNode preview = mapper.createObjectNode();
        preview.put("resourceType", mode.resourceLabel());
        preview.put("operation", mode.actionLabel());
        if (before != null) preview.set("before", mapper.valueToTree(before));
        preview.set("after", normalized.deepCopy());
        return preview;
    }

    private ArrayNode diff(Object before, ObjectNode normalized) {
        JsonNode source = mapper.valueToTree(before);
        ArrayNode diff = mapper.createArrayNode();
        normalized.fields().forEachRemaining(entry -> {
            if (entry.getKey().equals("bookId") || entry.getKey().equals("resourceId")) return;
            String left = source.path(entry.getKey()).isMissingNode() || source.path(entry.getKey()).isNull()
                    ? "" : source.path(entry.getKey()).asText();
            String right = entry.getValue().isNull() ? "" : entry.getValue().asText();
            if (!left.equalsIgnoreCase(right)) diff.addObject().put("label", fieldLabel(entry.getKey()))
                    .put("before", left).put("after", right);
        });
        return diff;
    }

    private ArrayNode deleteEffects(Object before) {
        ArrayNode effects = mapper.createArrayNode();
        if (before instanceof Book book) {
            effects.add("账本包含 " + book.transactionCount() + " 笔流水和 " + book.memberCount() + " 名成员")
                    .add("当前增量不会执行账本删除，需等待站内高风险审批中心");
        } else if (before instanceof Account account) {
            effects.add("账户当前余额为 " + account.balance() + " " + account.currency())
                    .add("删除采用软删除，历史流水、审计和回收站记录仍保留");
        } else if (before instanceof Category category) {
            effects.add(category.parentId() == null ? "一级分类删除前必须先处理其二级分类" : "该二级分类将停止用于新流水")
                    .add("删除采用软删除，历史流水、审计和回收站记录仍保留");
        }
        effects.add("提交前会再次校验 revision，数据已变化时不会覆盖");
        return effects;
    }

    private Book findBook(List<Book> values, String id) { return values.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("账本不存在或无权访问")); }
    private Account findAccount(List<Account> values, String id) { return values.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("账户不存在")); }
    private Category findCategory(List<Category> values, String id) { return values.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("分类不存在")); }

    private ArrayNode bookOptions(List<Book> values) { ArrayNode out = mapper.createArrayNode(); values.forEach(v -> out.addObject().put("value", v.id()).put("label", v.name()).put("description", v.currency())); return out; }
    private ArrayNode accountOptions(List<Account> values) { ArrayNode out = mapper.createArrayNode(); values.forEach(v -> out.addObject().put("value", v.id()).put("label", v.name()).put("description", v.accountType())); return out; }
    private ArrayNode categoryOptions(List<Category> values, boolean rootsOnly) { ArrayNode out = mapper.createArrayNode(); values.stream().filter(v -> !rootsOnly || v.parentId() == null).forEach(v -> out.addObject().put("value", v.id()).put("label", v.name()).put("description", v.kind().name()).put("kind", v.kind().name())); return out; }

    private void field(ArrayNode fields, String name, String label, String type, boolean required) { fields.addObject().put("name", name).put("label", label).put("type", type).put("required", required); }
    private void entityField(ArrayNode fields, String name, String label, boolean required, ArrayNode options) { ObjectNode field = fields.addObject(); field.put("name", name).put("label", label).put("type", "entity-picker").put("required", required); field.set("options", options); }
    private void select(ArrayNode fields, String name, String label, boolean required, String[][] options) { ObjectNode field = fields.addObject(); field.put("name", name).put("label", label).put("type", "select").put("required", required); ArrayNode out = field.putArray("options"); for (String[] option : options) out.addObject().put("value", option[0]).put("label", option[1]); }
    private void put(ObjectNode target, String name, String value) { if (value == null) target.putNull(name); else target.put(name, value); }
    private String value(JsonNode input, String name, String fallback) { String value = ToolInputs.optionalText(input, name); return value == null ? fallback : value; }
    private boolean bool(JsonNode input, String name, boolean fallback) { Boolean value = ToolInputs.optionalBoolean(input, name); return value == null ? fallback : value; }
    private java.math.BigDecimal decimal(JsonNode input, String name, String fallback) { var value = ToolInputs.optionalDecimal(input, name); return value == null ? new java.math.BigDecimal(fallback) : value; }
    private String fieldLabel(String field) { return switch (field) { case "name" -> "名称"; case "currency" -> "币种"; case "archived" -> "归档"; case "icon" -> "图标"; case "accountType" -> "账户类型"; case "openingBalance" -> "初始余额"; case "hidden" -> "使用状态"; case "kind" -> "收支类型"; case "parentId" -> "父分类"; case "color" -> "颜色"; default -> field; }; }
}
