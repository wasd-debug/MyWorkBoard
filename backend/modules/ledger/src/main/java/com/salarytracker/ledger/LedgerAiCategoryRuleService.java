package com.salarytracker.ledger;

import com.salarytracker.platform.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static com.salarytracker.ledger.LedgerModels.*;

@Service
public class LedgerAiCategoryRuleService {
    private final JdbcTemplate jdbc;
    private final LedgerBookAccess access;
    private final LedgerBookService books;

    public LedgerAiCategoryRuleService(JdbcTemplate jdbc, LedgerBookAccess access, LedgerBookService books) {
        this.jdbc = jdbc;
        this.access = access;
        this.books = books;
    }

    public List<AiCategoryRule> list(String bookId) { return list(access.resolve(bookId)); }

    List<AiCategoryRule> list(LedgerBookAccess.Context context) {
        return DbRow.query(jdbc, "SELECT r.public_id,r.keyword,c.public_id category_id,r.kind,r.enabled,r.source,r.revision " +
                        "FROM ledger_ai_category_rule r JOIN ledger_category c ON c.id=r.category_id " +
                        "WHERE r.user_id=? AND r.book_id=? ORDER BY r.created_at,r.id",
                context.userId(), context.bookId()).stream().map(this::view).toList();
    }

    @Transactional
    public AiCategoryRule create(String bookId, AiCategoryRuleCommand input) {
        LedgerBookAccess.Context context = access.resolve(bookId);
        requireWrite(context);
        String keyword = checkedKeyword(input == null ? null : input.keyword());
        Category category = category(context, input.categoryId(), null);
        return insert(context, keyword, normalize(keyword), category, input.enabled() == null || input.enabled(), "MANUAL");
    }

    @Transactional
    public AiCategoryRule update(String bookId, String id, AiCategoryRuleCommand input, String ifMatch) {
        LedgerBookAccess.Context context = access.resolve(bookId);
        requireWrite(context);
        AiCategoryRule before = find(context, id);
        requireRevision(ifMatch, before.revision());
        if (input == null) throw new IllegalArgumentException("请提供规则内容");
        boolean keywordChanged = input.keyword() != null && !normalize(input.keyword()).equals(normalize(before.keyword()));
        String keyword = keywordChanged ? checkedKeyword(input.keyword()) : before.keyword();
        Category category = category(context, input.categoryId() == null ? before.categoryId() : input.categoryId(), null);
        boolean enabled = input.enabled() == null ? before.enabled() : input.enabled();
        String source = keywordChanged ? "MANUAL" : before.source();
        String normalized = "LEARNED".equals(source) ? normalizedKeyword(context, id) : normalize(keyword);
        int changed = jdbc.update("UPDATE ledger_ai_category_rule SET keyword=?,normalized_keyword=?,category_id=" +
                        "(SELECT id FROM ledger_category WHERE public_id=? AND book_id=?),kind=?,enabled=?,source=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND book_id=? AND revision=?",
                keyword, normalized, category.id(), context.bookId(), category.kind().name(), enabled, source,
                id, context.userId(), context.bookId(), before.revision());
        if (changed != 1) throw new ConflictException("分类规则版本已变化", before.revision());
        return new AiCategoryRule(id, keyword, category.id(), category.kind(), enabled, source, before.revision() + 1);
    }

    @Transactional
    public DeleteCount delete(String bookId, String id, String ifMatch) {
        LedgerBookAccess.Context context = access.resolve(bookId);
        requireWrite(context);
        AiCategoryRule before = find(context, id);
        requireRevision(ifMatch, before.revision());
        int changed = jdbc.update("DELETE FROM ledger_ai_category_rule WHERE public_id=? AND user_id=? AND book_id=? AND revision=?",
                id, context.userId(), context.bookId(), before.revision());
        if (changed != 1) throw new ConflictException("分类规则版本已变化", before.revision());
        return new DeleteCount(changed);
    }

