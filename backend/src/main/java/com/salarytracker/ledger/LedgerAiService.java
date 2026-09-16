package com.salarytracker.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.LlmGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LedgerAiService {
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块)?");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LlmGateway gateway;
    private final LedgerBookAccess access;
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;

    public LedgerAiService(JdbcTemplate jdbc,
                           ObjectMapper mapper,
                           LlmGateway gateway,
                           LedgerBookAccess access,
                           LedgerBookService books,
                           LedgerTransactionService transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.gateway = gateway;
        this.access = access;
        this.books = books;
        this.transactions = transactions;
    }

    public Map<String, Object> previewText(String bookPublicId, String text) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        requireWrite(context);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("请输入记账内容");
        List<Map<String, Object>> drafts = gateway.configured()
                ? parseGateway(gateway.structured(systemPrompt(context), text, null, null))
                : localDrafts(text);
        return savePreview(context, "TEXT", enrich(context, drafts), !gateway.configured());
    }

    public Map<String, Object> previewImage(String bookPublicId, MultipartFile file) throws Exception {
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
    public Map<String, Object> confirm(String bookPublicId,
                                       String draftId,
                                       Map<String, Object> input,
                                       String idempotencyKey) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        requireWrite(context);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT payload_json,status,idempotency_key FROM ledger_ai_draft " +
                        "WHERE id=? AND book_id=? AND user_id=? AND expires_at>CURRENT_TIMESTAMP",
                draftId, context.bookId(), context.userId());
        if (rows.isEmpty()) throw new IllegalArgumentException("AI 草稿不存在或已过期");
        Map<String, Object> stored = parseMap(rows.get(0).get("payload_json"));
        if ("CONFIRMED".equals(rows.get(0).get("status"))) {
            return Map.of("draftId", draftId, "status", "CONFIRMED",
                    "created", stored.getOrDefault("created", List.of()));
        }
        List<Map<String, Object>> submitted = maps(input == null ? null : input.get("transactions"));
        if (submitted.isEmpty()) submitted = maps(stored.get("drafts"));
        if (submitted.isEmpty()) throw new IllegalArgumentException("至少保留一笔草稿");
        String key = text(idempotencyKey);
        if (key.isBlank()) key = text(input == null ? null : input.get("idempotencyKey"));
        if (key.isBlank()) key = "ai-" + draftId;
        List<Map<String, Object>> created = new ArrayList<>();
        for (int index = 0; index < submitted.size(); index++) {
            Map<String, Object> draft = new LinkedHashMap<>(submitted.get(index));
            draft.remove("warnings");
            draft.remove("parentCategoryName");
            draft.remove("categoryName");
            draft.remove("accountName");
            draft.put("source", "ai-confirmed");
            created.add(publicTransaction(transactions.create(
                    bookPublicId, draft, key + "-" + index)));
        }
        stored.put("created", created);
        jdbc.update(
                "UPDATE ledger_ai_draft SET status='CONFIRMED',payload_json=?,idempotency_key=?,confirmed_at=CURRENT_TIMESTAMP " +
                        "WHERE id=? AND status='PREVIEW'",
                json(stored), key, draftId);
        return Map.of("draftId", draftId, "status", "CONFIRMED", "created", created);
    }

    private Map<String, Object> savePreview(LedgerBookAccess.Context context,
                                            String sourceType,
                                            List<Map<String, Object>> drafts,
                                            boolean localFallback) {
        if (drafts.isEmpty()) throw new IllegalArgumentException("没有识别到可编辑的交易草稿");
        String id = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("drafts", drafts, "localFallback", localFallback);
        jdbc.update(
                "INSERT INTO ledger_ai_draft(id,book_id,user_id,source_type,payload_json,expires_at) " +
                        "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP+INTERVAL 24 HOUR)",
                id, context.bookId(), context.userId(), sourceType, json(payload));
        return Map.of(
                "draftId", id,
                "drafts", drafts,
                "sourceType", sourceType,
                "requiresConfirmation", true,
                "localFallback", localFallback);
    }

    private List<Map<String, Object>> enrich(LedgerBookAccess.Context context,
                                             List<Map<String, Object>> source) {
        List<Map<String, Object>> accounts = books.accounts(context.bookPublicId(), false);
        List<Map<String, Object>> categories = books.categories(context.bookPublicId(), false);
        List<Map<String, Object>> merchants = books.merchants(context.bookPublicId(), false);
        List<Map<String, Object>> projects = books.projects(context.bookPublicId(), false);
        List<Map<String, Object>> members = books.members(context.bookPublicId());
        Map<String, Object> currentMember = members.stream()
                .filter(item -> context.userId() == number(item.get("userId")))
                .findFirst().orElse(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> raw : source) {
            Map<String, Object> draft = new LinkedHashMap<>(raw);
            String kind = normalizeKind(draft.get("kind"));
            draft.put("kind", kind);
            draft.put("occurredOn", validDate(draft.get("occurredOn")));
            draft.put("amount", positiveAmount(draft.get("amount")));
            List<String> warnings = new ArrayList<>();
            resolveByName(draft, "accountId", "accountName", accounts, warnings, "账户");
            if ("TRANSFER".equals(kind)) {
                resolveByName(draft, "targetAccountId", "targetAccountName", accounts, warnings, "转入账户");
                if (text(draft.get("targetAccountId")).isBlank()) warnings.add("请选择转入账户");
            }
            List<Map<String, Object>> secondaries = categories.stream()
                    .filter(item -> item.get("parentId") != null)
                    .filter(item -> categoryKind(kind).equals(item.get("kind"))).toList();
            resolveCategory(draft, secondaries, categories, kind, warnings);
            resolveByName(draft, "merchantId", "merchantName", merchants, warnings, "商家");
            resolveByName(draft, "projectId", "projectName", projects, warnings, "项目");
            if (text(draft.get("memberId")).isBlank() && text(draft.get("member")).isBlank()
                    && currentMember != null) {
                draft.put("memberId", currentMember.get("id"));
                draft.put("member", currentMember.get("displayName"));
            }
            resolveMember(draft, members, warnings);
            if (text(draft.get("accountId")).isBlank()) warnings.add("请选择账户");
            if (text(draft.get("memberId")).isBlank()) warnings.add("请选择成员");
            if (requiresCategory(kind) && text(draft.get("categoryId")).isBlank()) warnings.add("请选择二级分类");
            draft.put("warnings", warnings.stream().distinct().toList());
            result.add(draft);
        }
        return result;
    }

    private String systemPrompt(LedgerBookAccess.Context context) {
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
                """.formatted(
                LocalDate.now(),
                names(books.accounts(context.bookPublicId(), false), "name"),
                categoryPaths(books.categories(context.bookPublicId(), false)),
                names(books.merchants(context.bookPublicId(), false), "name"),
                names(books.members(context.bookPublicId()), "displayName"),
                names(books.projects(context.bookPublicId(), false), "name"));
    }

    private List<Map<String, Object>> parseGateway(String response) {
        String json = text(response);
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first >= 0 && last > first) json = json.substring(first, last + 1);
        Map<String, Object> root = parseMap(json);
        List<Map<String, Object>> transactions = maps(root.get("transactions"));
        if (transactions.isEmpty() && root.containsKey("amount")) transactions = List.of(root);
        return transactions;
    }

    private List<Map<String, Object>> localDrafts(String text) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String part : text.split("[\\n；;]+")) {
            if (part.isBlank()) continue;
            Matcher matcher = AMOUNT.matcher(part);
            if (!matcher.find()) continue;
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("kind", inferKind(part));
            draft.put("amount", matcher.group(1));
            draft.put("occurredOn", inferDate(part));
            draft.put("note", part.trim());
            result.add(draft);
        }
        return result;
    }

    private void resolveByName(Map<String, Object> draft,
                               String idField,
                               String nameField,
                               List<Map<String, Object>> options,
                               List<String> warnings,
                               String label) {
        String id = text(draft.get(idField));
        if (!id.isBlank()) {
            Map<String, Object> matchedById = options.stream()
                    .filter(item -> id.equals(text(item.get("id"))))
                    .findFirst().orElse(null);
            if (matchedById != null) {
                draft.put(nameField, matchedById.get("name"));
                return;
            }
        }
        String name = text(draft.get(nameField));
        if (name.isBlank()) return;
        List<Map<String, Object>> matched = options.stream()
                .filter(item -> name.equalsIgnoreCase(text(item.get("name")))).toList();
        if (matched.size() == 1) {
            draft.put(idField, matched.get(0).get("id"));
            draft.put(nameField, matched.get(0).get("name"));
        }
        else warnings.add(label + "“" + name + "”未匹配");
    }

    private void resolveCategory(Map<String, Object> draft,
                                 List<Map<String, Object>> secondaries,
                                 List<Map<String, Object>> allCategories,
                                 String kind,
                                 List<String> warnings) {
        if (!requiresCategory(kind)) {
            draft.put("categoryMatchStatus", "not_required");
            return;
        }
        String id = text(draft.get("categoryId"));
        Map<String, Object> byId = secondaries.stream()
                .filter(item -> id.equals(text(item.get("id"))))
                .findFirst().orElse(null);
        if (byId != null) {
            enrichCategoryNames(draft, byId, allCategories);
            draft.put("categoryMatchStatus", "matched");
            return;
        }
        String childName = text(draft.get("categoryName"));
        String parentName = text(draft.get("parentCategoryName"));
        String[] reference = categoryReference(childName, parentName);
        final String resolvedParentName = reference[0];
        final String resolvedChildName = reference[1];
        parentName = resolvedParentName;
        childName = resolvedChildName;
        if (parentName.isBlank() && !resolvedChildName.isBlank()
                && allCategories.stream().anyMatch(item -> item.get("parentId") == null
                && kind.equals(item.get("kind"))
                && resolvedChildName.equalsIgnoreCase(text(item.get("name"))))) {
            draft.put("categoryMatchStatus", "primary_only");
            warnings.add("分类“" + childName + "”只有一级分类，请补充二级分类");
            return;
        }
        if (childName.isBlank() && !parentName.isBlank()) {
            draft.put("categoryMatchStatus", "primary_only");
            warnings.add("分类“" + parentName + "”只有一级分类，请补充二级分类");
            return;
        }
        if (childName.isBlank()) {
            draft.put("categoryMatchStatus", "missing");
            warnings.add("缺少二级分类，请补充分类");
            return;
        }
        List<Map<String, Object>> matched = secondaries.stream().filter(item -> {
            if (!resolvedChildName.equalsIgnoreCase(text(item.get("name")))) return false;
            if (resolvedParentName.isBlank()) return true;
            Map<String, Object> parent = allCategories.stream()
                    .filter(candidate -> text(candidate.get("id")).equals(text(item.get("parentId"))))
                    .findFirst().orElse(null);
            return resolvedParentName.equalsIgnoreCase(text(parent == null ? null : parent.get("name")));
        }).toList();
        if (matched.size() == 1) {
            Map<String, Object> category = matched.get(0);
            draft.put("categoryId", category.get("id"));
            enrichCategoryNames(draft, category, allCategories);
            draft.put("categoryMatchStatus", "matched");
        } else if (matched.isEmpty()) {
            draft.put("categoryMatchStatus", "unmatched");
            warnings.add("二级分类“" + childName + "”未匹配当前账本");
        } else {
            draft.put("categoryMatchStatus", "ambiguous");
            warnings.add("二级分类“" + childName + "”存在多个匹配，请选择一级分类");
        }
    }

    private void enrichCategoryNames(Map<String, Object> draft,
                                     Map<String, Object> category,
                                     List<Map<String, Object>> allCategories) {
        draft.put("categoryName", category.get("name"));
        allCategories.stream()
                .filter(item -> text(item.get("id")).equals(text(category.get("parentId"))))
                .findFirst()
                .ifPresent(parent -> draft.put("parentCategoryName", parent.get("name")));
    }

    /** Accept both separate fields and the path format emitted by the LLM, e.g. "餐饮 / 早餐". */
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

    private boolean requiresCategory(String kind) {
        return "INCOME".equals(kind) || "EXPENSE".equals(kind);
    }

    private String categoryPaths(List<Map<String, Object>> categories) {
        return categories.stream()
                .filter(item -> item.get("parentId") != null)
                .map(item -> {
                    String parent = categories.stream()
                            .filter(candidate -> text(candidate.get("id")).equals(text(item.get("parentId"))))
                            .map(candidate -> text(candidate.get("name")))
                            .findFirst().orElse("");
                    return parent.isBlank() ? text(item.get("name")) : parent + " / " + text(item.get("name"));
                })
                .filter(value -> !value.isBlank())
                .toList()
                .toString();
    }

    private void resolveMember(Map<String, Object> draft,
                               List<Map<String, Object>> members,
                               List<String> warnings) {
        String id = text(draft.get("memberId"));
        if (!id.isBlank()) {
            Map<String, Object> matchedById = members.stream()
                    .filter(item -> id.equals(text(item.get("id"))))
                    .findFirst().orElse(null);
            if (matchedById != null) {
                draft.put("member", matchedById.get("displayName"));
                return;
            }
        }
        String name = text(draft.get("member"));
        if (name.isBlank()) return;
        List<Map<String, Object>> matched = members.stream().filter(item ->
                name.equalsIgnoreCase(text(item.get("username")))
                        || name.equalsIgnoreCase(text(item.get("displayName")))).toList();
        if (matched.size() == 1) {
            draft.put("memberId", matched.get(0).get("id"));
            draft.put("member", matched.get(0).get("displayName"));
        }
        else warnings.add("成员“" + name + "”未匹配");
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

    private String normalizeKind(Object value) {
        String kind = text(value).toUpperCase(Locale.ROOT);
        return switch (kind) {
            case "收入" -> "INCOME";
            case "转账" -> "TRANSFER";
            case "借入" -> "BORROW_IN";
            case "借出" -> "LEND_OUT";
            case "收债" -> "COLLECT_DEBT";
            case "还债" -> "REPAY_DEBT";
            case "INCOME", "TRANSFER", "BORROW_IN", "LEND_OUT", "COLLECT_DEBT", "REPAY_DEBT" -> kind;
            default -> "EXPENSE";
        };
    }

    private String categoryKind(String kind) {
        return List.of("INCOME", "BORROW_IN", "COLLECT_DEBT").contains(kind) ? "INCOME" : "EXPENSE";
    }

    private String validDate(Object value) {
        try {
            return LocalDate.parse(text(value)).toString();
        } catch (Exception ignored) {
            return LocalDate.now().toString();
        }
    }

    private String positiveAmount(Object value) {
        try {
            return new BigDecimal(text(value)).abs().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String names(List<Map<String, Object>> values, String field) {
        return values.stream().map(item -> text(item.get(field))).filter(value -> !value.isBlank()).toList().toString();
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

    private Map<String, Object> parseMap(Object value) {
        try {
            return mapper.readValue(String.valueOf(value), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("AI 返回的结构化内容无效");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList();
    }

    private Map<String, Object> publicTransaction(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.remove("internalId");
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long number(Object value) {
        if (value == null) return 0;
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
