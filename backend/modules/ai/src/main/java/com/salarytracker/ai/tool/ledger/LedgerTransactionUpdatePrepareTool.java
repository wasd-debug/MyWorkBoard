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
import com.salarytracker.ledger.LedgerModels.Member;
import com.salarytracker.ledger.LedgerModels.NamedResource;
import com.salarytracker.ledger.LedgerModels.Transaction;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import com.salarytracker.ledger.LedgerTransactionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class LedgerTransactionUpdatePrepareTool implements DomainTool {
    private final LedgerTransactionService transactions; private final LedgerBookService books;
    private final PendingActionService actions; private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper; private final ToolDefinition definition;

    public LedgerTransactionUpdatePrepareTool(LedgerTransactionService transactions, LedgerBookService books,
                                              PendingActionService actions, CurrentUserResolver currentUser,
                                              ObjectMapper mapper) {
        this.transactions = transactions; this.books = books; this.actions = actions; this.currentUser = currentUser; this.mapper = mapper;
        ObjectNode schema = ToolSchemas.object(mapper);
        ToolSchemas.stringProperty(schema, "bookId", "账本公开 ID", null);
        ToolSchemas.stringProperty(schema, "transactionId", "要修改的流水公开 ID", null);
        ToolSchemas.stringProperty(schema, "kind", "修改后的类型，只支持 EXPENSE 或 INCOME", null);
        ToolSchemas.numberProperty(schema, "amount", "修改后的金额", 0);
        ToolSchemas.stringProperty(schema, "accountId", "修改后的账户公开 ID", null);
        ToolSchemas.stringProperty(schema, "categoryId", "修改后的有效二级分类公开 ID", null);
        ToolSchemas.stringProperty(schema, "merchantId", "修改后的商家公开 ID", null);
        ToolSchemas.stringProperty(schema, "memberId", "修改后的成员公开 ID", null);
        ToolSchemas.stringProperty(schema, "projectId", "修改后的项目公开 ID", null);
        ToolSchemas.stringProperty(schema, "occurredOn", "修改后的发生日期 yyyy-MM-dd", "date");
        ToolSchemas.stringProperty(schema, "note", "修改后的备注", null);
        ToolSchemas.required(schema, "bookId", "transactionId");
        definition = new ToolDefinition("ledger.transaction.update.prepare", 1,
                "读取一笔收入或支出，校验修改字段并生成完整编辑卡片与差异预览；不写入数据。",
                ToolRisk.R3, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public ToolResult execute(JsonNode input) {
        String bookId = ToolInputs.requiredText(input, "bookId");
        String transactionId = ToolInputs.requiredText(input, "transactionId");
        Transaction original = transactions.transaction(bookId, transactionId);
        if (!Set.of(TransactionKind.EXPENSE, TransactionKind.INCOME).contains(original.kind())) {
            throw new IllegalArgumentException("首版只支持修改收入或支出流水");
        }
        List<Account> accountOptions = books.accounts(bookId, false);
        List<Category> categories = books.categories(bookId, false);
        List<NamedResource> merchants = books.merchants(bookId, false);
        List<Member> members = books.members(bookId);
        List<NamedResource> projects = books.projects(bookId, false);

        String kind = input.hasNonNull("kind") ? ToolInputs.requiredText(input, "kind").toUpperCase() : original.kind().name();
        if (!Set.of("EXPENSE", "INCOME").contains(kind)) throw new IllegalArgumentException("kind 只支持 EXPENSE 或 INCOME");
        BigDecimal amount = input.hasNonNull("amount") ? positive(input.path("amount")) : original.amount();
        String accountId = value(input, "accountId", original.accountId());
        String categoryId = value(input, "categoryId", original.categoryId());
        String merchantId = value(input, "merchantId", original.merchantId());
        String memberId = value(input, "memberId", original.memberId());
        String projectId = value(input, "projectId", original.projectId());
        String occurredOn = input.hasNonNull("occurredOn") ? ToolInputs.optionalDate(input, "occurredOn") : original.occurredOn().toString();
        String note = input.has("note") && !input.path("note").isNull() ? input.path("note").asText() : original.note();
        require(accountOptions.stream().anyMatch(item -> item.id().equals(accountId)), "账户不存在或不可用");
        Category category = categories.stream().filter(item -> item.id().equals(categoryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("分类不存在或不可用"));
        require(category.parentId() != null, "分类必须选择到二级");
        require(category.kind().name().equals(kind), "分类类型与流水类型不匹配");
        validate(merchants, merchantId, "商家"); validateMembers(members, memberId); validate(projects, projectId, "项目");

        ObjectNode normalized = mapper.createObjectNode();
        normalized.put("bookId", bookId); normalized.put("transactionId", transactionId); normalized.put("kind", kind);
        normalized.put("amount", amount); normalized.put("accountId", accountId); normalized.put("categoryId", categoryId);
        nullable(normalized, "merchantId", merchantId); nullable(normalized, "memberId", memberId); nullable(normalized, "projectId", projectId);
        normalized.put("occurredOn", occurredOn); normalized.put("note", note == null ? "" : note);
        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, original.revision(), false, Duration.ofMinutes(15));

        ObjectNode content = mapper.createObjectNode(); content.put("actionType", "ledger.transaction.update");
        content.set("input", normalized); content.set("original", transactionPreview(original));
        ObjectNode preview = content.putObject("preview");
        preview.put("transactionId", transactionId); preview.put("kind", kind); preview.put("amount", amount);
        preview.put("accountId", accountId); preview.put("accountName", accountOptions.stream().filter(v -> v.id().equals(accountId)).findFirst().map(Account::name).orElse(accountId));
        preview.put("categoryId", categoryId); preview.put("categoryName", category.name()); preview.put("categoryPath", categoryLabel(category, categories));
        nullable(preview, "merchantId", merchantId); nullable(preview, "merchantName", namedName(merchants, merchantId));
        nullable(preview, "memberId", memberId); nullable(preview, "memberName", memberName(members, memberId));
        nullable(preview, "projectId", projectId); nullable(preview, "projectName", namedName(projects, projectId));
        preview.put("occurredOn", occurredOn); preview.put("note", note == null ? "" : note); preview.put("revision", original.revision());
        ArrayNode fields = content.putArray("fields");
        field(fields, "kind", "收支类型", "select", true, List.of(option("EXPENSE", "支出"), option("INCOME", "收入")));
        field(fields, "amount", "金额", "money", true, null); field(fields, "occurredOn", "发生日期", "date", true, null);
        field(fields, "accountId", "账户", "entity-picker", true, accountOptions.stream().map(v -> option(v.id(), v.name())).toList());
        field(fields, "categoryId", "二级分类", "entity-picker", true, categories.stream().filter(v -> v.parentId() != null).map(v -> categoryOption(v, categories)).toList());
        field(fields, "merchantId", "商家 / 对方", "entity-picker", false, merchants.stream().map(v -> option(v.id(), v.name())).toList());
        field(fields, "memberId", "成员", "entity-picker", false, members.stream().map(v -> option(v.id(), v.displayName())).toList());
        field(fields, "projectId", "项目", "entity-picker", false, projects.stream().map(v -> option(v.id(), v.name())).toList());
        field(fields, "note", "备注", "textarea", false, null);
        content.set("diff", diff(content.path("original"), preview));
        return ToolResult.needsConfirmation("请确认修改这笔流水", content, action.id(), action.expiresAt().toString());
    }

    private ObjectNode transactionPreview(Transaction value) {
        ObjectNode node = mapper.createObjectNode(); node.put("transactionId", value.id()); node.put("kind", value.kind().name());
        node.put("amount", value.amount()); node.put("accountId", value.accountId()); node.put("accountName", value.accountName());
        nullable(node, "categoryId", value.categoryId()); nullable(node, "categoryName", value.categoryName());
        String path = value.parentCategoryName() == null ? value.categoryName() : value.parentCategoryName() + " / " + value.categoryName();
        nullable(node, "categoryPath", path); nullable(node, "merchantId", value.merchantId()); nullable(node, "merchantName", value.merchantName());
        nullable(node, "memberId", value.memberId()); nullable(node, "memberName", value.member()); nullable(node, "projectId", value.projectId()); nullable(node, "projectName", value.projectName());
        node.put("occurredOn", value.occurredOn().toString()); node.put("note", value.note() == null ? "" : value.note()); node.put("revision", value.revision()); return node;
    }

    private ArrayNode diff(JsonNode before, JsonNode after) {
        ArrayNode rows = mapper.createArrayNode();
        change(rows, "收支类型", labelKind(before.path("kind").asText()), labelKind(after.path("kind").asText()));
        change(rows, "金额", money(before.path("amount").decimalValue()), money(after.path("amount").decimalValue()));
        change(rows, "账户", before.path("accountName").asText(), after.path("accountName").asText());
        change(rows, "二级分类", before.path("categoryPath").asText(), after.path("categoryPath").asText());
        change(rows, "商家 / 对方", before.path("merchantName").asText(), after.path("merchantName").asText());
        change(rows, "成员", before.path("memberName").asText(), after.path("memberName").asText());
        change(rows, "项目", before.path("projectName").asText(), after.path("projectName").asText());
        change(rows, "日期", before.path("occurredOn").asText(), after.path("occurredOn").asText());
        change(rows, "备注", before.path("note").asText(), after.path("note").asText()); return rows;
    }

    private void change(ArrayNode rows, String label, String before, String after) { if (!Objects.equals(before, after)) rows.addObject().put("label", label).put("before", before).put("after", after); }
    private String labelKind(String value) { return "INCOME".equals(value) ? "收入" : "支出"; }
    private String money(BigDecimal value) { return "¥" + value.stripTrailingZeros().toPlainString(); }
    private String value(JsonNode input, String field, String fallback) { return input.has(field) ? (input.path(field).isNull() ? null : input.path(field).asText()) : fallback; }
    private BigDecimal positive(JsonNode value) { if (!value.isNumber() || value.decimalValue().signum() <= 0) throw new IllegalArgumentException("amount 必须大于 0"); return value.decimalValue(); }
    private void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
    private void validate(List<NamedResource> options, String id, String label) { if (id != null) require(options.stream().anyMatch(v -> v.id().equals(id)), label + "不存在或不可用"); }
    private void validateMembers(List<Member> options, String id) { if (id != null) require(options.stream().anyMatch(v -> v.id().equals(id)), "成员不存在或不可用"); }
    private String namedName(List<NamedResource> options, String id) { return id == null ? null : options.stream().filter(v -> v.id().equals(id)).findFirst().map(NamedResource::name).orElse(id); }
    private String memberName(List<Member> options, String id) { return id == null ? null : options.stream().filter(v -> v.id().equals(id)).findFirst().map(Member::displayName).orElse(id); }
    private String categoryLabel(Category category, List<Category> categories) { String parent = categories.stream().filter(v -> v.id().equals(category.parentId())).findFirst().map(Category::name).orElse(null); return parent == null ? category.name() : parent + " / " + category.name(); }
    private void nullable(ObjectNode node, String field, String value) { if (value == null || value.isBlank()) node.putNull(field); else node.put(field, value); }
    private void field(ArrayNode fields, String name, String label, String type, boolean required, List<ObjectNode> options) { ObjectNode field = fields.addObject().put("name", name).put("label", label).put("type", type).put("required", required); if (options != null) field.set("options", mapper.valueToTree(options)); }
    private ObjectNode option(String value, String label) { return mapper.createObjectNode().put("value", value).put("label", label); }
    private ObjectNode categoryOption(Category value, List<Category> categories) { return option(value.id(), categoryLabel(value, categories)).put("kind", value.kind().name()); }
}
