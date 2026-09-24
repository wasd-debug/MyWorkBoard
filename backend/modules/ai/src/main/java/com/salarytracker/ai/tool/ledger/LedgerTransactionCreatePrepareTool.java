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
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
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
        ToolSchemas.stringProperty(schema, "accountName", "账户名称或简称", null);
        ToolSchemas.stringProperty(schema, "categoryId", "有效二级分类公开 ID", null);
        ToolSchemas.stringProperty(schema, "categoryName", "二级分类名称", null);
        ToolSchemas.stringProperty(schema, "merchantId", "商家公开 ID", null);
        ToolSchemas.stringProperty(schema, "merchantName", "商家或交易对方名称", null);
        ToolSchemas.stringProperty(schema, "memberId", "成员公开 ID", null);
        ToolSchemas.stringProperty(schema, "memberName", "成员名称", null);
        ToolSchemas.stringProperty(schema, "projectId", "项目公开 ID", null);
        ToolSchemas.stringProperty(schema, "projectName", "项目名称", null);
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
        BigDecimal amount = decimal(input, "amount");
        String accountId = ToolInputs.optionalText(input, "accountId");
        String accountName = ToolInputs.optionalText(input, "accountName");
        String categoryId = ToolInputs.optionalText(input, "categoryId");
        String categoryName = ToolInputs.optionalText(input, "categoryName");
        String merchantId = ToolInputs.optionalText(input, "merchantId");
        String merchantName = ToolInputs.optionalText(input, "merchantName");
        String memberId = ToolInputs.optionalText(input, "memberId");
        String memberName = ToolInputs.optionalText(input, "memberName");
        String projectId = ToolInputs.optionalText(input, "projectId");
        String projectName = ToolInputs.optionalText(input, "projectName");
        String occurredOn = ToolInputs.optionalDate(input, "occurredOn");
        if (occurredOn == null) occurredOn = LocalDate.now().toString();
        String note = ToolInputs.optionalText(input, "note");

        List<Account> accountOptions = bookId == null ? List.of() : books.accounts(bookId, false);
        List<Category> categories = bookId == null ? List.of() : books.categories(bookId, false);
        List<NamedResource> merchants = bookId == null ? List.of() : books.merchants(bookId, false);
        List<Member> members = bookId == null ? List.of() : books.members(bookId);
        List<NamedResource> projects = bookId == null ? List.of() : books.projects(bookId, false);

        ArrayNode suggested = mapper.createArrayNode();
        if (kind == null && amount != null) {
            kind = "EXPENSE";
            suggested.add("kind");
        }
        final String requestedKind = kind;
        List<Category> secondaryCategories = categories.stream().filter(item -> item.parentId() != null)
                .filter(item -> requestedKind == null || item.kind().name().equals(requestedKind)).toList();
        if (accountId == null && accountName != null) {
            List<Account> matches = accountOptions.stream().filter(item -> item.name().equalsIgnoreCase(accountName)
                    || item.name().toLowerCase().contains(accountName.toLowerCase())).toList();
            if (matches.size() == 1) { accountId = matches.get(0).id(); suggested.add("accountId"); }
        }
        if (categoryId == null && categoryName != null) {
            String normalizedCategoryName = compact(categoryName);
            List<Category> matches = secondaryCategories.stream().filter(item -> compact(item.name()).equals(normalizedCategoryName)
                    || compact(categoryLabel(item, categories)).equals(normalizedCategoryName)
                    || compact(categoryLabel(item, categories)).contains(normalizedCategoryName)).toList();
            if (matches.size() == 1) { categoryId = matches.get(0).id(); suggested.add("categoryId"); }
        }
        if (merchantId == null && merchantName != null) {
            merchantId = matchNamedId(merchants, merchantName);
            if (merchantId != null) suggested.add("merchantId");
        }
        if (memberId == null && memberName != null) {
            memberId = matchMemberId(members, memberName);
            if (memberId != null) suggested.add("memberId");
        }
        if (projectId == null && projectName != null) {
            projectId = matchNamedId(projects, projectName);
            if (projectId != null) suggested.add("projectId");
        }
        if (accountId == null && !accountOptions.isEmpty()) {
            accountId = accountOptions.get(0).id();
            suggested.add("accountId");
        }
        if (categoryId == null && !secondaryCategories.isEmpty()) {
            categoryId = secondaryCategories.get(0).id();
            suggested.add("categoryId");
        }

        ObjectNode normalized = mapper.createObjectNode();
        put(normalized, "bookId", bookId); put(normalized, "kind", kind);
        if (amount == null) normalized.putNull("amount"); else normalized.put("amount", amount);
        put(normalized, "accountId", accountId); put(normalized, "categoryId", categoryId);
        put(normalized, "accountName", accountName); put(normalized, "categoryName", categoryName);
        put(normalized, "merchantId", merchantId); put(normalized, "memberId", memberId);
        put(normalized, "projectId", projectId);
        put(normalized, "merchantName", merchantName); put(normalized, "memberName", memberName);
        put(normalized, "projectName", projectName);
        put(normalized, "occurredOn", occurredOn); put(normalized, "note", note);

        ArrayNode missing = mapper.createArrayNode();
        if (bookId == null) missing.add("bookId");
        if (kind == null) missing.add("kind");
        if (amount == null) missing.add("amount");
        if (accountId == null) missing.add("accountId");
        if (categoryId == null) missing.add("categoryId");

        final String normalizedAccountId = accountId;
        final String normalizedCategoryId = categoryId;
        if (normalizedAccountId != null && accountOptions.stream().noneMatch(item -> item.id().equals(normalizedAccountId))) {
            throw new IllegalArgumentException("账户不存在或不可用");
        }
        Category category = normalizedCategoryId == null ? null : categories.stream()
                .filter(item -> item.id().equals(normalizedCategoryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("分类不存在或不可用"));
        if (category != null) {
            if (category.parentId() == null) throw new IllegalArgumentException("分类必须选择到二级");
            if (kind != null && !category.kind().name().equals(kind)) {
                throw new IllegalArgumentException("分类类型与流水类型不匹配");
            }
        }
        validateNamed(merchants, merchantId, "商家");
        validateMember(members, memberId);
        validateNamed(projects, projectId, "项目");

        PendingAction action = actions.prepare(currentUser.id(), definition, normalized, null,
                !missing.isEmpty(), Duration.ofMinutes(15));
        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.transaction.create");
        content.set("input", normalized);
        content.set("suggestedFields", suggested);
        ArrayNode fields = content.putArray("fields");
        field(fields, "kind", "收支类型", "select", true,
                List.of(option("EXPENSE", "支出"), option("INCOME", "收入")));
        field(fields, "amount", "金额", "money", true, null);
        field(fields, "occurredOn", "发生日期", "date", true, null);
        field(fields, "accountId", "账户", "entity-picker", true,
                accountOptions.stream().map(item -> option(item.id(), item.name())).toList());
        field(fields, "categoryId", "二级分类", "entity-picker", true,
                categories.stream().filter(item -> item.parentId() != null)
                        .map(item -> categoryOption(item, categories)).toList());
        field(fields, "merchantId", "商家 / 对方", "entity-picker", false,
                merchants.stream().map(item -> option(item.id(), item.name())).toList());
        field(fields, "memberId", "成员", "entity-picker", false,
                members.stream().map(item -> option(item.id(), item.displayName())).toList());
        field(fields, "projectId", "项目", "entity-picker", false,
                projects.stream().map(item -> option(item.id(), item.name())).toList());
        field(fields, "note", "备注", "textarea", false, null);
        if (!missing.isEmpty()) {
            content.set("missingFields", missing);
            if (bookId == null) field(fields, "bookId", "账本 ID", "text", true, null);
            return ToolResult.needsInput("请补充记账所需信息", content, action.id(), action.expiresAt().toString());
        }
        ObjectNode preview = content.putObject("preview");
        preview.put("bookId", bookId); preview.put("kind", kind); preview.put("amount", amount);
        preview.put("accountId", accountId); preview.put("accountName", name(accountOptions, accountId));
        preview.put("categoryId", categoryId); preview.put("categoryName", category.name());
        preview.put("categoryPath", categoryLabel(category, categories));
        preview.put("merchantId", merchantId); preview.put("merchantName", namedName(merchants, merchantId));
        preview.put("memberId", memberId); preview.put("memberName", memberName(members, memberId));
        preview.put("projectId", projectId); preview.put("projectName", namedName(projects, projectId));
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

    private ObjectNode categoryOption(Category category, List<Category> categories) {
        return option(category.id(), categoryLabel(category, categories)).put("kind", category.kind().name());
    }

    private String name(List<Account> accounts, String id) {
        return accounts.stream().filter(item -> item.id().equals(id)).map(Account::name).findFirst().orElse(id);
    }

    private String categoryLabel(Category category, List<Category> categories) {
        String parent = categories.stream().filter(item -> item.id().equals(category.parentId()))
                .map(Category::name).findFirst().orElse(null);
        return parent == null ? category.name() : parent + " / " + category.name();
    }

    private void validateNamed(List<NamedResource> options, String id, String label) {
        if (id != null && options.stream().noneMatch(item -> item.id().equals(id))) {
            throw new IllegalArgumentException(label + "不存在或不可用");
        }
    }

    private void validateMember(List<Member> options, String id) {
        if (id != null && options.stream().noneMatch(item -> item.id().equals(id))) {
            throw new IllegalArgumentException("成员不存在或不可用");
        }
    }

    private String namedName(List<NamedResource> options, String id) {
        if (id == null) return null;
        return options.stream().filter(item -> item.id().equals(id)).map(NamedResource::name).findFirst().orElse(id);
    }

    private String memberName(List<Member> options, String id) {
        if (id == null) return null;
        return options.stream().filter(item -> item.id().equals(id)).map(Member::displayName).findFirst().orElse(id);
    }

    private String matchNamedId(List<NamedResource> options, String name) {
        String normalized = compact(name);
        List<NamedResource> exact = options.stream()
                .filter(item -> item.name().equalsIgnoreCase(name)).toList();
        if (exact.size() == 1) return exact.get(0).id();
        List<NamedResource> partial = options.stream()
                .filter(item -> compact(item.name()).contains(normalized)
                        || normalized.contains(compact(item.name()))).toList();
        return partial.size() == 1 ? partial.get(0).id() : null;
    }

    private String matchMemberId(List<Member> options, String name) {
        String normalized = compact(name);
        List<Member> matches = options.stream().filter(item -> item.displayName().equalsIgnoreCase(name)
                || item.username().equalsIgnoreCase(name) || (item.nickname() != null && item.nickname().equalsIgnoreCase(name))
                || compact(item.displayName()).contains(normalized)).toList();
        return matches.size() == 1 ? matches.get(0).id() : null;
    }

    private String compact(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[\\s/／_-]+", "");
    }
}