    List<MatchRule> matchingRules(LedgerBookAccess.Context context) {
        return DbRow.query(jdbc, "SELECT c.public_id category_id,r.kind,r.source,r.normalized_keyword " +
                        "FROM ledger_ai_category_rule r JOIN ledger_category c ON c.id=r.category_id " +
                        "JOIN ledger_category p ON p.id=c.parent_id AND p.book_id=c.book_id " +
                        "WHERE r.user_id=? AND r.book_id=? AND r.enabled=TRUE AND c.book_id=r.book_id " +
                        "AND c.deleted=FALSE AND c.hidden=FALSE AND p.deleted=FALSE AND p.hidden=FALSE AND p.parent_id IS NULL " +
                        "AND c.kind=r.kind AND p.kind=r.kind",
                context.userId(), context.bookId()).stream().map(row -> new MatchRule(
                string(row.get("category_id")), CategoryKind.valueOf(string(row.get("kind"))),
                string(row.get("source")), string(row.get("normalized_keyword")))).toList();
    }

    static RuleMatch match(List<MatchRule> rules, TransactionKind kind, String note, String merchant) {
        if (kind != TransactionKind.EXPENSE && kind != TransactionKind.INCOME) return null;
        String haystack = normalize(note) + "\n" + normalize(merchant);
        String fingerprint = fingerprint(note, merchant);
        List<MatchRule> matches = rules.stream().filter(rule -> rule.kind().name().equals(kind.name()))
                .filter(rule -> "MANUAL".equals(rule.source()) ? haystack.contains(rule.keyword())
                        : !fingerprint.isEmpty() && fingerprint.equals(rule.keyword())).toList();
        if (matches.isEmpty()) return null;
        boolean manual = matches.stream().anyMatch(rule -> "MANUAL".equals(rule.source()));
        List<MatchRule> priority = matches.stream().filter(rule -> manual == "MANUAL".equals(rule.source())).toList();
        int longest = priority.stream().mapToInt(rule -> rule.keyword().length()).max().orElse(0);
        List<MatchRule> best = priority.stream().filter(rule -> rule.keyword().length() == longest).toList();
        boolean conflict = best.stream().map(MatchRule::categoryId).distinct().count() > 1;
        return new RuleMatch(conflict ? null : best.get(0).categoryId(), manual ? "MANUAL" : "LEARNED", conflict);
    }

