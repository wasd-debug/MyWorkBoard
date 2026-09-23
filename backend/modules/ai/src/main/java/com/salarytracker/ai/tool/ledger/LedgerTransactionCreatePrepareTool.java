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
import com.salarytracker.ledger.LedgerModels.Category;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class LedgerTransactionCreatePrepareTool implements DomainTool {
    private final LedgerBookService books;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public LedgerTransactionCreatePrepareTool(LedgerBookService books, PendingActionService actions,
                                              CurrentUserResolver currentUser, ObjectMapper mapper) {
        this.books = books;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "kind", "只支持 EXPENSE 或 INCOME", null);
        ToolSchemas.numberProperty(schema, "amount", "金额，必须大于 0", 0);
        ToolSchemas.stringProperty(schema, "accountId", "账户公开 ID", null);
        ToolSchemas.stringProperty(schema, "categoryId", "有效二级分类公开 ID", null);
        ToolSchemas.stringProperty(schema, "occurredOn", "发生日期 yyyy-MM-dd，缺省为今天", "date");
        ToolSchemas.stringProperty(schema, "note", "备注", null);
        definition = new ToolDefinition("ledger.transaction.create.prepare", 1,
                "校验单笔收入或支出并生成确认预览；不写入账本数据。",
                ToolRisk.R2, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.optionalText(input, "bookId");
        String kind = ToolInputs.optionalText(input, "kind");
        if (kind != null && !Set.of("EXPENSE", "INCOME").contains(kind.toUpperCase())) {
            throw new IllegalArgumentException("kind 只支持 EXPENSE 或 INCOME");
        }
        if (kind != null) kind = kind.toUpperCase();
        final String requestedKind = kind;
        BigDecimal amount = decimal(input, "amount");
        String accountId = ToolInputs.optionalText(input, "accountId");
        String categoryId = ToolInputs.optionalText(input, "categoryId");
        String occurredOn = ToolInputs.optionalDate(input, "occurredOn");
        String note = ToolInputs.optionalText(input, "note");

        ObjectNode normalized = mapper.createObjectNode();
        put(normalized, "bookId", bookId); put(normalized, "kind", kind);
        if (amount == null) normalized.putNull("amount"); else normalized.put("amount", amount);
        put(normalized, "accountId", accountId); put(normalized, "categoryId", categoryId);
        put(normalized, "occurredOn", occurredOn); put(normalized, "note", note);

        ArrayNode missing = mapper.createArrayNode();
        if (bookId == null) missing.add("bookId");
        if (kind == null) missing.add("kind");
        if (amount == null) missing.add("amount");
        if (accountId == null) missing.add("accountId");
        if (categoryId == null) missing.add("categoryId");

        List<Account> accountOptions = bookId == null ? List.of() : books.accounts(bookId, false);
        List<Category> categories = bookId == null ? List.of() : books.categories(bookId, false);
        if (accountId != null && accountOptions.stream().noneMatch(item -> item.id().equals(accountId))) {
            throw new IllegalArgumentException("账户不存在或不可用");
        }
        Category category = categoryId == null ? null : categories.stream()
                .filter(item -> item.id().equals(categoryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("分类不存在或不可用"));
        if (category != null) {
            if (category.parentId() == null) throw new IllegalArgumentException("分类必须选择到二级");
            if (kind != null && !category.kind().name().equals(kind)) {
                throw new IllegalArgumentException("分类类型与流水类型不匹配");
            }
        }

        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, null,
                !missing.isEmpty(), Duration.ofMinutes(15));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.transaction.create");
        content.set("input", normalized);
        if (!missing.isEmpty()) {
            content.set("missingFields", missing);
            ArrayNode fields = content.putArray("fields");
            if (bookId == null) field(fields, "bookId", "账本 ID", "text", true, null);
            if (kind == null) field(fields, "kind", "收支类型", "select", true,
                    List.of(option("EXPENSE", "支出"), option("INCOME", "收入")));
            if (amount == null) field(fields, "amount", "金额", "money", true, null);
            if (accountId == null) field(fields, "accountId", "账户", "entity-picker", true,
                    accountOptions.stream().map(item -> option(item.id(), item.name())).toList());
            if (categoryId == null) field(fields, "categoryId", "二级分类", "entity-picker", true,
                    categories.stream().filter(item -> item.parentId() != null)
                            .filter(item -> requestedKind == null || item.kind().name().equals(requestedKind))
                            .map(item -> option(item.id(), item.name())).toList());
            field(fields, "occurredOn", "发生日期", "date", false, null);
            field(fields, "note", "备注", "textarea", false, null);
            return ToolResult.needsInput("请补充记账所需信息", content, action.id(), action.expiresAt().toString());
        }
        ObjectNode preview = content.putObject("preview");
        preview.put("bookId", bookId); preview.put("kind", kind); preview.put("amount", amount);
        preview.put("accountId", accountId); preview.put("accountName", name(accountOptions, accountId));
        preview.put("categoryId", categoryId); preview.put("categoryName", category.name());
        if (occurredOn != null) preview.put("occurredOn", occurredOn);
        if (note != null) preview.put("note", note);
        return ToolResult.needsConfirmation("请确认这笔" + ("INCOME".equals(kind) ? "收入" : "支出"),
                content, action.id(), action.expiresAt().toString());
    }

    private BigDecimal decimal(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) throw new IllegalArgumentException(field + " 必须是数字");
        BigDecimal number = value.decimalValue();
        if (number.signum() <= 0) throw new IllegalArgumentException(field + " 必须大于 0");
        return number;
    }

    private void put(ObjectNode target, String field, String value) {
        if (value == null) target.putNull(field); else target.put(field, value);
    }

    private void field(ArrayNode fields, String name, String label, String type, boolean required,
                       List<ObjectNode> options) {
        ObjectNode field = fields.addObject();
        field.put("name", name); field.put("label", label); field.put("type", type); field.put("required", required);
        if (options != null) field.set("options", mapper.valueToTree(options));
    }

    private ObjectNode option(String value, String label) {
        return mapper.createObjectNode().put("value", value).put("label", label);
    }

    private String name(List<Account> accounts, String id) {
        return accounts.stream().filter(item -> item.id().equals(id)).map(Account::name).findFirst().orElse(id);
    }
}
