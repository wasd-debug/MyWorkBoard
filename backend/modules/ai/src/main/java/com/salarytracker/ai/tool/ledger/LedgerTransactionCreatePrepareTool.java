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
import com.salarytracker.ledger.LedgerModels.Member;
import com.salarytracker.ledger.LedgerModels.NamedResource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class LedgerTransactionCreatePrepareTool implements DomainTool {
    static final Set<String> SUPPORTED_KINDS = Set.of(
            "EXPENSE", "INCOME", "TRANSFER", "BORROW_IN", "LEND_OUT", "COLLECT_DEBT", "REPAY_DEBT");
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
        ToolSchemas.stringProperty(schema, "bookName", "账本名称", null);
        ToolSchemas.stringProperty(schema, "kind",
                "流水类型：EXPENSE、INCOME、TRANSFER、BORROW_IN、LEND_OUT、COLLECT_DEBT 或 REPAY_DEBT", null);
        ToolSchemas.numberProperty(schema, "amount", "金额，必须大于 0", 0);
        ToolSchemas.stringProperty(schema, "accountId", "账户公开 ID", null);
        ToolSchemas.stringProperty(schema, "accountName", "账户名称或简称", null);
        ToolSchemas.stringProperty(schema, "targetAccountId", "转账的转入账户公开 ID", null);
        ToolSchemas.stringProperty(schema, "targetAccountName", "转账的转入账户名称或简称", null);
        ToolSchemas.stringProperty(schema, "categoryId", "有效二级分类公开 ID", null);
        ToolSchemas.stringProperty(schema, "categoryName", "当前账本已有二级分类名称或一级 / 二级路径；先查 ledger.category.list，禁止创造名称", null);
        ToolSchemas.stringProperty(schema, "merchantId", "商家公开 ID", null);
        ToolSchemas.stringProperty(schema, "merchantName", "当前账本已有商家或交易对方名称；先查 ledger.merchant.list，未提及则留空，禁止创造名称", null);
        ToolSchemas.stringProperty(schema, "memberId", "成员公开 ID", null);
        ToolSchemas.stringProperty(schema, "memberName", "成员名称", null);
        ToolSchemas.stringProperty(schema, "projectId", "项目公开 ID", null);
        ToolSchemas.stringProperty(schema, "projectName", "项目名称", null);
        ToolSchemas.stringProperty(schema, "occurredOn", "发生日期 yyyy-MM-dd，缺省为今天", "date");
        ToolSchemas.stringProperty(schema, "note", "备注", null);
        definition = new ToolDefinition("ledger.transaction.create.prepare", 2,
                "校验单笔收入、支出、转账或借贷流水并生成确认预览；分类和商家只能匹配当前账本已有资源，不创建资源，不写入账本数据。",
                ToolRisk.R2, Set.of("ledger:write"), schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input) {
        ResolvedDraft resolved = resolve(input);
        PendingAction action = actions.prepare(currentUser.id(), definition, resolved.normalized(), null,
                !resolved.complete(), Duration.ofMinutes(15));
        if (!resolved.complete()) {
            return ToolResult.needsInput(resolved.summary(), resolved.content(),
                    action.id(), action.expiresAt().toString());
        }
        return ToolResult.needsConfirmation(resolved.summary(), resolved.content(),
                action.id(), action.expiresAt().toString());
    }

    ResolvedDraft resolve(JsonNode input) {
        String bookId = ToolInputs.optionalText(input, "bookId");
        String bookName = ToolInputs.optionalText(input, "bookName");
        String kind = ToolInputs.optionalText(input, "kind");
        if (kind != null && !SUPPORTED_KINDS.contains(kind.toUpperCase())) {
            throw new IllegalArgumentException("不支持的流水类型: " + kind);
        }
        if (kind != null) kind = kind.toUpperCase();
        BigDecimal amount = decimal(input, "amount");
        String accountId = ToolInputs.optionalText(input, "accountId");
        String accountName = ToolInputs.optionalText(input, "accountName");
        String targetAccountId = ToolInputs.optionalText(input, "targetAccountId");
        String targetAccountName = ToolInputs.optionalText(input, "targetAccountName");
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

        List<Book> bookOptions = books.books();
        Match<Book> bookMatch = matchBooks(bookOptions, bookId, bookName);
        if (bookId == null && bookMatch.selected() != null) bookId = bookMatch.selected().id();
        List<Account> accountOptions = bookId == null ? List.of() : books.accounts(bookId, false);
        List<Category> categories = bookId == null ? List.of() : books.categories(bookId, false);
        List<NamedResource> merchants = bookId == null ? List.of() : books.merchants(bookId, false);
        List<Member> members = bookId == null ? List.of() : books.members(bookId);
        List<NamedResource> projects = bookId == null ? List.of() : books.projects(bookId, false);

        ArrayNode suggested = mapper.createArrayNode();
        ObjectNode entityMatches = mapper.createObjectNode();
        ArrayNode ambiguous = mapper.createArrayNode();
        ArrayNode resolutionRequired = mapper.createArrayNode();
        writeMatch(entityMatches, ambiguous, resolutionRequired, "bookId", bookName, bookMatch,
                bookOptions.stream().map(item -> option(item.id(), item.name())).toList(), true);
        if (bookId != null && input.path("bookId").isMissingNode() && !input.hasNonNull("bookId")) suggested.add("bookId");
        if (kind == null && amount != null) {
            kind = "EXPENSE";
            suggested.add("kind");
        }
        final String requestedKind = kind;
        boolean transfer = "TRANSFER".equals(requestedKind);
        boolean requiresCategory = Set.of("EXPENSE", "INCOME").contains(requestedKind);
        List<Category> secondaryCategories = categories.stream().filter(item -> item.parentId() != null)
                .filter(item -> requestedKind == null || requiresCategory && item.kind().name().equals(requestedKind)).toList();
        Match<Account> accountMatch = matchAccounts(accountOptions, accountId, accountName);
        if (accountId == null && accountMatch.selected() != null) { accountId = accountMatch.selected().id(); suggested.add("accountId"); }
        final String selectedAccountId = accountId;
        List<Account> targetAccountOptions = selectedAccountId == null ? accountOptions : accountOptions.stream()
                .filter(item -> !item.id().equals(selectedAccountId)).toList();
        Match<Account> targetAccountMatch = transfer
                ? matchAccounts(targetAccountOptions, targetAccountId, targetAccountName)
                : new Match<>("missing", null, List.of());
        if (transfer && targetAccountId == null && targetAccountMatch.selected() != null) {
            targetAccountId = targetAccountMatch.selected().id();
            suggested.add("targetAccountId");
        }
        Match<Category> categoryMatch = requiresCategory
                ? matchCategories(secondaryCategories, categories, categoryId, categoryName)
                : new Match<>("missing", null, List.of());
        if (categoryId == null && categoryMatch.selected() != null) { categoryId = categoryMatch.selected().id(); suggested.add("categoryId"); }
        if (!requiresCategory) {
            categoryId = null;
            categoryName = null;
        }
        Match<NamedResource> merchantMatch = matchNamed(merchants, merchantId, merchantName);
        boolean merchantRequested = merchantId != null || merchantName != null;
        if (merchantId == null && merchantMatch.selected() != null) { merchantId = merchantMatch.selected().id(); suggested.add("merchantId"); }
        Match<Member> memberMatch = matchMembers(members, memberId, memberName);
        if (memberId == null && memberMatch.selected() != null) { memberId = memberMatch.selected().id(); suggested.add("memberId"); }
        if (memberId == null && memberName == null) {
            long currentUserId = currentUser.id();
            List<Member> currentMembers = members.stream()
                    .filter(item -> item.userId() == currentUserId).toList();
            if (currentMembers.size() == 1) {
                memberId = currentMembers.get(0).id();
                suggested.add("memberId");
                memberMatch = new Match<>("suggested", currentMembers.get(0), currentMembers);
            }
        }
        Match<NamedResource> projectMatch = matchNamed(projects, projectId, projectName);
        if (projectId == null && projectMatch.selected() != null) { projectId = projectMatch.selected().id(); suggested.add("projectId"); }
        writeMatch(entityMatches, ambiguous, resolutionRequired, "accountId", accountName, accountMatch,
                accountOptions.stream().map(this::accountOption).toList(), true);
        if (transfer) {
            writeMatch(entityMatches, ambiguous, resolutionRequired, "targetAccountId", targetAccountName,
                    targetAccountMatch, targetAccountOptions.stream().map(this::accountOption).toList(), true);
        }
        if (requiresCategory) {
            writeMatch(entityMatches, ambiguous, resolutionRequired, "categoryId", categoryName, categoryMatch,
                    secondaryCategories.stream().map(item -> categoryOption(item, categories)).toList(), true);
        }
        writeMatch(entityMatches, ambiguous, resolutionRequired, "merchantId", merchantName, merchantMatch,
                merchants.stream().map(item -> namedOption(item, "商家")).toList(),
                merchantRequested);
        writeMatch(entityMatches, ambiguous, resolutionRequired, "memberId", memberName, memberMatch,
                members.stream().map(this::memberOption).toList(), "ambiguous".equals(memberMatch.status()));
        writeMatch(entityMatches, ambiguous, resolutionRequired, "projectId", projectName, projectMatch,
                projects.stream().map(item -> namedOption(item, "项目")).toList(),
                "ambiguous".equals(projectMatch.status()));

        ObjectNode normalized = mapper.createObjectNode();
        put(normalized, "bookId", bookId); put(normalized, "bookName", bookName); put(normalized, "kind", kind);
        if (amount == null) normalized.putNull("amount"); else normalized.put("amount", amount);
        put(normalized, "accountId", accountId); put(normalized, "categoryId", categoryId);
        put(normalized, "targetAccountId", targetAccountId);
        put(normalized, "accountName", accountName); put(normalized, "categoryName", categoryName);
        put(normalized, "targetAccountName", targetAccountName);
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
        if (transfer && targetAccountId == null) missing.add("targetAccountId");
        if (requiresCategory && categoryId == null) missing.add("categoryId");
        addResolutionMissing(missing, resolutionRequired, "merchantId");
        addResolutionMissing(missing, resolutionRequired, "memberId");
        addResolutionMissing(missing, resolutionRequired, "projectId");

        final String normalizedAccountId = accountId;
        final String normalizedCategoryId = categoryId;
        if (normalizedAccountId != null && accountOptions.stream().noneMatch(item -> item.id().equals(normalizedAccountId))) {
            throw new IllegalArgumentException("账户不存在或不可用");
        }
        final String normalizedTargetAccountId = targetAccountId;
        if (normalizedTargetAccountId != null
                && accountOptions.stream().noneMatch(item -> item.id().equals(normalizedTargetAccountId))) {
            throw new IllegalArgumentException("转入账户不存在或不可用");
        }
        if (normalizedAccountId != null && normalizedAccountId.equals(normalizedTargetAccountId)) {
            throw new IllegalArgumentException("转出账户与转入账户不能相同");
        }
        Category category = normalizedCategoryId == null ? null : categories.stream()
                .filter(item -> item.id().equals(normalizedCategoryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("分类不存在或不可用"));
        if (category != null) {
            if (category.parentId() == null) throw new IllegalArgumentException("分类必须选择到二级");
            if (kind != null && requiresCategory && !category.kind().name().equals(kind)) {
                throw new IllegalArgumentException("分类类型与流水类型不匹配");
            }
        }
        validateNamed(merchants, merchantId, "商家");
        validateMember(members, memberId);
        validateNamed(projects, projectId, "项目");

        ObjectNode content = mapper.createObjectNode();
        content.put("actionType", "ledger.transaction.create");
        content.set("input", normalized);
        content.set("suggestedFields", suggested);
        content.set("entityMatches", entityMatches);
        content.set("ambiguousFields", ambiguous);
        content.set("resolutionRequiredFields", resolutionRequired);
        ArrayNode fields = content.putArray("fields");
        field(fields, "kind", "流水类型", "select", true, kindOptions());
        field(fields, "amount", "金额", "money", true, null);
        field(fields, "occurredOn", "发生日期", "date", true, null);
        if (bookId == null) field(fields, "bookId", "账本", "entity-picker", true,
                bookOptions.stream().map(item -> option(item.id(), item.name())).toList());
        if (bookId != null) {
            field(fields, "accountId", transfer ? "转出账户" : "账户", "entity-picker", true,
                    accountOptions.stream().map(this::accountOption).toList());
            if (transfer) {
                field(fields, "targetAccountId", "转入账户", "entity-picker", true,
                        targetAccountOptions.stream().map(this::accountOption).toList());
            }
            if (requiresCategory) {
                field(fields, "categoryId", "二级分类", "entity-picker", true,
                        categories.stream().filter(item -> item.parentId() != null)
                                .map(item -> categoryOption(item, categories)).toList());
            }
            field(fields, "merchantId", "商家 / 对方", "entity-picker",
                    contains(resolutionRequired, "merchantId"),
                    merchants.stream().map(item -> namedOption(item, "商家")).toList());
            field(fields, "memberId", "成员", "entity-picker",
                    contains(resolutionRequired, "memberId"), members.stream().map(this::memberOption).toList());
            field(fields, "projectId", "项目", "entity-picker",
                    contains(resolutionRequired, "projectId"),
                    projects.stream().map(item -> namedOption(item, "项目")).toList());
        }
        field(fields, "note", "备注", "textarea", false, null);
        if (!missing.isEmpty()) {
            content.set("missingFields", missing);
            String summary = ambiguous.isEmpty() ? "请补充记账所需信息" : "请选择存在歧义的记账信息";
            return new ResolvedDraft(normalized, content, false, summary);
        }
        ObjectNode preview = content.putObject("preview");
        preview.put("bookId", bookId); preview.put("kind", kind); preview.put("amount", amount);
        preview.put("accountId", accountId); preview.put("accountName", name(accountOptions, accountId));
        if (targetAccountId != null) {
            preview.put("targetAccountId", targetAccountId);
            preview.put("targetAccountName", name(accountOptions, targetAccountId));
        }
        if (category != null) {
            preview.put("categoryId", categoryId); preview.put("categoryName", category.name());
            preview.put("categoryPath", categoryLabel(category, categories));
        }
        preview.put("merchantId", merchantId); preview.put("merchantName", namedName(merchants, merchantId));
        preview.put("memberId", memberId); preview.put("memberName", memberName(members, memberId));
        preview.put("projectId", projectId); preview.put("projectName", namedName(projects, projectId));
        if (occurredOn != null) preview.put("occurredOn", occurredOn);
        if (note != null) preview.put("note", note);
        return new ResolvedDraft(normalized, content, true, "请确认这笔" + kindLabel(kind));
    }

    private List<ObjectNode> kindOptions() {
        return List.of(option("EXPENSE", "支出"), option("INCOME", "收入"),
                option("TRANSFER", "转账"), option("BORROW_IN", "借入"), option("LEND_OUT", "借出"),
                option("COLLECT_DEBT", "收债"), option("REPAY_DEBT", "还款"));
    }

    static String kindLabel(String kind) {
        return switch (kind) {
            case "INCOME" -> "收入";
            case "TRANSFER" -> "转账";
            case "BORROW_IN" -> "借入";
            case "LEND_OUT" -> "借出";
            case "COLLECT_DEBT" -> "收债";
            case "REPAY_DEBT" -> "还款";
            default -> "支出";
        };
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

    private ObjectNode accountOption(Account account) {
        String description = account.accountType() + " · " + account.currency();
        if (account.balance() != null) description += " · 余额 " + account.balance().stripTrailingZeros().toPlainString();
        return option(account.id(), account.name()).put("description", description);
    }

    private ObjectNode namedOption(NamedResource resource, String type) {
        ObjectNode option = option(resource.id(), resource.name());
        option.put("description", resource.note() == null || resource.note().isBlank() ? type : resource.note());
        return option;
    }

    private ObjectNode memberOption(Member member) {
        return option(member.id(), member.displayName())
                .put("description", member.username() + " · " + member.roleName());
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

    private Match<Book> matchBooks(List<Book> options, String id, String name) {
        return match(options, id, name, Book::id, Book::name);
    }

    private Match<Account> matchAccounts(List<Account> options, String id, String name) {
        return match(options, id, name, Account::id, Account::name);
    }

    private Match<Category> matchCategories(List<Category> options, List<Category> all, String id, String name) {
        if (id != null) return match(options, id, null, Category::id, Category::name);
        if (name == null) return options.size() == 1
                ? new Match<>("suggested", options.get(0), options) : new Match<>(options.isEmpty() ? "missing" : "ambiguous", null, options);
        String normalized = compact(name);
        List<Category> exact = options.stream().filter(item -> compact(item.name()).equals(normalized)
                || compact(categoryLabel(item, all)).equals(normalized)).toList();
        if (exact.size() == 1) return new Match<>("exact", exact.get(0), exact);
        List<Category> candidates = exact.isEmpty() ? options.stream().filter(item -> compact(item.name()).contains(normalized)
                || compact(categoryLabel(item, all)).contains(normalized)
                || normalized.contains(compact(item.name()))).toList() : exact;
        return candidates.size() == 1 ? new Match<>("suggested", candidates.get(0), candidates)
                : new Match<>(candidates.isEmpty() ? "missing" : "ambiguous", null, candidates);
    }

    private Match<NamedResource> matchNamed(List<NamedResource> options, String id, String name) {
        if (id == null && name == null) return new Match<>("missing", null, List.of());
        return match(options, id, name, NamedResource::id, NamedResource::name);
    }

    private Match<Member> matchMembers(List<Member> options, String id, String name) {
        if (id != null) return match(options, id, null, Member::id, Member::displayName);
        if (name == null) return new Match<>("missing", null, List.of());
        String normalized = compact(name);
        List<Member> exact = options.stream().filter(item -> compact(item.displayName()).equals(normalized)
                || compact(item.username()).equals(normalized) || compact(item.nickname()).equals(normalized)).toList();
        if (exact.size() == 1) return new Match<>("exact", exact.get(0), exact);
        List<Member> candidates = exact.isEmpty() ? options.stream().filter(item -> compact(item.displayName()).contains(normalized)
                || compact(item.username()).contains(normalized) || compact(item.nickname()).contains(normalized)).toList() : exact;
        return candidates.size() == 1 ? new Match<>("suggested", candidates.get(0), candidates)
                : new Match<>(candidates.isEmpty() ? "missing" : "ambiguous", null, candidates);
    }

    private <T> Match<T> match(List<T> options, String id, String name,
                               java.util.function.Function<T, String> idOf,
                               java.util.function.Function<T, String> labelOf) {
        if (id != null) {
            T selected = options.stream().filter(item -> idOf.apply(item).equals(id)).findFirst().orElse(null);
            return new Match<>(selected == null ? "missing" : "exact", selected, selected == null ? List.of() : List.of(selected));
        }
        if (name == null) return options.size() == 1
                ? new Match<>("suggested", options.get(0), options)
                : new Match<>(options.isEmpty() ? "missing" : "ambiguous", null, options);
        String normalized = compact(name);
        List<T> exact = options.stream().filter(item -> compact(labelOf.apply(item)).equals(normalized)).toList();
        if (exact.size() == 1) return new Match<>("exact", exact.get(0), exact);
        List<T> candidates = exact.isEmpty() ? options.stream().filter(item -> compact(labelOf.apply(item)).contains(normalized)
                || normalized.contains(compact(labelOf.apply(item)))).toList() : exact;
        return candidates.size() == 1 ? new Match<>("suggested", candidates.get(0), candidates)
                : new Match<>(candidates.isEmpty() ? "missing" : "ambiguous", null, candidates);
    }

    private <T> void writeMatch(ObjectNode matches, ArrayNode ambiguous, ArrayNode resolutionRequired,
                                String field, String query, Match<T> match, List<ObjectNode> allOptions,
                                boolean required) {
        ObjectNode node = matches.putObject(field);
        node.put("status", match.status());
        if (query != null) node.put("query", query);
        ArrayNode candidates = node.putArray("candidates");
        Set<String> candidateIds = match.candidates().stream().map(item -> {
            if (item instanceof Book value) return value.id();
            if (item instanceof Account value) return value.id();
            if (item instanceof Category value) return value.id();
            if (item instanceof NamedResource value) return value.id();
            if (item instanceof Member value) return value.id();
            return "";
        }).collect(java.util.stream.Collectors.toSet());
        if ("missing".equals(match.status()) && query != null) {
            // A supplied but unknown name must return the existing directory as the only source
            // for correction; never pass the model's free-text guess to the commit path.
            allOptions.forEach(candidates::add);
        } else {
            allOptions.stream().filter(option -> candidateIds.contains(option.path("value").asText())).forEach(candidates::add);
        }
        if ("ambiguous".equals(match.status())) ambiguous.add(field);
        if (required && match.selected() == null) resolutionRequired.add(field);
    }

    private void addResolutionMissing(ArrayNode missing, ArrayNode resolutionRequired, String field) {
        boolean unresolved = contains(resolutionRequired, field);
        boolean alreadyMissing = java.util.stream.StreamSupport.stream(missing.spliterator(), false)
                .anyMatch(item -> field.equals(item.asText()));
        if (unresolved && !alreadyMissing) missing.add(field);
    }

    private boolean contains(ArrayNode values, String value) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .anyMatch(item -> value.equals(item.asText()));
    }

    private record Match<T>(String status, T selected, List<T> candidates) { }

    record ResolvedDraft(ObjectNode normalized, ObjectNode content, boolean complete, String summary) { }

    private String compact(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[\\s/／_-]+", "");
    }
}
