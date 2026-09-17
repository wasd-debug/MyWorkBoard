package com.salarytracker.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LedgerTransactionService {
    private static final Set<String> KINDS = Set.of(
            "EXPENSE", "INCOME", "TRANSFER", "BORROW_IN", "LEND_OUT", "COLLECT_DEBT", "REPAY_DEBT");
    private static final Set<String> INCOME_KINDS = Set.of("INCOME", "BORROW_IN", "COLLECT_DEBT");
    private static final Set<String> EXPENSE_KINDS = Set.of("EXPENSE", "LEND_OUT", "REPAY_DEBT");
    private static final String SELECT =
            "SELECT t.id internal_id,t.public_id,t.book_id,t.account_id,a.public_id account_public_id,a.name account_name,a.icon account_icon," +
                    "t.counterparty_account_id,target.public_id target_account_public_id,target.name target_account_name,target.icon target_account_icon," +
                    "t.transfer_group_id,t.category_id,c.public_id category_public_id,c.name category_name,c.icon category_icon,c.color category_color," +
                    "parent.public_id parent_category_public_id,parent.name parent_category_name,parent.icon parent_category_icon,parent.color parent_category_color," +
                    "t.merchant_id,m.public_id merchant_public_id,m.name merchant_name,m.icon merchant_icon," +
                    "t.member_id,bm.public_id member_public_id,bm.icon member_icon,u.username member_username,u.nickname member_nickname," +
                    "t.project_id,p.public_id project_public_id,p.name project_name,p.icon project_icon,p.color project_color," +
                    "t.kind,t.amount,t.currency,t.occurred_on,t.payee,t.member_name,t.project_name legacy_project_name," +
                    "t.note,t.source,t.client_op_id,t.revision,t.deleted,t.deleted_at,t.created_by,t.created_at,t.updated_at " +
                    "FROM ledger_transaction t JOIN ledger_account a ON a.id=t.account_id " +
                    "LEFT JOIN ledger_account target ON target.id=t.counterparty_account_id " +
                    "LEFT JOIN ledger_category c ON c.id=t.category_id " +
                    "LEFT JOIN ledger_category parent ON parent.id=c.parent_id " +
                    "LEFT JOIN ledger_merchant m ON m.id=t.merchant_id " +
                    "LEFT JOIN ledger_book_member bm ON bm.id=t.member_id " +
                    "LEFT JOIN app_user u ON u.id=bm.user_id " +
                    "LEFT JOIN ledger_project p ON p.id=t.project_id ";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerBookAccess access;
    private final LedgerBookService resources;
    private final LedgerAuditService audit;

    public LedgerTransactionService(JdbcTemplate jdbc,
                                    ObjectMapper mapper,
                                    LedgerBookAccess access,
                                    LedgerBookService resources,
                                    LedgerAuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.access = access;
        this.resources = resources;
        this.audit = audit;
    }

    public Map<String, Object> list(String bookPublicId, Map<String, String> filters) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        int page = positiveInt(filters.get("page"), 1, 1, Integer.MAX_VALUE);
        int pageSize = positiveInt(filters.get("pageSize"), 20, 1, 100);
        List<Object> args = new ArrayList<>();
        String where = where(context, filters, args, false);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction t " +
                        "LEFT JOIN ledger_account a ON a.id=t.account_id " +
                        "LEFT JOIN ledger_category c ON c.id=t.category_id " +
                        "LEFT JOIN ledger_merchant m ON m.id=t.merchant_id " +
                        "LEFT JOIN ledger_project p ON p.id=t.project_id " +
                        "WHERE " + where,
                Long.class, args.toArray());
        String sort = sortColumn(filters.get("sort"));
        String direction = "asc".equalsIgnoreCase(filters.get("direction")) ? "ASC" : "DESC";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((page - 1) * pageSize);
        pageArgs.add(pageSize);
        List<Map<String, Object>> items = jdbc.queryForList(
                SELECT + "WHERE " + where + " ORDER BY " + sort + " " + direction + ",t.id " + direction +
                        " LIMIT ?,?",
                pageArgs.toArray()).stream().map(this::view).toList();
        Map<String, Object> summary = summary(context, filters);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("totalPages", Math.max(1, (total + pageSize - 1) / pageSize));
        result.put("summary", summary);
        return result;
    }

    public List<Map<String, Object>> recent(String bookPublicId, int limit) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        return jdbc.queryForList(
                SELECT + "WHERE t.book_id=? AND t.deleted=FALSE AND t.kind<>'TRANSFER_IN' " +
                        "ORDER BY t.occurred_on DESC,t.id DESC LIMIT ?",
                context.bookId(), Math.min(Math.max(1, limit), 100)).stream().map(this::view).toList();
    }

    public Map<String, Object> overview(String bookPublicId, String from, String to) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        LocalDate start = from == null || from.isBlank()
                ? YearMonth.now().atDay(1) : LocalDate.parse(from);
        LocalDate end = to == null || to.isBlank()
                ? YearMonth.now().atEndOfMonth() : LocalDate.parse(to);
        if (end.isBefore(start)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        Map<String, Object> totals = jdbc.queryForMap(
                "SELECT COALESCE(SUM(CASE WHEN kind IN ('INCOME','BORROW_IN','COLLECT_DEBT') THEN amount ELSE 0 END),0) income," +
                        "COALESCE(SUM(CASE WHEN kind IN ('EXPENSE','LEND_OUT','REPAY_DEBT') THEN amount ELSE 0 END),0) expense," +
                        "COUNT(CASE WHEN kind<>'TRANSFER_IN' THEN 1 END) count " +
                        "FROM ledger_transaction WHERE book_id=? AND deleted=FALSE AND occurred_on BETWEEN ? AND ?",
                context.bookId(), start, end);
        BigDecimal income = decimal(totals.get("income"));
        BigDecimal expense = decimal(totals.get("expense"));
        List<Map<String, Object>> daily = jdbc.queryForList(
                "SELECT occurred_on date," +
                        "COALESCE(SUM(CASE WHEN kind IN ('INCOME','BORROW_IN','COLLECT_DEBT') THEN amount ELSE 0 END),0) income," +
                        "COALESCE(SUM(CASE WHEN kind IN ('EXPENSE','LEND_OUT','REPAY_DEBT') THEN amount ELSE 0 END),0) expense " +
                        "FROM ledger_transaction WHERE book_id=? AND deleted=FALSE AND occurred_on BETWEEN ? AND ? " +
                        "GROUP BY occurred_on ORDER BY occurred_on",
                context.bookId(), start, end);
        List<Map<String, Object>> categories = jdbc.queryForList(
                "SELECT parent.public_id parent_id,parent.name parent,c.public_id category_id,c.name category,SUM(t.amount) amount " +
                        "FROM ledger_transaction t JOIN ledger_category c ON c.id=t.category_id " +
                        "LEFT JOIN ledger_category parent ON parent.id=c.parent_id " +
                        "WHERE t.book_id=? AND t.deleted=FALSE AND t.kind IN ('EXPENSE','LEND_OUT','REPAY_DEBT') " +
                        "AND t.occurred_on BETWEEN ? AND ? GROUP BY parent.id,c.id ORDER BY amount DESC",
                context.bookId(), start, end);
        String month = YearMonth.from(start).equals(YearMonth.from(end))
                ? YearMonth.from(start).toString() : YearMonth.now().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("income", income);
        result.put("expense", expense);
        result.put("balance", income.subtract(expense));
        result.put("count", number(totals.get("count")));
        result.put("daily", daily);
        result.put("categories", categories);
        result.put("budgets", resources.budgets(bookPublicId, month));
        return result;
    }

    @Transactional
    public Map<String, Object> create(String bookPublicId,
                                      Map<String, Object> input,
                                      String idempotencyKey) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        return createWithContext(context, input, idempotencyKey);
    }

    /** Used by ShedLock background jobs after the job has resolved the book owner context. */
    @Transactional
    Map<String, Object> createForSystem(LedgerBookAccess.Context context,
                                        Map<String, Object> input,
                                        String idempotencyKey) {
        return createWithContext(context, input, idempotencyKey);
    }

    private Map<String, Object> createWithContext(LedgerBookAccess.Context context,
                                                  Map<String, Object> input,
                                                  String idempotencyKey) {
        if (!context.permissions().contains("TRANSACTION_OWN_WRITE")
                && !context.permissions().contains("TRANSACTION_ANY_WRITE")
                && !context.isOwner()) {
            access.require(context, "TRANSACTION_OWN_WRITE");
        }
        String opId = opId(input, idempotencyKey);
        List<Map<String, Object>> previous = jdbc.queryForList(
                "SELECT public_id FROM ledger_transaction WHERE book_id=? AND client_op_id=?",
                context.bookId(), opId);
        if (!previous.isEmpty()) return get(context, String.valueOf(previous.get(0).get("public_id")), true);

        Draft draft = draft(context, input, null);
        if ("TRANSFER".equals(draft.kind())) return createTransfer(context, draft, opId);
        long internalId = insert(context, requestedPublicId(input), draft, draft.kind(), null, opId);
        Map<String, Object> result = getByInternalId(context, internalId, true);
        appendVersion(context, internalId, 1, "CREATE", result);
        resources.appendSync(context, opId, "transaction", String.valueOf(result.get("id")), "UPSERT", result);
        audit.record(context, "transaction.create", "transaction", String.valueOf(result.get("id")), null, result);
        return result;
    }

    @Transactional
    public Map<String, Object> update(String bookPublicId,
                                      String transactionPublicId,
                                      Map<String, Object> input,
                                      String ifMatch,
                                      String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        Map<String, Object> before = get(context, transactionPublicId, true);
        access.requireTransactionWrite(context, number(before.get("createdBy")));
        long revision = number(before.get("revision"));
        requireRevision(ifMatch, revision);
        Draft draft = draft(context, input, before);
        boolean existingTransfer = "TRANSFER".equals(before.get("kind"));
        if (existingTransfer != "TRANSFER".equals(draft.kind())) {
            throw new IllegalArgumentException("转账与其他流水类型不能互相转换");
        }
        long internalId = number(before.get("internalId"));
        if (existingTransfer) {
            updateTransfer(context, internalId, before, draft);
        } else {
            jdbc.update(
                    "UPDATE ledger_transaction SET account_id=?,counterparty_account_id=NULL,category_id=?,merchant_id=?," +
                            "member_id=?,project_id=?,kind=?,amount=?,currency=?,occurred_on=?,payee=?,member_name=?," +
                            "project_name=?,note=?,revision=revision+1 WHERE id=? AND book_id=? AND deleted=FALSE",
                    draft.accountId(), draft.categoryId(), nullable(draft.merchantId()), nullable(draft.memberId()),
                    nullable(draft.projectId()), draft.kind(), draft.amount(), draft.currency(), draft.occurredOn(),
                    draft.merchantName(), draft.memberName(), draft.projectName(), draft.note(),
                    internalId, context.bookId());
        }
        Map<String, Object> result = get(context, transactionPublicId, true);
        appendVersion(context, internalId, number(result.get("revision")), "UPDATE", result);
        String operationId = blankTo(opId, UUID.randomUUID().toString());
        resources.appendSync(context, operationId, "transaction", transactionPublicId, "UPSERT", result);
        audit.record(context, "transaction.update", "transaction", transactionPublicId, before, result);
        return result;
    }

    @Transactional
    public Map<String, Object> delete(String bookPublicId,
                                      String transactionPublicId,
                                      String ifMatch,
                                      String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        Map<String, Object> before = get(context, transactionPublicId, true);
        access.requireTransactionWrite(context, number(before.get("createdBy")));
        long revision = number(before.get("revision"));
        requireRevision(ifMatch, revision);
        List<Long> ids = transactionGroupIds(context, before);
        jdbc.update(
                "UPDATE ledger_transaction SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 " +
                        ("TRANSFER".equals(before.get("kind"))
                                ? "WHERE transfer_group_id=? AND book_id=? AND deleted=FALSE"
                                : "WHERE public_id=? AND book_id=? AND deleted=FALSE"),
                "TRANSFER".equals(before.get("kind")) ? before.get("transferGroupId") : transactionPublicId,
                context.bookId());
        for (long id : ids) {
            Map<String, Object> deleted = getByInternalId(context, id, false);
            appendVersion(context, id, number(deleted.get("revision")), "DELETE", deleted);
        }
        Map<String, Object> result = tombstone(transactionPublicId, revision + 1);
        String operationId = blankTo(opId, UUID.randomUUID().toString());
        resources.appendSync(context, operationId, "transaction", transactionPublicId, "DELETE", result);
        audit.record(context, "transaction.delete", "transaction", transactionPublicId, before, result);
        return result;
    }

    public List<Map<String, Object>> history(String bookPublicId, String transactionPublicId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        Map<String, Object> transaction = get(context, transactionPublicId, false);
        access.requireTransactionWrite(context, number(transaction.get("createdBy")));
        long internalId = number(transaction.get("internalId"));
        return jdbc.queryForList(
                "SELECT v.revision,v.operation,v.payload_json,v.created_at,u.username,u.nickname " +
                        "FROM ledger_transaction_version v JOIN app_user u ON u.id=v.actor_user_id " +
                        "WHERE v.transaction_id=? AND v.book_id=? ORDER BY v.revision DESC",
                internalId, context.bookId()).stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("revision", number(row.get("revision")));
            result.put("operation", row.get("operation"));
            result.put("payload", parse(row.get("payload_json")));
            result.put("actor", text(row.get("nickname")).isBlank() ? row.get("username") : row.get("nickname"));
            result.put("createdAt", row.get("created_at"));
            return result;
        }).toList();
    }

    @Transactional
    public Map<String, Object> restore(String bookPublicId,
                                       String transactionPublicId,
                                       String opId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        Map<String, Object> before = get(context, transactionPublicId, false);
        if (!Boolean.TRUE.equals(before.get("deleted"))) throw new IllegalArgumentException("流水未在回收站");
        access.requireTransactionWrite(context, number(before.get("createdBy")));
        List<Long> ids = transactionGroupIds(context, before);
        if ("TRANSFER".equals(before.get("kind"))) {
            jdbc.update(
                    "UPDATE ledger_transaction SET deleted=FALSE,deleted_at=NULL,revision=revision+1 " +
                            "WHERE transfer_group_id=? AND book_id=?",
                    before.get("transferGroupId"), context.bookId());
        } else {
            jdbc.update(
                    "UPDATE ledger_transaction SET deleted=FALSE,deleted_at=NULL,revision=revision+1 WHERE public_id=? AND book_id=?",
                    transactionPublicId, context.bookId());
        }
        for (long id : ids) {
            Map<String, Object> restored = getByInternalId(context, id, true);
            appendVersion(context, id, number(restored.get("revision")), "RESTORE", restored);
        }
        Map<String, Object> result = get(context, transactionPublicId, true);
        resources.appendSync(context, blankTo(opId, UUID.randomUUID().toString()),
                "transaction", transactionPublicId, "UPSERT", result);
        audit.record(context, "transaction.restore", "transaction", transactionPublicId, before, result);
        return result;
    }

    @Transactional
    public Map<String, Object> purge(String bookPublicId, String transactionPublicId) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        if (!context.isAdmin()) throw new com.salarytracker.platform.ForbiddenException("只有主人或管理员可以永久删除");
        Map<String, Object> before = get(context, transactionPublicId, false);
        if (!Boolean.TRUE.equals(before.get("deleted"))) throw new IllegalArgumentException("请先将流水移入回收站");
        List<Long> ids = transactionGroupIds(context, before);
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbc.update("DELETE FROM ledger_transaction_version WHERE transaction_id IN (" + placeholders + ")", ids.toArray());
        jdbc.update("DELETE FROM ledger_transaction_history WHERE transaction_id IN (" + placeholders + ")", ids.toArray());
        jdbc.update("DELETE FROM ledger_transaction WHERE id IN (" + placeholders + ")", ids.toArray());
        audit.record(context, "transaction.purge", "transaction", transactionPublicId, before, null);
        return Map.of("id", transactionPublicId, "purged", true);
    }

    @Transactional
    public Map<String, Object> copy(String sourceBookPublicId,
                                    String transactionPublicId,
                                    Map<String, Object> input) {
        LedgerBookAccess.Context source = access.resolve(sourceBookPublicId);
        Map<String, Object> transaction = get(source, transactionPublicId, true);
        String targetBookPublicId = textOr(input == null ? null : input.get("targetBookId"), sourceBookPublicId);
        LedgerBookAccess.Context target = access.resolve(targetBookPublicId);
        if (!target.permissions().contains("TRANSACTION_OWN_WRITE")
                && !target.permissions().contains("TRANSACTION_ANY_WRITE")
                && !target.isOwner()) {
            access.require(target, "TRANSACTION_OWN_WRITE");
        }
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", UUID.randomUUID().toString());
        draft.put("kind", transaction.get("kind"));
        draft.put("amount", transaction.get("amount"));
        draft.put("occurredOn", input != null && input.get("occurredOn") != null
                ? input.get("occurredOn") : LocalDate.now().toString());
        draft.put("currency", transaction.get("currency"));
        draft.put("note", transaction.get("note"));
        draft.put("accountId", matchAccount(target, String.valueOf(transaction.get("accountName"))));
        if ("TRANSFER".equals(transaction.get("kind"))) {
            draft.put("targetAccountId", matchAccount(target, String.valueOf(transaction.get("targetAccountName"))));
        }
        draft.put("categoryId", matchCategory(target,
                text(transaction.get("parentCategoryName")), text(transaction.get("categoryName")),
                String.valueOf(transaction.get("kind"))));
        if (!text(transaction.get("merchantName")).isBlank()) {
            draft.put("merchantId", matchNamed(target, "merchant", text(transaction.get("merchantName"))));
        }
        if (!text(transaction.get("projectName")).isBlank()) {
            draft.put("projectId", matchNamed(target, "project", text(transaction.get("projectName"))));
        }
        String memberUsername = text(transaction.get("memberUsername"));
        if (!memberUsername.isBlank()) {
            List<String> members = jdbc.queryForList(
                    "SELECT m.public_id FROM ledger_book_member m JOIN app_user u ON u.id=m.user_id " +
                            "WHERE m.book_id=? AND m.deleted=FALSE AND u.username=?",
                    String.class, target.bookId(), memberUsername);
            if (!members.isEmpty()) draft.put("memberId", members.get(0));
        }
        return create(target.bookPublicId(), draft, "copy-" + UUID.randomUUID());
    }

    @Transactional
    public int materialize(String bookPublicId, String month) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        access.require(context, "RESOURCE_MANAGE");
        return materializeBook(context.bookId(), YearMonth.parse(month));
    }

    @Transactional
    public int materializeBook(long bookId, YearMonth month) {
        LocalDate end = month.atEndOfMonth();
        Long cursor = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id),0) FROM ledger_sync_oplog WHERE book_id=?", Long.class, bookId);
        int count = 0;
        for (Map<String, Object> account : jdbc.queryForList(
                "SELECT id,user_id,opening_balance FROM ledger_account WHERE book_id=? AND deleted=FALSE",
                bookId)) {
            long accountId = number(account.get("id"));
            BigDecimal opening = decimal(account.get("opening_balance"));
            BigDecimal delta = decimal(jdbc.queryForObject(
                    "SELECT COALESCE(SUM(CASE " +
                            "WHEN kind IN ('INCOME','BORROW_IN','COLLECT_DEBT','TRANSFER_IN') THEN amount " +
                            "WHEN kind IN ('EXPENSE','LEND_OUT','REPAY_DEBT','TRANSFER_OUT') THEN -amount ELSE 0 END),0) " +
                            "FROM ledger_transaction WHERE book_id=? AND account_id=? AND deleted=FALSE AND occurred_on<=?",
                    BigDecimal.class, bookId, accountId, end));
            jdbc.update(
                    "INSERT INTO ledger_balance_snapshot(user_id,book_id,account_id,month_key,balance,snapshot_cursor,recalculated_at) " +
                            "VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE balance=VALUES(balance)," +
                            "snapshot_cursor=VALUES(snapshot_cursor),calculated_at=CURRENT_TIMESTAMP,recalculated_at=CURRENT_TIMESTAMP",
                    number(account.get("user_id")), bookId, accountId, month.toString(),
                    opening.add(delta), cursor == null ? 0 : cursor);
            count++;
        }
        return count;
    }

    @Transactional
    public int materializeAll(YearMonth month) {
        int count = 0;
        for (Long bookId : jdbc.queryForList(
                "SELECT id FROM ledger_book WHERE deleted=FALSE", Long.class)) {
            count += materializeBook(bookId, month);
        }
        return count;
    }

    @Transactional
    public int purgeExpiredRecycle() {
        List<Long> transactionIds = jdbc.queryForList(
                "SELECT id FROM ledger_transaction WHERE deleted=TRUE AND deleted_at<CURRENT_TIMESTAMP-INTERVAL 90 DAY",
                Long.class);
        if (!transactionIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(transactionIds.size(), "?"));
            jdbc.update("DELETE FROM ledger_transaction_version WHERE transaction_id IN (" + placeholders + ")",
                    transactionIds.toArray());
            jdbc.update("DELETE FROM ledger_transaction_history WHERE transaction_id IN (" + placeholders + ")",
                    transactionIds.toArray());
            jdbc.update("DELETE FROM ledger_transaction WHERE id IN (" + placeholders + ")",
                    transactionIds.toArray());
        }
        int removed = transactionIds.size();
        removed += jdbc.update(
                "DELETE b FROM ledger_budget b WHERE b.deleted=TRUE AND b.deleted_at<CURRENT_TIMESTAMP-INTERVAL 90 DAY");
        removed += jdbc.update(
                "DELETE m FROM ledger_merchant m LEFT JOIN ledger_transaction t ON t.merchant_id=m.id " +
                        "WHERE m.deleted=TRUE AND m.deleted_at<CURRENT_TIMESTAMP-INTERVAL 90 DAY AND t.id IS NULL");
        removed += jdbc.update(
                "DELETE p FROM ledger_project p LEFT JOIN ledger_transaction t ON t.project_id=p.id " +
                        "WHERE p.deleted=TRUE AND p.deleted_at<CURRENT_TIMESTAMP-INTERVAL 90 DAY AND t.id IS NULL");
        removed += jdbc.update(
                "DELETE c FROM ledger_category c LEFT JOIN ledger_transaction t ON t.category_id=c.id " +
                        "LEFT JOIN ledger_category child ON child.parent_id=c.id " +
                        "WHERE c.deleted=TRUE AND c.deleted_at<CURRENT_TIMESTAMP-INTERVAL 90 DAY " +
                        "AND t.id IS NULL AND child.id IS NULL");
        removed += jdbc.update(
                "DELETE a FROM ledger_account a LEFT JOIN ledger_transaction t " +
                        "ON t.account_id=a.id OR t.counterparty_account_id=a.id " +
                        "WHERE a.deleted=TRUE AND a.deleted_at<CURRENT_TIMESTAMP-INTERVAL 90 DAY AND t.id IS NULL");
        removed += jdbc.update(
                "DELETE m FROM ledger_book_member m LEFT JOIN ledger_transaction t ON t.member_id=m.id " +
                        "WHERE m.deleted=TRUE AND m.deleted_at<CURRENT_TIMESTAMP-INTERVAL 90 DAY AND t.id IS NULL");
        List<Long> expiredRoles = jdbc.queryForList(
                "SELECT r.id FROM ledger_role r LEFT JOIN ledger_book_member m ON m.role_id=r.id AND m.deleted=FALSE " +
                        "WHERE r.deleted=TRUE AND r.deleted_at<CURRENT_TIMESTAMP-INTERVAL 90 DAY AND m.id IS NULL",
                Long.class);
        if (!expiredRoles.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(expiredRoles.size(), "?"));
            removed += jdbc.update("DELETE FROM ledger_role_permission WHERE role_id IN (" + placeholders + ")",
                    expiredRoles.toArray());
            removed += jdbc.update("DELETE FROM ledger_role WHERE id IN (" + placeholders + ")",
                    expiredRoles.toArray());
        }
        removed += jdbc.update(
                "DELETE FROM ledger_audit_log WHERE created_at<CURRENT_TIMESTAMP-INTERVAL 30 DAY");
        return removed;
    }

    Map<String, Object> get(LedgerBookAccess.Context context, String publicId, boolean activeOnly) {
        String active = activeOnly ? " AND t.deleted=FALSE" : "";
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT + "WHERE t.public_id=? AND t.book_id=?" + active,
                publicId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("流水不存在");
        return view(rows.get(0));
    }

    private Map<String, Object> getByInternalId(LedgerBookAccess.Context context,
                                                long internalId,
                                                boolean activeOnly) {
        String active = activeOnly ? " AND t.deleted=FALSE" : "";
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT + "WHERE t.id=? AND t.book_id=?" + active,
                internalId, context.bookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("流水不存在");
        return view(rows.get(0));
    }

    private String where(LedgerBookAccess.Context context,
                         Map<String, String> filters,
                         List<Object> args,
                         boolean deleted) {
        StringBuilder where = new StringBuilder("t.book_id=? AND t.deleted=? AND t.kind<>'TRANSFER_IN'");
        args.add(context.bookId());
        args.add(deleted);
        addDate(where, args, "t.occurred_on>=?", filters.get("from"));
        addDate(where, args, "t.occurred_on<=?", filters.get("to"));
        String filterKind = normalizedFilterKind(filters.get("kind"));
        if ("TRANSFER".equals(filterKind)) {
            where.append(" AND t.kind='TRANSFER_OUT'");
        } else {
            addEquals(where, args, "t.kind=?", filterKind);
        }
        addAccountPublicId(where, args, filters.get("accountId"), context.bookId());
        addPublicId(where, args, "t.category_id", "ledger_category",
                text(filters.get("categoryId")).isBlank() ? filters.get("secondaryCategoryId") : filters.get("categoryId"),
                context.bookId());
        addPublicId(where, args, "c.parent_id", "ledger_category", filters.get("primaryCategoryId"), context.bookId());
        addPublicId(where, args, "t.merchant_id", "ledger_merchant", filters.get("merchantId"), context.bookId());
        addPublicId(where, args, "t.member_id", "ledger_book_member", filters.get("memberId"), context.bookId());
        addPublicId(where, args, "t.project_id", "ledger_project", filters.get("projectId"), context.bookId());
        String note = text(filters.get("note"));
        if (!note.isBlank()) {
            where.append(" AND t.note LIKE ?");
            args.add("%" + note + "%");
        }
        String payee = text(filters.get("payee"));
        if (!payee.isBlank()) {
            where.append(" AND COALESCE(m.name,t.payee) LIKE ?");
            args.add("%" + payee + "%");
        }
        String member = text(filters.get("member"));
        if (!member.isBlank()) {
            where.append(" AND COALESCE(NULLIF(u.nickname,''),NULLIF(u.username,''),t.member_name) LIKE ?");
            args.add("%" + member + "%");
        }
        String project = text(filters.get("project"));
        if (!project.isBlank()) {
            where.append(" AND COALESCE(p.name,t.project_name) LIKE ?");
            args.add("%" + project + "%");
        }
        String q = text(filters.get("q"));
        if (!q.isBlank()) {
            where.append(" AND (t.payee LIKE ? OR t.note LIKE ? OR a.name LIKE ? OR c.name LIKE ? OR m.name LIKE ? OR p.name LIKE ?)");
            for (int i = 0; i < 6; i++) args.add("%" + q + "%");
        }
        String createdBy = text(filters.get("createdBy"));
        if (!createdBy.isBlank()) {
            where.append(" AND t.created_by=?");
            args.add(Long.parseLong(createdBy));
        }
        return where.toString();
    }

    private Map<String, Object> summary(LedgerBookAccess.Context context, Map<String, String> filters) {
        List<Object> args = new ArrayList<>();
        String where = where(context, filters, args, false);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT COALESCE(SUM(CASE WHEN t.kind IN ('INCOME','BORROW_IN','COLLECT_DEBT') THEN t.amount ELSE 0 END),0) income," +
                        "COALESCE(SUM(CASE WHEN t.kind IN ('EXPENSE','LEND_OUT','REPAY_DEBT') THEN t.amount ELSE 0 END),0) expense," +
                        "COUNT(*) count FROM ledger_transaction t " +
                        "LEFT JOIN ledger_account a ON a.id=t.account_id " +
                        "LEFT JOIN ledger_category c ON c.id=t.category_id " +
                        "LEFT JOIN ledger_merchant m ON m.id=t.merchant_id " +
                        "LEFT JOIN ledger_project p ON p.id=t.project_id WHERE " + where,
                args.toArray());
        BigDecimal income = decimal(row.get("income"));
        BigDecimal expense = decimal(row.get("expense"));
        return Map.of(
                "income", income,
                "expense", expense,
                "balance", income.subtract(expense),
                "count", number(row.get("count")));
    }

    private Draft draft(LedgerBookAccess.Context context,
                        Map<String, Object> input,
                        Map<String, Object> fallback) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (fallback != null) value.putAll(fallback);
        if (input != null) value.putAll(input);
        String kind = kind(value.get("kind"));
        long accountId = resources.internalId(context, "ledger_account", required(value, "accountId"), true);
        Long targetAccountId = null;
        if ("TRANSFER".equals(kind)) {
            targetAccountId = resources.internalId(context, "ledger_account",
                    required(value, "targetAccountId"), true);
            if (targetAccountId == accountId) throw new IllegalArgumentException("转出账户与转入账户不能相同");
        }
        Long categoryId = null;
        if ("INCOME".equals(kind) || "EXPENSE".equals(kind)) {
            categoryId = resources.internalId(context, "ledger_category", required(value, "categoryId"), true);
            Map<String, Object> category = jdbc.queryForMap(
                    "SELECT kind,parent_id FROM ledger_category WHERE id=? AND book_id=? AND deleted=FALSE AND hidden=FALSE",
                    categoryId, context.bookId());
            if (category.get("parent_id") == null) throw new IllegalArgumentException("分类必须选择到二级");
            if (!kind.equals(category.get("kind"))) {
                throw new IllegalArgumentException("分类类型与流水类型不匹配");
            }
        }
        Long merchantId = optionalInternal(context, "ledger_merchant", value.get("merchantId"));
        Long memberId = optionalInternal(context, "ledger_book_member", value.get("memberId"));
        Long projectId = optionalInternal(context, "ledger_project", value.get("projectId"));
        BigDecimal amount = positiveAmount(value.get("amount"));
        LocalDate occurredOn = LocalDate.parse(textOr(value.get("occurredOn"), LocalDate.now().toString()));
        String merchantName = merchantId == null ? "" : jdbc.queryForObject(
                "SELECT name FROM ledger_merchant WHERE id=?", String.class, merchantId);
        String memberName = "";
        if (memberId != null) {
            memberName = jdbc.queryForObject(
                    "SELECT CASE WHEN u.nickname='' THEN u.username ELSE u.nickname END " +
                            "FROM ledger_book_member bm JOIN app_user u ON u.id=bm.user_id WHERE bm.id=?",
                    String.class, memberId);
        }
        String projectName = projectId == null ? "" : jdbc.queryForObject(
                "SELECT name FROM ledger_project WHERE id=?", String.class, projectId);
        return new Draft(
                accountId, targetAccountId, categoryId, merchantId, memberId, projectId,
                kind, amount, currency(value.get("currency")), occurredOn,
                merchantName, memberName, projectName, text(value.get("note")),
                textOr(value.get("source"), "manual"));
    }

    private long insert(LedgerBookAccess.Context context,
                        String publicId,
                        Draft draft,
                        String storedKind,
                        String transferGroupId,
                        String opId) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO ledger_transaction(public_id,user_id,book_id,account_id,counterparty_account_id," +
                            "transfer_group_id,category_id,merchant_id,member_id,project_id,kind,amount,currency," +
                            "occurred_on,payee,member_name,project_name,note,source,client_op_id,created_by) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, publicId);
            statement.setLong(2, context.userId());
            statement.setLong(3, context.bookId());
            statement.setLong(4, draft.accountId());
            if (draft.targetAccountId() == null) statement.setNull(5, java.sql.Types.BIGINT);
            else statement.setLong(5, draft.targetAccountId());
            statement.setString(6, transferGroupId);
            setNullableLong(statement, 7, draft.categoryId());
            setNullableLong(statement, 8, draft.merchantId());
            setNullableLong(statement, 9, draft.memberId());
            setNullableLong(statement, 10, draft.projectId());
            statement.setString(11, storedKind);
            statement.setBigDecimal(12, draft.amount());
            statement.setString(13, draft.currency());
            statement.setObject(14, draft.occurredOn());
            statement.setString(15, draft.merchantName());
            statement.setString(16, draft.memberName());
            statement.setString(17, draft.projectName());
            statement.setString(18, draft.note());
            statement.setString(19, draft.source());
            statement.setString(20, opId);
            statement.setLong(21, context.userId());
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    private Map<String, Object> createTransfer(LedgerBookAccess.Context context, Draft draft, String opId) {
        String groupId = UUID.randomUUID().toString();
        String sourcePublicId = UUID.randomUUID().toString();
        String targetPublicId = UUID.randomUUID().toString();
        long sourceId = insert(context, sourcePublicId, draft, "TRANSFER_OUT", groupId, opId);
        Draft targetDraft = new Draft(
                draft.targetAccountId(), draft.accountId(), draft.categoryId(), draft.merchantId(),
                draft.memberId(), draft.projectId(), draft.kind(), draft.amount(), draft.currency(),
                draft.occurredOn(), draft.merchantName(), draft.memberName(), draft.projectName(),
                draft.note(), draft.source());
        long targetId = insert(context, targetPublicId, targetDraft, "TRANSFER_IN", groupId, opId + ":target");
        Map<String, Object> source = getByInternalId(context, sourceId, true);
        Map<String, Object> target = getByInternalId(context, targetId, true);
        appendVersion(context, sourceId, 1, "CREATE", source);
        appendVersion(context, targetId, 1, "CREATE", target);
        resources.appendSync(context, opId, "transaction", sourcePublicId, "UPSERT", source);
        resources.appendSync(context, opId + ":target", "transaction", targetPublicId, "UPSERT", target);
        audit.record(context, "transaction.create", "transaction", sourcePublicId, null, source);
        return source;
    }

    private void updateTransfer(LedgerBookAccess.Context context,
                                long sourceId,
                                Map<String, Object> before,
                                Draft draft) {
        String groupId = String.valueOf(before.get("transferGroupId"));
        List<Long> targetIds = jdbc.queryForList(
                "SELECT id FROM ledger_transaction WHERE transfer_group_id=? AND id<>? AND book_id=? AND deleted=FALSE",
                Long.class, groupId, sourceId, context.bookId());
        if (targetIds.isEmpty()) throw new IllegalStateException("转账对端流水缺失");
        long targetId = targetIds.get(0);
        jdbc.update(
                "UPDATE ledger_transaction SET account_id=?,counterparty_account_id=?,category_id=?,merchant_id=?," +
                        "member_id=?,project_id=?,amount=?,currency=?,occurred_on=?,payee=?,member_name=?," +
                        "project_name=?,note=?,revision=revision+1 WHERE id=? AND book_id=? AND deleted=FALSE",
                draft.accountId(), draft.targetAccountId(), draft.categoryId(), nullable(draft.merchantId()),
                nullable(draft.memberId()), nullable(draft.projectId()), draft.amount(), draft.currency(),
                draft.occurredOn(), draft.merchantName(), draft.memberName(), draft.projectName(), draft.note(),
                sourceId, context.bookId());
        jdbc.update(
                "UPDATE ledger_transaction SET account_id=?,counterparty_account_id=?,category_id=?,merchant_id=?," +
                        "member_id=?,project_id=?,amount=?,currency=?,occurred_on=?,payee=?,member_name=?," +
                        "project_name=?,note=?,revision=revision+1 WHERE id=? AND book_id=? AND deleted=FALSE",
                draft.targetAccountId(), draft.accountId(), draft.categoryId(), nullable(draft.merchantId()),
                nullable(draft.memberId()), nullable(draft.projectId()), draft.amount(), draft.currency(),
                draft.occurredOn(), draft.merchantName(), draft.memberName(), draft.projectName(), draft.note(),
                targetId, context.bookId());
        Map<String, Object> target = getByInternalId(context, targetId, true);
        appendVersion(context, targetId, number(target.get("revision")), "UPDATE", target);
        resources.appendSync(context, UUID.randomUUID().toString(),
                "transaction", String.valueOf(target.get("id")), "UPSERT", target);
    }

    private void appendVersion(LedgerBookAccess.Context context,
                               long internalId,
                               long revision,
                               String operation,
                               Object payload) {
        jdbc.update(
                "INSERT INTO ledger_transaction_version(transaction_id,book_id,revision,operation,payload_json,actor_user_id) " +
                        "VALUES(?,?,?,?,?,?)",
                internalId, context.bookId(), revision, operation, json(payload), context.userId());
    }

    private List<Long> transactionGroupIds(LedgerBookAccess.Context context, Map<String, Object> transaction) {
        if ("TRANSFER".equals(transaction.get("kind")) && transaction.get("transferGroupId") != null) {
            return jdbc.queryForList(
                    "SELECT id FROM ledger_transaction WHERE transfer_group_id=? AND book_id=?",
                    Long.class, transaction.get("transferGroupId"), context.bookId());
        }
        return List.of(number(transaction.get("internalId")));
    }

    String matchAccount(LedgerBookAccess.Context context, String name) {
        List<String> ids = jdbc.queryForList(
                "SELECT public_id FROM ledger_account WHERE book_id=? AND name=? AND deleted=FALSE ORDER BY id LIMIT 1",
                String.class, context.bookId(), name);
        if (!ids.isEmpty()) return ids.get(0);
        access.require(context, "RESOURCE_MANAGE");
        return String.valueOf(resources.createAccount(
                context.bookPublicId(), Map.of("name", name, "accountType", "other"), null).get("id"));
    }

    String matchCategory(LedgerBookAccess.Context context,
                         String parentName,
                         String childName,
                         String transactionKind) {
        String categoryKind = INCOME_KINDS.contains(kind(transactionKind)) ? "INCOME" : "EXPENSE";
        List<String> ids = jdbc.queryForList(
                "SELECT c.public_id FROM ledger_category c LEFT JOIN ledger_category p ON p.id=c.parent_id " +
                        "WHERE c.book_id=? AND c.name=? AND c.kind=? AND c.deleted=FALSE " +
                        "AND (?='' OR p.name=?) ORDER BY c.id LIMIT 1",
                String.class, context.bookId(), childName, categoryKind, parentName, parentName);
        if (!ids.isEmpty()) return ids.get(0);
        access.require(context, "RESOURCE_MANAGE");
        String effectiveParent = parentName.isBlank() ? "其他" : parentName;
        List<String> parents = jdbc.queryForList(
                "SELECT public_id FROM ledger_category WHERE book_id=? AND name=? AND kind=? " +
                        "AND parent_id IS NULL AND deleted=FALSE ORDER BY id LIMIT 1",
                String.class, context.bookId(), effectiveParent, categoryKind);
        String parentId = parents.isEmpty()
                ? String.valueOf(resources.createCategory(context.bookPublicId(),
                Map.of("name", effectiveParent, "kind", categoryKind), null).get("id"))
                : parents.get(0);
        return String.valueOf(resources.createCategory(context.bookPublicId(),
                Map.of("name", childName.isBlank() ? "其他" : childName,
                        "kind", categoryKind, "parentId", parentId), null).get("id"));
    }

    String matchNamed(LedgerBookAccess.Context context, String type, String name) {
        String table = "merchant".equals(type) ? "ledger_merchant" : "ledger_project";
        List<String> ids = jdbc.queryForList(
                "SELECT public_id FROM " + table + " WHERE book_id=? AND name=? AND deleted=FALSE ORDER BY id LIMIT 1",
                String.class, context.bookId(), name);
        if (!ids.isEmpty()) return ids.get(0);
        access.require(context, "RESOURCE_MANAGE");
        return String.valueOf(resources.createNamedResource(
                context.bookPublicId(), type, Map.of("name", name), null).get("id"));
    }

    String matchMember(LedgerBookAccess.Context context, String usernameOrNickname) {
        String value = text(usernameOrNickname);
        if (value.isBlank()) return "";
        List<String> ids = jdbc.queryForList(
                "SELECT m.public_id FROM ledger_book_member m JOIN app_user u ON u.id=m.user_id " +
                        "WHERE m.book_id=? AND m.deleted=FALSE AND (u.username=? OR u.nickname=?) ORDER BY m.id LIMIT 1",
                String.class, context.bookId(), value, value);
        if (ids.isEmpty()) throw new IllegalArgumentException("成员不在当前账本：" + value);
        return ids.get(0);
    }

    private void addDate(StringBuilder where, List<Object> args, String expression, String value) {
        String text = text(value);
        if (text.isBlank()) return;
        LocalDate.parse(text);
        where.append(" AND ").append(expression);
        args.add(text);
    }

    private void addEquals(StringBuilder where, List<Object> args, String expression, String value) {
        if (value == null || value.isBlank()) return;
        where.append(" AND ").append(expression);
        args.add(value);
    }

    private void addPublicId(StringBuilder where,
                             List<Object> args,
                             String column,
                             String table,
                             String publicId,
                             long bookId) {
        String id = text(publicId);
        if (id.isBlank()) return;
        where.append(" AND ").append(column).append("=(SELECT id FROM ").append(table)
                .append(" WHERE public_id=? AND book_id=?)");
        args.add(id);
        args.add(bookId);
    }

    private void addAccountPublicId(StringBuilder where,
                                    List<Object> args,
                                    String publicId,
                                    long bookId) {
        String id = text(publicId);
        if (id.isBlank()) return;
        where.append(" AND (t.account_id=(SELECT id FROM ledger_account WHERE public_id=? AND book_id=?)")
                .append(" OR t.counterparty_account_id=(SELECT id FROM ledger_account WHERE public_id=? AND book_id=?))");
        args.add(id);
        args.add(bookId);
        args.add(id);
        args.add(bookId);
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("internalId", number(row.get("internal_id")));
        result.put("id", row.get("public_id"));
        result.put("accountId", row.get("account_public_id"));
        result.put("accountName", row.get("account_name"));
        result.put("accountIcon", row.get("account_icon"));
        result.put("targetAccountId", row.get("target_account_public_id"));
        result.put("targetAccountName", row.get("target_account_name"));
        result.put("targetAccountIcon", row.get("target_account_icon"));
        result.put("transferGroupId", row.get("transfer_group_id"));
        result.put("categoryId", row.get("category_public_id"));
        result.put("categoryName", row.get("category_name"));
        result.put("categoryIcon", row.get("category_icon"));
        result.put("categoryColor", row.get("category_color"));
        result.put("parentCategoryId", row.get("parent_category_public_id"));
        result.put("parentCategoryName", row.get("parent_category_name"));
        result.put("parentCategoryIcon", row.get("parent_category_icon"));
        result.put("parentCategoryColor", row.get("parent_category_color"));
        result.put("merchantId", row.get("merchant_public_id"));
        result.put("merchantName", row.get("merchant_name"));
        result.put("merchantIcon", row.get("merchant_icon"));
        result.put("payee", row.get("merchant_name") == null ? row.get("payee") : row.get("merchant_name"));
        result.put("memberId", row.get("member_public_id"));
        result.put("memberIcon", row.get("member_icon"));
        result.put("memberUsername", row.get("member_username"));
        String memberName = text(row.get("member_nickname"));
        result.put("member", memberName.isBlank() ? row.get("member_username") : memberName);
        result.put("projectId", row.get("project_public_id"));
        result.put("projectIcon", row.get("project_icon"));
        result.put("projectColor", row.get("project_color"));
        result.put("projectName", row.get("project_name") == null
                ? row.get("legacy_project_name") : row.get("project_name"));
        result.put("project", result.get("projectName"));
        String storedKind = String.valueOf(row.get("kind"));
        result.put("storedKind", storedKind);
        result.put("kind", storedKind.startsWith("TRANSFER_") ? "TRANSFER" : storedKind);
        result.put("amount", row.get("amount"));
        result.put("currency", row.get("currency"));
        result.put("occurredOn", String.valueOf(row.get("occurred_on")));
        result.put("note", row.get("note"));
        result.put("source", row.get("source"));
        result.put("clientOpId", row.get("client_op_id"));
        result.put("revision", number(row.get("revision")));
        result.put("deleted", Boolean.TRUE.equals(row.get("deleted")));
        result.put("deletedAt", row.get("deleted_at"));
        result.put("createdBy", number(row.get("created_by")));
        result.put("createdAt", row.get("created_at"));
        result.put("updatedAt", row.get("updated_at"));
        return result;
    }

    private Map<String, Object> tombstone(String publicId, long revision) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", publicId);
        result.put("revision", revision);
        result.put("deleted", true);
        result.put("deletedAt", java.time.Instant.now().toString());
        return result;
    }

    private Long optionalInternal(LedgerBookAccess.Context context, String table, Object value) {
        String publicId = text(value);
        if (publicId.isBlank()) return null;
        long id = resources.internalId(context, table, publicId, true);
        if ("ledger_book_member".equals(table)) {
            long deleted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_book_member WHERE id=? AND deleted=FALSE", Long.class, id);
            if (deleted == 0) throw new IllegalArgumentException("成员不存在");
        } else {
            String hiddenColumn = Set.of("ledger_account", "ledger_category", "ledger_merchant", "ledger_project")
                    .contains(table) ? " AND hidden=FALSE" : "";
            long visible = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE id=? AND deleted=FALSE" + hiddenColumn,
                    Long.class, id);
            if (visible == 0) throw new IllegalArgumentException("所选资源已隐藏或删除");
        }
        return id;
    }

    private String opId(Map<String, Object> input, String header) {
        String value = text(header);
        if (value.isBlank()) value = text(input == null ? null : input.get("clientOpId"));
        return value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private String requestedPublicId(Map<String, Object> input) {
        String value = text(input == null ? null : input.get("id"));
        if (value.isBlank()) return UUID.randomUUID().toString();
        try {
            UUID.fromString(value);
            return value;
        } catch (Exception exception) {
            throw new IllegalArgumentException("id 必须是 UUID");
        }
    }

    private String required(Map<String, Object> input, String key) {
        String value = text(input == null ? null : input.get(key));
        if (value.isBlank()) throw new IllegalArgumentException(key + " 必填");
        return value;
    }

    private String kind(Object value) {
        String kind = text(value).toUpperCase(Locale.ROOT);
        kind = switch (kind) {
            case "收入" -> "INCOME";
            case "支出" -> "EXPENSE";
            case "转账", "TRANSFER_IN", "TRANSFER_OUT" -> "TRANSFER";
            case "借入" -> "BORROW_IN";
            case "借出" -> "LEND_OUT";
            case "收债" -> "COLLECT_DEBT";
            case "还债" -> "REPAY_DEBT";
            default -> kind;
        };
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("流水类型不正确");
        return kind;
    }

    private String normalizedFilterKind(String value) {
        String raw = text(value);
        return raw.isBlank() ? "" : kind(raw);
    }

    private String currency(Object value) {
        String currency = textOr(value, "CNY").toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency 必须是三位货币代码");
        return currency;
    }

    private BigDecimal positiveAmount(Object value) {
        BigDecimal result = decimal(value).abs().setScale(2, java.math.RoundingMode.HALF_UP);
        if (result.signum() <= 0) throw new IllegalArgumentException("金额必须大于 0");
        return result;
    }

    private BigDecimal decimal(Object value) {
        try {
            return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
        } catch (Exception exception) {
            throw new IllegalArgumentException("金额格式不正确");
        }
    }

    private int positiveInt(String value, int fallback, int min, int max) {
        try {
            return Math.min(max, Math.max(min, Integer.parseInt(textOr(value, String.valueOf(fallback)))));
        } catch (Exception exception) {
            return fallback;
        }
    }

    private String sortColumn(String value) {
        return switch (text(value)) {
            case "amount" -> "t.amount";
            case "kind" -> "t.kind";
            case "category" -> "c.name";
            case "account" -> "a.name";
            case "merchant" -> "m.name";
            case "project" -> "p.name";
            default -> "t.occurred_on";
        };
    }

    private Object nullable(Long value) {
        return value;
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String textOr(Object value, String fallback) {
        String result = text(value);
        return result.isBlank() ? fallback : result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private void requireRevision(String ifMatch, long revision) {
        if (ifMatch == null || ifMatch.isBlank()) throw new IllegalArgumentException("If-Match 必填");
        try {
            long expected = Long.parseLong(ifMatch.replace("W/", "").replace("\"", ""));
            if (expected != revision) throw new ConflictException("流水版本已变化", revision);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match 必须是 revision");
        }
    }

    private void setNullableLong(java.sql.PreparedStatement statement, int index, Long value)
            throws java.sql.SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT);
        else statement.setLong(index, value);
    }

    private Object parse(Object value) {
        try {
            return mapper.readValue(String.valueOf(value), new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private record Draft(long accountId,
                         Long targetAccountId,
                         Long categoryId,
                         Long merchantId,
                         Long memberId,
                         Long projectId,
                         String kind,
                         BigDecimal amount,
                         String currency,
                         LocalDate occurredOn,
                         String merchantName,
                         String memberName,
                         String projectName,
                         String note,
                         String source) {
    }
}
