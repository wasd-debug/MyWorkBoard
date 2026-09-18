package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ai.LlmGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.salarytracker.ledger.LedgerModels.*;

@Service
public class LedgerAiService {
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块)?");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LlmGateway gateway;
    private final LedgerBookAccess access;
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;

    public LedgerAiService(JdbcTemplate jdbc, ObjectMapper mapper, LlmGateway gateway,
                           LedgerBookAccess access, LedgerBookService books,
                           LedgerTransactionService transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.gateway = gateway;
        this.access = access;
        this.books = books;
        this.transactions = transactions;
    }

    public AiPreview previewText(String bookPublicId, String text) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        requireWrite(context);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("请输入记账内容");
        List<GatewayDraft> drafts = gateway.configured()
                ? parseGateway(gateway.structured(systemPrompt(context), text, null, null))
                : localDrafts(text);
        return savePreview(context, "TEXT", enrich(context, drafts), !gateway.configured());
    }

    public AiPreview previewImage(String bookPublicId, MultipartFile file) throws Exception {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        requireWrite(context);
        if (!gateway.configured()) throw new IllegalArgumentException("图片记账需要先配置多模态 AI 网关");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择票据或账单截图");
        if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("图片不能超过 10MB");
        String mediaType = file.getContentType();
        if (mediaType == null || !mediaType.startsWith("image/")) throw new IllegalArgumentException("文件必须是图片");
        String response = gateway.structured(systemPrompt(context),
                "识别图片里的每一笔交易。无法确定的字段留空，不要猜测。", file.getBytes(), mediaType);
        return savePreview(context, "IMAGE", enrich(context, parseGateway(response)), false);
    }

    @Transactional
    public AiConfirm confirm(String bookPublicId, String draftId,
                             AiConfirmCommand input, String idempotencyKey) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        requireWrite(context);
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT payload_json,status,idempotency_key FROM ledger_ai_draft " +
                        "WHERE id=? AND book_id=? AND user_id=? AND expires_at>CURRENT_TIMESTAMP",
                draftId, context.bookId(), context.userId());
        if (rows.isEmpty()) throw new IllegalArgumentException("AI 草稿不存在或已过期");
        AiStoredDraft stored = read(rows.get(0).get("payload_json"), AiStoredDraft.class);
        if ("CONFIRMED".equals(rows.get(0).get("status"))) {
            return new AiConfirm(draftId, "CONFIRMED", stored.created() == null ? List.of() : stored.created());
        }
        List<TransactionCommand> submitted = input == null || input.transactions() == null
                ? List.of() : input.transactions();
        if (submitted.isEmpty()) submitted = stored.drafts().stream().map(this::command).toList();
        if (submitted.isEmpty()) throw new IllegalArgumentException("至少保留一笔草稿");
        String key = text(idempotencyKey);
        if (key.isBlank()) key = "ai-" + draftId;
        List<Transaction> created = new ArrayList<>();
        for (int index = 0; index < submitted.size(); index++) {
            TransactionCommand draft = submitted.get(index);
            TransactionCommand confirmed = new TransactionCommand(draft.id(), draft.accountId(),
                    draft.targetAccountId(), draft.categoryId(), draft.merchantId(), draft.memberId(),
                    draft.projectId(), draft.kind(), draft.amount(), draft.currency(), draft.occurredOn(),
                    draft.payee(), draft.member(), draft.project(), draft.note(), "ai-confirmed",
                    draft.clientOpId(), draft.recurringId(), draft.revision());
            created.add(transactions.create(bookPublicId, confirmed, key + "-" + index));
        }
        AiStoredDraft confirmed = new AiStoredDraft(stored.drafts(), stored.localFallback(), created);
        jdbc.update("UPDATE ledger_ai_draft SET status='CONFIRMED',payload_json=?,idempotency_key=?," +
                        "confirmed_at=CURRENT_TIMESTAMP WHERE id=? AND status='PREVIEW'",
                json(confirmed), key, draftId);
        return new AiConfirm(draftId, "CONFIRMED", created);
    }

    private AiPreview savePreview(LedgerBookAccess.Context context, String sourceType,
                                  List<AiDraft> drafts, boolean localFallback) {
        if (drafts.isEmpty()) throw new IllegalArgumentException("没有识别到可编辑的交易草稿");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ledger_ai_draft(id,book_id,user_id,source_type,payload_json,expires_at) " +
                        "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP+INTERVAL 24 HOUR)",
                id, context.bookId(), context.userId(), sourceType,
                json(new AiStoredDraft(drafts, localFallback, List.of())));
        return new AiPreview(id, drafts, sourceType, true, localFallback);
    }

    private List<AiDraft> enrich(LedgerBookAccess.Context context, List<GatewayDraft> source) {
        List<Account> accounts = books.accounts(context.bookPublicId(), false);
        List<Category> categories = books.categories(context.bookPublicId(), false);
        List<NamedResource> merchants = books.merchants(context.bookPublicId(), false);
        List<NamedResource> projects = books.projects(context.bookPublicId(), false);
        List<Member> members = books.members(context.bookPublicId());
        Member currentMember = members.stream().filter(item -> item.userId() == context.userId()).findFirst().orElse(null);
        List<AiDraft> result = new ArrayList<>();
        for (GatewayDraft raw : source) {
            TransactionKind kind = normalizeKind(raw.kind());
            LocalDate date = validDate(raw.occurredOn());
            BigDecimal amount = positiveAmount(raw.amount());
            List<String> warnings = new ArrayList<>();
            Account account = matchAccount(raw.accountId(), raw.accountName(), accounts);
            if (account == null && (!text(raw.accountId()).isBlank() || !text(raw.accountName()).isBlank())) {
                warnings.add("账户“" + text(raw.accountName()) + "”未匹配");
            }
            Account target = kind == TransactionKind.TRANSFER
                    ? matchAccount(raw.targetAccountId(), raw.targetAccountName(), accounts) : null;
            if (kind == TransactionKind.TRANSFER && target == null) warnings.add("请选择转入账户");
            CategoryMatch category = matchCategory(raw, kind, categories, warnings);
            NamedResource merchant = matchNamed(raw.merchantId(), raw.merchantName(), merchants, warnings, "商家");
            NamedResource project = matchNamed(raw.projectId(), raw.projectName(), projects, warnings, "项目");
            Member member = matchMember(raw.memberId(), raw.member(), members);
            if (member == null) member = currentMember;
            if (member == null) warnings.add("请选择成员");
            if (account == null) warnings.add("请选择账户");
            result.add(new AiDraft(UUID.randomUUID().toString(), kind, amount, date,
                    account == null ? null : account.id(), account == null ? raw.accountName() : account.name(),
                    target == null ? null : target.id(), target == null ? raw.targetAccountName() : target.name(),
                    category.categoryId(), category.categoryName(), category.parentName(), category.status(),
                    merchant == null ? null : merchant.id(), merchant == null ? raw.merchantName() : merchant.name(),
                    member == null ? null : member.id(), member == null ? raw.member() : member.displayName(),
                    project == null ? null : project.id(), project == null ? raw.projectName() : project.name(),
                    raw.note(), warnings.stream().filter(value -> !value.isBlank()).distinct().toList()));
        }
        return result;
    }

    private CategoryMatch matchCategory(GatewayDraft raw, TransactionKind kind,
                                        List<Category> categories, List<String> warnings) {
        if (kind != TransactionKind.INCOME && kind != TransactionKind.EXPENSE) {
            return new CategoryMatch(null, raw.categoryName(), raw.parentCategoryName(), "not_required");
        }
        CategoryKind required = kind == TransactionKind.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        List<Category> secondaries = categories.stream()
                .filter(item -> item.parentId() != null && item.kind() == required).toList();
        Category byId = secondaries.stream().filter(item -> item.id().equals(raw.categoryId())).findFirst().orElse(null);
        if (byId != null) return categoryMatch(byId, categories, "matched");
        String[] reference = categoryReference(raw.categoryName(), raw.parentCategoryName());
        String parentName = reference[0];
        String childName = reference[1];
        if (childName.isBlank()) {
            warnings.add(parentName.isBlank() ? "缺少二级分类，请补充分类" : "分类“" + parentName + "”只有一级分类，请补充二级分类");
            return new CategoryMatch(null, childName, parentName, parentName.isBlank() ? "missing" : "primary_only");
        }
        List<Category> matches = secondaries.stream().filter(item -> item.name().equalsIgnoreCase(childName))
                .filter(item -> parentName.isBlank() || categories.stream()
                        .anyMatch(parent -> parent.id().equals(item.parentId()) && parent.name().equalsIgnoreCase(parentName)))
                .toList();
        if (matches.size() == 1) return categoryMatch(matches.get(0), categories, "matched");
        String status = matches.isEmpty() ? "unmatched" : "ambiguous";
        warnings.add(matches.isEmpty() ? "二级分类“" + childName + "”未匹配当前账本"
                : "二级分类“" + childName + "”存在多个匹配，请选择一级分类");
        return new CategoryMatch(null, childName, parentName, status);
    }

    private CategoryMatch categoryMatch(Category category, List<Category> all, String status) {
        String parent = all.stream().filter(item -> item.id().equals(category.parentId()))
                .map(Category::name).findFirst().orElse(null);
        return new CategoryMatch(category.id(), category.name(), parent, status);
    }

    private Account matchAccount(String id, String name, List<Account> options) {
        Account byId = options.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
        if (byId != null) return byId;
        List<Account> matches = options.stream().filter(item -> item.name().equalsIgnoreCase(text(name))).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private NamedResource matchNamed(String id, String name, List<NamedResource> options,
                                     List<String> warnings, String label) {
        NamedResource byId = options.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
        if (byId != null) return byId;
        if (text(name).isBlank()) return null;
        List<NamedResource> matches = options.stream().filter(item -> item.name().equalsIgnoreCase(name)).toList();
        if (matches.size() == 1) return matches.get(0);
        warnings.add(label + "“" + name + "”未匹配");
        return null;
    }

    private Member matchMember(String id, String name, List<Member> members) {
        Member byId = members.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
        if (byId != null) return byId;
        List<Member> matches = members.stream().filter(item -> item.username().equalsIgnoreCase(text(name))
                || item.displayName().equalsIgnoreCase(text(name))).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String systemPrompt(LedgerBookAccess.Context context) {
        List<Category> categories = books.categories(context.bookPublicId(), false);
        return """
                你是记账解析器。只返回 JSON 对象：{"transactions":[...]}。
                每笔字段：kind,amount,occurredOn,accountName,targetAccountName,categoryName,
                merchantName,member,projectName,note。kind 只能为 EXPENSE、INCOME、TRANSFER、
                BORROW_IN、LEND_OUT、COLLECT_DEBT、REPAY_DEBT。日期格式 yyyy-MM-dd。
                可以返回多笔，无法确定的字段使用空字符串。不得直接入账。
                当前日期：%s
                当前账户：%s
                当前分类（一级 / 二级）：%s
                当前商家：%s
                当前成员：%s
                当前项目：%s
                """.formatted(LocalDate.now(), names(books.accounts(context.bookPublicId(), false), Account::name),
                categoryPaths(categories), names(books.merchants(context.bookPublicId(), false), NamedResource::name),
                memberNames(books.members(context.bookPublicId())),
                names(books.projects(context.bookPublicId(), false), NamedResource::name));
    }

    private List<GatewayDraft> parseGateway(String response) {
        String json = text(response);
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first >= 0 && last > first) json = json.substring(first, last + 1);
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode items = root.get("transactions");
            if (items != null && items.isArray()) {
                return mapper.readerForListOf(GatewayDraft.class).readValue(items);
            }
            return root.has("amount") ? List.of(mapper.treeToValue(root, GatewayDraft.class)) : List.of();
        } catch (Exception exception) {
            throw new IllegalArgumentException("AI 返回的结构化内容无效");
        }
    }

    private List<GatewayDraft> localDrafts(String text) {
        List<GatewayDraft> result = new ArrayList<>();
        for (String part : text.split("[\\n；;]+")) {
            if (part.isBlank()) continue;
            Matcher matcher = AMOUNT.matcher(part);
            if (!matcher.find()) continue;
            result.add(new GatewayDraft(null, inferKind(part), matcher.group(1), inferDate(part), null,
                    null, null, null, null, null, null, null, null, null, null, null, null, part.trim()));
        }
        return result;
    }

    private TransactionCommand command(AiDraft draft) {
        return new TransactionCommand(null, draft.accountId(), draft.targetAccountId(), draft.categoryId(),
                draft.merchantId(), draft.memberId(), draft.projectId(), draft.kind(), draft.amount(), null,
                draft.occurredOn(), draft.merchantName(), draft.member(), draft.projectName(), draft.note(),
                "ai-confirmed", null, null, null);
    }

    static String[] categoryReference(String childName, String parentName) {
        String child = childName == null ? "" : childName.trim();
        String parent = parentName == null ? "" : parentName.trim();
        if (parent.isBlank()) {
            String[] parts = child.split("\\s*(?:/|／|>|＞|→)\\s*", 2);
            if (parts.length == 2) {
                parent = parts[0].trim();
                child = parts[1].trim();
            }
        }
        return new String[]{parent, child};
    }

    private String categoryPaths(List<Category> categories) {
        return categories.stream().filter(item -> item.parentId() != null).map(item -> {
            String parent = categories.stream().filter(candidate -> candidate.id().equals(item.parentId()))
                    .map(Category::name).findFirst().orElse("");
            return parent.isBlank() ? item.name() : parent + " / " + item.name();
        }).filter(value -> !value.isBlank()).toList().toString();
    }

    private <T> String names(List<T> values, Function<T, String> name) {
        return values.stream().map(name).map(this::text).filter(value -> !value.isBlank()).toList().toString();
    }

    private String memberNames(List<Member> values) {
        return values.stream().map(Member::displayName).filter(value -> !value.isBlank()).toList().toString();
    }

    private String inferKind(String text) {
        if (text.contains("收入") || text.contains("收到") || text.contains("工资")) return "INCOME";
        if (text.contains("转账")) return "TRANSFER";
        if (text.contains("借入")) return "BORROW_IN";
        if (text.contains("借出")) return "LEND_OUT";
        if (text.contains("收债")) return "COLLECT_DEBT";
        if (text.contains("还债") || text.contains("还款")) return "REPAY_DEBT";
        return "EXPENSE";
    }

    private String inferDate(String text) {
        if (text.contains("昨天")) return LocalDate.now().minusDays(1).toString();
        if (text.contains("前天")) return LocalDate.now().minusDays(2).toString();
        return LocalDate.now().toString();
    }

    private TransactionKind normalizeKind(Object value) {
        String kind = text(value).toUpperCase(Locale.ROOT);
        kind = switch (kind) {
            case "收入" -> "INCOME";
            case "转账" -> "TRANSFER";
            case "借入" -> "BORROW_IN";
            case "借出" -> "LEND_OUT";
            case "收债" -> "COLLECT_DEBT";
            case "还债" -> "REPAY_DEBT";
            case "INCOME", "TRANSFER", "BORROW_IN", "LEND_OUT", "COLLECT_DEBT", "REPAY_DEBT" -> kind;
            default -> "EXPENSE";
        };
        return TransactionKind.valueOf(kind);
    }

    private LocalDate validDate(Object value) {
        try {
            return LocalDate.parse(text(value));
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private BigDecimal positiveAmount(Object value) {
        try {
            return new BigDecimal(text(value)).abs().setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void requireWrite(LedgerBookAccess.Context context) {
        if (context.isOwner() || context.permissions().contains("TRANSACTION_OWN_WRITE")
                || context.permissions().contains("TRANSACTION_ANY_WRITE")) return;
        access.require(context, "TRANSACTION_OWN_WRITE");
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存 AI 草稿", exception);
        }
    }

    private <T> T read(Object value, Class<T> type) {
        try {
            return mapper.readValue(String.valueOf(value), type);
        } catch (Exception exception) {
            throw new IllegalArgumentException("AI 草稿数据无效", exception);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record CategoryMatch(String categoryId, String categoryName, String parentName, String status) { }

    private record GatewayResponse(List<GatewayDraft> transactions) { }

    private record GatewayDraft(String id, String kind, String amount, String occurredOn,
                                String accountId, String accountName, String targetAccountId,
                                String targetAccountName, String categoryId, String categoryName,
                                String parentCategoryName, String merchantId, String merchantName,
                                String memberId, String member, String projectId, String projectName,
                                String note) { }
}