    Category category(LedgerBookAccess.Context context, String categoryId, TransactionKind kind) {
        List<Category> categories = books.categories(context.bookPublicId(), false);
        return categories.stream().filter(item -> item.id().equals(categoryId) && item.parentId() != null && !item.hidden())
                .filter(item -> kind == null || item.kind().name().equals(kind.name()))
                .filter(item -> categories.stream().anyMatch(parent -> parent.id().equals(item.parentId())
                        && parent.parentId() == null && !parent.hidden() && parent.kind() == item.kind()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("请选择当前账本有效且类型一致的二级分类"));
    }

    void feedback(LedgerBookAccess.Context context, AiDraft original, TransactionCommand finalDraft,
                  Transaction created, AiCategoryReview review) {
        if (finalDraft.kind() != TransactionKind.EXPENSE && finalDraft.kind() != TransactionKind.INCOME) return;
        Category category = category(context, finalDraft.categoryId(), finalDraft.kind());
        String keyword = review == null ? "" : normalize(review.rememberKeyword());
        if (!keyword.isBlank()) {
            keyword = checkedKeyword(keyword);
            String haystack = normalize(original.note()) + "\n" + normalize(original.merchantName());
            if (!haystack.contains(keyword)) throw new IllegalArgumentException("记忆关键词必须出现在原始备注或商家中");
        }
        jdbc.update("INSERT INTO ledger_ai_category_feedback(user_id,book_id,draft_id,transaction_id,original_category_id," +
                        "confirmed_category_id,keyword) VALUES(?,?,?,?," +
                        "(SELECT id FROM ledger_category WHERE public_id=? AND book_id=?)," +
                        "(SELECT id FROM ledger_category WHERE public_id=? AND book_id=?),?)",
                context.userId(), context.bookId(), original.id(), created.internalId(), original.categoryId(), context.bookId(),
                category.id(), context.bookId(), keyword.isBlank() ? null : keyword);
        boolean corrected = !Objects.equals(original.categoryId(), category.id()) || original.kind() != finalDraft.kind();
        String source = keyword.isBlank() ? "LEARNED" : "MANUAL";
        String normalized = keyword.isBlank() ? fingerprint(original.note(), original.merchantName()) : normalize(keyword);
        if (normalized.isBlank() || !corrected && keyword.isBlank()) return;
        String display = keyword.isBlank() ? normalize(original.note()) : keyword;
        if (display.length() > 160) display = display.substring(0, 160);
        List<DbRow> existing = DbRow.query(jdbc, "SELECT public_id FROM ledger_ai_category_rule " +
                        "WHERE user_id=? AND book_id=? AND source=? AND normalized_keyword=? FOR UPDATE",
                context.userId(), context.bookId(), source, normalized);
        if (existing.isEmpty()) {
            try {
                insert(context, display, normalized, category, true, source);
                return;
            } catch (DuplicateKeyException ignored) {
                // 并发确认相同内容时，更新已由另一事务建立的规则。
            }
        }
        jdbc.update("UPDATE ledger_ai_category_rule SET category_id=(SELECT id FROM ledger_category WHERE public_id=? AND book_id=?)," +
                        "kind=?,enabled=IF(source='MANUAL',TRUE,enabled),revision=revision+1 WHERE user_id=? AND book_id=? AND source=? AND normalized_keyword=?",
                category.id(), context.bookId(), category.kind().name(), context.userId(), context.bookId(), source, normalized);
    }

    private AiCategoryRule insert(LedgerBookAccess.Context context, String keyword, String normalized,
                                  Category category, boolean enabled, String source) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ledger_ai_category_rule(public_id,user_id,book_id,keyword,normalized_keyword,category_id,kind,enabled,source) " +
                        "VALUES(?,?,?,?,?,(SELECT id FROM ledger_category WHERE public_id=? AND book_id=?),?,?,?)",
                id, context.userId(), context.bookId(), keyword, normalized, category.id(), context.bookId(),
                category.kind().name(), enabled, source);
        return new AiCategoryRule(id, keyword, category.id(), category.kind(), enabled, source, 1);
    }

    private AiCategoryRule find(LedgerBookAccess.Context context, String id) {
        return list(context).stream().filter(rule -> rule.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("分类规则不存在"));
    }

    private String normalizedKeyword(LedgerBookAccess.Context context, String id) {
        return jdbc.queryForObject("SELECT normalized_keyword FROM ledger_ai_category_rule WHERE public_id=? AND user_id=? AND book_id=?",
                String.class, id, context.userId(), context.bookId());
    }

    private AiCategoryRule view(DbRow row) {
        return new AiCategoryRule(string(row.get("public_id")), string(row.get("keyword")), string(row.get("category_id")),
                CategoryKind.valueOf(string(row.get("kind"))), Boolean.TRUE.equals(row.get("enabled"))
                || row.get("enabled") instanceof Number number && number.intValue() != 0,
                string(row.get("source")), ((Number) row.get("revision")).longValue());
    }

    static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .strip().replaceAll("\\s+", " ");
    }

    static String fingerprint(String note, String merchant) {
        String normalized = normalize(note);
        if (normalized.length() < 2) return "";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((normalized + "\n" + normalize(merchant)).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    static String checkedKeyword(String input) {
        String keyword = normalize(input);
        if (keyword.length() < 2 || keyword.length() > 80 || keyword.contains("\n")) {
            throw new IllegalArgumentException("关键词长度应为 2 至 80 个字符");
        }
        return keyword;
    }

    private void requireWrite(LedgerBookAccess.Context context) {
        if (context.isOwner() || context.permissions().contains("TRANSACTION_ANY_WRITE")) return;
        access.require(context, "TRANSACTION_OWN_WRITE");
    }

    private void requireRevision(String input, long revision) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("If-Match 必填");
        try {
            long expected = Long.parseLong(input.replace("W/", "").replace("\"", "").trim());
            if (expected != revision) throw new ConflictException("分类规则版本已变化", revision);
        } catch (NumberFormatException exception) { throw new IllegalArgumentException("If-Match 格式不正确"); }
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    record MatchRule(String categoryId, CategoryKind kind, String source, String keyword) { }
    record RuleMatch(String categoryId, String source, boolean conflict) { }
}
