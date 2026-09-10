package com.salarytracker.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LedgerService {
    private static final Pattern MONEY = Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern DATE = Pattern.compile("((?:19|20)\\d{2}[-/]\\d{1,2}[-/]\\d{1,2})");
    private static final String BALANCE_DELTA = "CASE WHEN t.kind IN ('INCOME','BORROW_IN','COLLECT_DEBT','TRANSFER_IN') THEN t.amount WHEN t.kind IN ('EXPENSE','LEND_OUT','REPAY_DEBT','TRANSFER_OUT') THEN -t.amount ELSE 0 END";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserResolver currentUser;

    public LedgerService(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserResolver currentUser) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    public List<Map<String, Object>> accounts() {
        long user = currentUser.id();
        return jdbc.queryForList("SELECT a.id, a.name, a.account_type, a.currency, a.opening_balance, a.revision, a.created_at, " +
                "a.opening_balance + COALESCE(SUM(" + BALANCE_DELTA + "), 0) balance " +
                "FROM ledger_account a LEFT JOIN ledger_transaction t ON t.account_id = a.id AND t.user_id = a.user_id AND t.deleted = FALSE " +
                "WHERE a.user_id = ? AND a.deleted = FALSE GROUP BY a.id ORDER BY a.created_at, a.id", user)
                .stream().map(this::accountView).toList();
    }

    @Transactional
    public Map<String, Object> createAccount(Map<String, Object> input) {
        long user = currentUser.id();
        String name = required(input, "name");
        String type = normalizeType(input == null ? null : input.get("accountType"), "cash");
        String currency = String.valueOf(input == null ? "CNY" : input.getOrDefault("currency", "CNY")).toUpperCase(Locale.ROOT);
        BigDecimal opening = amount(input == null ? null : input.getOrDefault("openingBalance", 0));
        jdbc.update("INSERT INTO ledger_account (user_id, name, account_type, currency, opening_balance) VALUES (?, ?, ?, ?, ?)", user, name, type, currency, opening);
        long id = jdbc.queryForObject("SELECT id FROM ledger_account WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, user);
        return account(id, user);
    }

    @Transactional
    public Map<String, Object> updateAccount(long id, Map<String, Object> input, String ifMatch) {
        long user = currentUser.id();
        Map<String, Object> current = account(id, user);
        checkRevision(ifMatch, number(current.get("revision")));
        String name = String.valueOf(input == null ? current.get("name") : input.getOrDefault("name", current.get("name")));
        String type = normalizeType(input == null ? current.get("accountType") : input.getOrDefault("accountType", current.get("accountType")), "cash");
        String currency = String.valueOf(input == null ? current.get("currency") : input.getOrDefault("currency", current.get("currency"))).toUpperCase(Locale.ROOT);
        BigDecimal opening = amount(input == null ? current.get("openingBalance") : input.getOrDefault("openingBalance", current.get("openingBalance")));
        jdbc.update("UPDATE ledger_account SET name = ?, account_type = ?, currency = ?, opening_balance = ?, revision = revision + 1 WHERE id = ? AND user_id = ? AND deleted = FALSE", name, type, currency, opening, id, user);
        return account(id, user);
    }

    @Transactional
    public Map<String, Object> deleteAccount(long id, String ifMatch) {
        long user = currentUser.id();
        Map<String, Object> current = account(id, user);
        checkRevision(ifMatch, number(current.get("revision")));
        jdbc.update("UPDATE ledger_account SET deleted = TRUE, revision = revision + 1 WHERE id = ? AND user_id = ?", id, user);
        return Map.of("id", id, "deleted", true, "revision", number(current.get("revision")) + 1);
    }

    public List<Map<String, Object>> categories() {
        long user = currentUser.id();
        return jdbc.queryForList("SELECT id, name, kind, parent_id, color, revision FROM ledger_category WHERE user_id = ? AND deleted = FALSE ORDER BY kind, name", user)
                .stream().map(row -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", number(row.get("id"))); view.put("name", row.get("name")); view.put("kind", row.get("kind"));
                    view.put("parentId", row.get("parent_id")); view.put("color", row.get("color")); view.put("revision", number(row.get("revision")));
                    return view;
                }).toList();
    }

    @Transactional
    public Map<String, Object> createCategory(Map<String, Object> input) {
        long user = currentUser.id();
        String name = required(input, "name");
        String kind = normalizeCategoryKind(input == null ? null : input.get("kind"));
        Long parent = longValue(input == null ? null : input.get("parentId"));
        if (parent != null) ensureCategory(parent, user);
        String color = String.valueOf(input == null ? "#0f5132" : input.getOrDefault("color", "#0f5132"));
        jdbc.update("INSERT INTO ledger_category (user_id, name, kind, parent_id, color) VALUES (?, ?, ?, ?, ?)", user, name, kind, parent, color);
        long id = jdbc.queryForObject("SELECT id FROM ledger_category WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, user);
        return category(id, user);
    }

    @Transactional
    public Map<String, Object> updateCategory(long id, Map<String, Object> input, String ifMatch) {
        long user = currentUser.id();
        Map<String, Object> current = category(id, user);
        checkRevision(ifMatch, number(current.get("revision")));
        String name = String.valueOf(input == null ? current.get("name") : input.getOrDefault("name", current.get("name")));
        String kind = normalizeCategoryKind(input == null ? current.get("kind") : input.getOrDefault("kind", current.get("kind")));
        Long parent = longValue(input == null ? current.get("parentId") : input.getOrDefault("parentId", current.get("parentId")));
        if (parent != null) ensureCategory(parent, user);
        String color = String.valueOf(input == null ? current.get("color") : input.getOrDefault("color", current.get("color")));
        jdbc.update("UPDATE ledger_category SET name = ?, kind = ?, parent_id = ?, color = ?, revision = revision + 1 WHERE id = ? AND user_id = ? AND deleted = FALSE", name, kind, parent, color, id, user);
        return category(id, user);
    }

    @Transactional
    public Map<String, Object> deleteCategory(long id, String ifMatch) {
        long user = currentUser.id();
        Map<String, Object> current = category(id, user);
        checkRevision(ifMatch, number(current.get("revision")));
        jdbc.update("UPDATE ledger_category SET deleted = TRUE, revision = revision + 1 WHERE id = ? AND user_id = ?", id, user);
        return Map.of("id", id, "deleted", true, "revision", number(current.get("revision")) + 1);
    }

    public List<Map<String, Object>> transactions(String from, String to, int limit) {
        long user = currentUser.id();
        StringBuilder sql = new StringBuilder("SELECT t.id, t.account_id, a.name account_name, t.counterparty_account_id, counterparty.name counterparty_account_name, t.transfer_group_id, t.category_id, c.name category_name, parent.name parent_category_name, t.kind, t.amount, t.currency, t.occurred_on, t.payee, t.member_name, t.project_name, t.note, t.source, t.recurring_id, t.client_op_id, t.revision, t.created_at FROM ledger_transaction t JOIN ledger_account a ON a.id = t.account_id LEFT JOIN ledger_account counterparty ON counterparty.id = t.counterparty_account_id LEFT JOIN ledger_category c ON c.id = t.category_id LEFT JOIN ledger_category parent ON parent.id = c.parent_id WHERE t.user_id = ? AND t.deleted = FALSE AND t.kind <> 'TRANSFER_IN'");
        List<Object> args = new ArrayList<>(); args.add(user);
        if (from != null && !from.isBlank()) { LocalDate.parse(from); sql.append(" AND t.occurred_on >= ?"); args.add(from); }
        if (to != null && !to.isBlank()) { LocalDate.parse(to); sql.append(" AND t.occurred_on <= ?"); args.add(to); }
        sql.append(" ORDER BY t.occurred_on DESC, t.id DESC LIMIT ?"); args.add(Math.min(Math.max(limit, 1), 2000));
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(this::transactionView).toList();
    }

    @Transactional
    public Map<String, Object> createTransaction(Map<String, Object> input, String idempotencyKey) {
        long user = currentUser.id();
        String opId = idempotencyKey == null || idempotencyKey.isBlank() ? String.valueOf(input == null ? "" : input.getOrDefault("clientOpId", "")) : idempotencyKey.trim();
        if (!opId.isBlank()) {
            List<Map<String, Object>> previous = jdbc.queryForList("SELECT id FROM ledger_transaction WHERE user_id = ? AND client_op_id = ?", user, opId);
            if (!previous.isEmpty()) return transaction(number(previous.get(0).get("id")), user);
        }
        Map<String, Object> value = input == null ? Map.of() : input;
        long account = requiredLong(value, "accountId"); ensureAccount(account, user);
        String kind = normalizeKind(value.get("kind"));
        if ("TRANSFER".equals(kind)) return createTransfer(value, user, account, opId);
        long category = requiredSecondaryCategory(value, user, kind);
        BigDecimal amount = amount(value.get("amount")); if (amount.signum() <= 0) throw new IllegalArgumentException("amount 必须大于 0");
        String date = String.valueOf(value.getOrDefault("occurredOn", value.getOrDefault("date", LocalDate.now()))); LocalDate.parse(date);
        String currency = String.valueOf(value.getOrDefault("currency", "CNY")).toUpperCase(Locale.ROOT);
        String payee = text(value.get("payee")); String member = text(value.get("member")); String project = text(value.get("project")); String note = text(value.get("note"));
        String source = String.valueOf(value.getOrDefault("source", "manual")); Long recurring = longValue(value.get("recurringId"));
        jdbc.update("INSERT INTO ledger_transaction (user_id, account_id, category_id, kind, amount, currency, occurred_on, payee, member_name, project_name, note, source, recurring_id, client_op_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", user, account, category, kind, amount, currency, date, payee, member, project, note, source, recurring, opId.isBlank() ? null : opId);
        long id = jdbc.queryForObject("SELECT id FROM ledger_transaction WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, user);
        Map<String, Object> result = transaction(id, user); appendHistory(id, user, "CREATE", result); appendSync(user, opId.isBlank() ? UUID.randomUUID().toString() : opId, "transaction", id, "UPSERT", result);
        return result;
    }

    @Transactional
    public Map<String, Object> updateTransaction(long id, Map<String, Object> input, String ifMatch) {
        long user = currentUser.id(); Map<String, Object> current = transaction(id, user); checkRevision(ifMatch, number(current.get("revision")));
        Map<String, Object> value = new LinkedHashMap<>(current); if (input != null) value.putAll(input);
        String kind = normalizeKind(value.get("kind"));
        if ("TRANSFER".equals(current.get("kind")) || "TRANSFER".equals(kind)) {
            if (!"TRANSFER".equals(current.get("kind")) || !"TRANSFER".equals(kind)) throw new IllegalArgumentException("转账与其他流水类型不能互相转换");
            return updateTransfer(id, value, current, user);
        }
        long account = requiredLong(value, "accountId"); ensureAccount(account, user); long category = requiredSecondaryCategory(value, user, kind);
        BigDecimal amount = amount(value.get("amount")); if (amount.signum() <= 0) throw new IllegalArgumentException("amount 必须大于 0");
        String date = String.valueOf(value.getOrDefault("occurredOn", value.get("date"))); LocalDate.parse(date);
        jdbc.update("UPDATE ledger_transaction SET account_id = ?, counterparty_account_id = NULL, transfer_group_id = NULL, category_id = ?, kind = ?, amount = ?, currency = ?, occurred_on = ?, payee = ?, member_name = ?, project_name = ?, note = ?, revision = revision + 1 WHERE id = ? AND user_id = ? AND deleted = FALSE", account, category, kind, amount, String.valueOf(value.getOrDefault("currency", "CNY")), date, text(value.get("payee")), text(value.get("member")), text(value.get("project")), text(value.get("note")), id, user);
        Map<String, Object> result = transaction(id, user); appendHistory(id, user, "UPDATE", result); appendSync(user, UUID.randomUUID().toString(), "transaction", id, "UPSERT", result); return result;
    }

    @Transactional
    public Map<String, Object> deleteTransaction(long id, String ifMatch) {
        long user = currentUser.id(); Map<String, Object> current = transaction(id, user); checkRevision(ifMatch, number(current.get("revision")));
        List<Long> deletedIds;
        if ("TRANSFER".equals(current.get("kind")) && current.get("transferGroupId") != null) {
            deletedIds = jdbc.query("SELECT id FROM ledger_transaction WHERE transfer_group_id=? AND user_id=? AND deleted=FALSE", (result, row) -> result.getLong("id"), current.get("transferGroupId"), user);
            jdbc.update("UPDATE ledger_transaction SET deleted = TRUE, revision = revision + 1 WHERE transfer_group_id = ? AND user_id = ?", current.get("transferGroupId"), user);
        } else {
            deletedIds = List.of(id);
            jdbc.update("UPDATE ledger_transaction SET deleted = TRUE, revision = revision + 1 WHERE id = ? AND user_id = ?", id, user);
        }
        Map<String, Object> result = Map.of("id", id, "deleted", true, "revision", number(current.get("revision")) + 1);
        for(long deletedId:deletedIds)appendHistory(deletedId,user,"DELETE",Map.of("id",deletedId,"deleted",true));appendSync(user, UUID.randomUUID().toString(), "transaction", id, "DELETE", result); return result;
    }

    private Map<String, Object> createTransfer(Map<String, Object> value, long user, long account, String opId) {
        long targetAccount = requiredLong(value, "targetAccountId"); ensureAccount(targetAccount, user);
        if (account == targetAccount) throw new IllegalArgumentException("转出账户和转入账户不能相同");
        long category = requiredSecondaryCategory(value, user, "TRANSFER");
        BigDecimal valueAmount = amount(value.get("amount")); if (valueAmount.signum() <= 0) throw new IllegalArgumentException("amount 必须大于 0");
        String date = String.valueOf(value.getOrDefault("occurredOn", value.getOrDefault("date", LocalDate.now()))); LocalDate.parse(date);
        String currency = String.valueOf(value.getOrDefault("currency", "CNY")).toUpperCase(Locale.ROOT);
        String payee = text(value.get("payee")); String member = text(value.get("member")); String project = text(value.get("project")); String note = text(value.get("note"));
        String source = String.valueOf(value.getOrDefault("source", "manual"));
        String groupId = UUID.randomUUID().toString();
        String sourceOpId = opId.isBlank() ? "transfer-" + groupId : opId;
        String targetOpId = transferTargetOpId(sourceOpId);
        long sourceId = insertTransferEntry(user, account, targetAccount, groupId, category, "TRANSFER_OUT", valueAmount, currency, date, payee, member, project, note, source, sourceOpId);
        long targetId = insertTransferEntry(user, targetAccount, account, groupId, category, "TRANSFER_IN", valueAmount, currency, date, payee, member, project, note, source, targetOpId);
        Map<String, Object> result = transaction(sourceId, user); Map<String, Object> targetResult = transaction(targetId, user);
        appendHistory(sourceId, user, "CREATE", result); appendHistory(targetId, user, "CREATE", targetResult);
        appendSync(user, sourceOpId, "transaction", sourceId, "UPSERT", result);
        return result;
    }

    private Map<String, Object> updateTransfer(long id, Map<String, Object> value, Map<String, Object> current, long user) {
        long account = requiredLong(value, "accountId"); long targetAccount = requiredLong(value, "targetAccountId");
        ensureAccount(account, user); ensureAccount(targetAccount, user);
        if (account == targetAccount) throw new IllegalArgumentException("转出账户和转入账户不能相同");
        long category = requiredSecondaryCategory(value, user, "TRANSFER");
        BigDecimal valueAmount = amount(value.get("amount")); if (valueAmount.signum() <= 0) throw new IllegalArgumentException("amount 必须大于 0");
        String date = String.valueOf(value.getOrDefault("occurredOn", value.get("date"))); LocalDate.parse(date);
        String groupId = String.valueOf(current.get("transferGroupId"));
        Long targetEntryId = jdbc.query("SELECT id FROM ledger_transaction WHERE transfer_group_id=? AND id<>? AND user_id=? AND deleted=FALSE", (result, row) -> result.getLong("id"), groupId, id, user).stream().findFirst().orElse(null);
        String currency = String.valueOf(value.getOrDefault("currency", "CNY")).toUpperCase(Locale.ROOT);
        String payee = text(value.get("payee")); String member = text(value.get("member")); String project = text(value.get("project")); String note = text(value.get("note"));
        jdbc.update("UPDATE ledger_transaction SET account_id=?, counterparty_account_id=?, category_id=?, kind='TRANSFER_OUT', amount=?, currency=?, occurred_on=?, payee=?, member_name=?, project_name=?, note=?, revision=revision+1 WHERE id=? AND user_id=? AND deleted=FALSE", account, targetAccount, category, valueAmount, currency, date, payee, member, project, note, id, user);
        jdbc.update("UPDATE ledger_transaction SET account_id=?, counterparty_account_id=?, category_id=?, kind='TRANSFER_IN', amount=?, currency=?, occurred_on=?, payee=?, member_name=?, project_name=?, note=?, revision=revision+1 WHERE transfer_group_id=? AND id<>? AND user_id=? AND deleted=FALSE", targetAccount, account, category, valueAmount, currency, date, payee, member, project, note, groupId, id, user);
        Map<String, Object> result = transaction(id, user); appendHistory(id, user, "UPDATE", result); if(targetEntryId!=null)appendHistory(targetEntryId,user,"UPDATE",transaction(targetEntryId,user));appendSync(user, UUID.randomUUID().toString(), "transaction", id, "UPSERT", result); return result;
    }

    private long insertTransferEntry(long user, long account, long counterparty, String groupId, long category, String kind, BigDecimal valueAmount, String currency, String date, String payee, String member, String project, String note, String source, String opId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO ledger_transaction (user_id, account_id, counterparty_account_id, transfer_group_id, category_id, kind, amount, currency, occurred_on, payee, member_name, project_name, note, source, client_op_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, user); statement.setLong(2, account); statement.setLong(3, counterparty); statement.setString(4, groupId); statement.setLong(5, category); statement.setString(6, kind); statement.setBigDecimal(7, valueAmount); statement.setString(8, currency); statement.setString(9, date); statement.setString(10, payee); statement.setString(11, member); statement.setString(12, project); statement.setString(13, note); statement.setString(14, source); statement.setString(15, opId);
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) throw new IllegalStateException("转账流水创建失败");
        return keyHolder.getKey().longValue();
    }

    private String transferTargetOpId(String sourceOpId) {
        return (sourceOpId.length() > 156 ? sourceOpId.substring(0, 156) : sourceOpId) + ":in";
    }

    public Map<String, Object> reports(String from, String to) {
        long user = currentUser.id(); String start = from == null || from.isBlank() ? YearMonth.now().atDay(1).toString() : from; String end = to == null || to.isBlank() ? LocalDate.now().toString() : to; LocalDate.parse(start); LocalDate.parse(end);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("from", start); report.put("to", end);
        report.put("summary", jdbc.queryForMap("SELECT COALESCE(SUM(CASE WHEN kind='INCOME' THEN amount ELSE 0 END),0) income, COALESCE(SUM(CASE WHEN kind='EXPENSE' THEN amount ELSE 0 END),0) expense, COUNT(*) count FROM ledger_transaction WHERE user_id = ? AND deleted = FALSE AND occurred_on BETWEEN ? AND ?", user, start, end));
        report.put("trend", jdbc.queryForList("SELECT DATE_FORMAT(occurred_on, '%Y-%m-%d') date, COALESCE(SUM(CASE WHEN kind='INCOME' THEN amount ELSE 0 END),0) income, COALESCE(SUM(CASE WHEN kind='EXPENSE' THEN amount ELSE 0 END),0) expense FROM ledger_transaction WHERE user_id = ? AND deleted = FALSE AND occurred_on BETWEEN ? AND ? GROUP BY occurred_on ORDER BY occurred_on", user, start, end));
        report.put("categories", jdbc.queryForList("SELECT c.id category_id, c.parent_id, COALESCE(c.name,'未分类') category, COALESCE(parent.name, c.name, '未分类') parent_category, t.kind, SUM(t.amount) amount FROM ledger_transaction t LEFT JOIN ledger_category c ON c.id=t.category_id LEFT JOIN ledger_category parent ON parent.id=c.parent_id AND parent.user_id=t.user_id WHERE t.user_id = ? AND t.deleted = FALSE AND t.occurred_on BETWEEN ? AND ? GROUP BY c.id, c.parent_id, c.name, parent.name, t.kind ORDER BY amount DESC", user, start, end));
        report.put("monthly", jdbc.queryForList("SELECT DATE_FORMAT(occurred_on, '%Y-%m') month, COALESCE(SUM(CASE WHEN kind='INCOME' THEN amount ELSE 0 END),0) income, COALESCE(SUM(CASE WHEN kind='EXPENSE' THEN amount ELSE 0 END),0) expense FROM ledger_transaction WHERE user_id = ? AND deleted = FALSE AND occurred_on BETWEEN ? AND ? GROUP BY DATE_FORMAT(occurred_on, '%Y-%m') ORDER BY month", user, start, end));
        String month = start.length() >= 7 ? start.substring(0, 7) : YearMonth.now().toString();
        List<Map<String, Object>> budgetRows = jdbc.queryForList("SELECT b.id, b.category_id, c.name category, b.month_key, b.amount budget, COALESCE(SUM(CASE WHEN t.kind='EXPENSE' THEN t.amount ELSE 0 END),0) spent FROM ledger_budget b JOIN ledger_category c ON c.id=b.category_id LEFT JOIN ledger_transaction t ON t.category_id=b.category_id AND t.user_id=b.user_id AND t.deleted=FALSE AND DATE_FORMAT(t.occurred_on, '%Y-%m')=b.month_key WHERE b.user_id=? AND b.month_key=? AND b.deleted=FALSE GROUP BY b.id, c.name", user, month);
        report.put("budgets", budgetRows);
        BigDecimal budgetTotal = budgetRows.stream().map(row -> decimal(row.get("budget"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spentTotal = budgetRows.stream().map(row -> decimal(row.get("spent"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("budgetSummary", Map.of("budget", budgetTotal, "spent", spentTotal));
        return report;
    }

    public List<Map<String, Object>> budgets(String month) {
        String monthKey = month == null || month.isBlank() ? YearMonth.now().toString() : month;
        YearMonth.parse(monthKey);
        long user = currentUser.id();
        return jdbc.queryForList("SELECT b.id, b.category_id, c.name category, b.month_key, b.amount budget, COALESCE((SELECT SUM(t.amount) FROM ledger_transaction t WHERE t.user_id=b.user_id AND t.category_id=b.category_id AND t.kind='EXPENSE' AND t.deleted=FALSE AND DATE_FORMAT(t.occurred_on, '%Y-%m')=b.month_key),0) spent, b.revision FROM ledger_budget b JOIN ledger_category c ON c.id=b.category_id WHERE b.user_id=? AND b.month_key=? AND b.deleted=FALSE ORDER BY c.name", user, monthKey);
    }

    @Transactional
    public Map<String, Object> createBudget(Map<String, Object> input) {
        long user = currentUser.id(); long category = requiredLong(input, "categoryId"); ensureCategory(category, user);
        String month = String.valueOf(input.getOrDefault("monthKey", YearMonth.now())); YearMonth.parse(month); BigDecimal amount = amount(input.get("amount"));
        jdbc.update("INSERT INTO ledger_budget(user_id,category_id,month_key,amount) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE amount=VALUES(amount), deleted=FALSE, revision=revision+1", user, category, month, amount);
        return budgets(month).stream().filter(row -> number(row.get("category_id")) == category).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String, Object> deleteBudget(long id) {
        long user = currentUser.id();
        if (jdbc.update("UPDATE ledger_budget SET deleted=TRUE, revision=revision+1 WHERE id=? AND user_id=? AND deleted=FALSE", id, user) == 0) throw new IllegalArgumentException("预算不存在");
        return Map.of("id", id, "deleted", true);
    }

    @Transactional
    public List<Map<String, Object>> importCsv(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择 CSV 文件");
        long user = currentUser.id(); List<Map<String, Object>> imported = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine(); if (headerLine == null) return imported;
            String[] headers = splitCsv(headerLine); Map<String, Integer> columns = headerMap(headers);
            String line; while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue; String[] cells = splitCsv(line);
                String date = cell(cells, columns, "date", "日期", "交易日期"); String amount = cell(cells, columns, "amount", "金额", "收支金额");
                if (date.isBlank() || amount.isBlank()) continue;
                String normalizedDate = normalizeImportDate(date);
                String rawKind = cell(cells, columns, "kind", "类型", "交易类型", "收支");
                if (isTransfer(rawKind)) continue;
                String kind = rawKind.contains("收") || rawKind.equalsIgnoreCase("income") ? "INCOME" : "EXPENSE";
                String accountName = cell(cells, columns, "account", "账户", "账户名称", "支出账户", "收入账户"); if (accountName.isBlank()) accountName = "现金";
                long accountId = ensureNamedAccount(accountName, user);
                String primaryCategory = cell(cells, columns, "一级分类"); String secondaryCategory = cell(cells, columns, "二级分类"); String genericCategory = cell(cells, columns, "category", "分类", "类别");
                long categoryId = ensureImportSecondaryCategory(primaryCategory, secondaryCategory, genericCategory, kind, user);
                Map<String, Object> body = new LinkedHashMap<>(); body.put("accountId", accountId); body.put("categoryId", categoryId); body.put("kind", kind); body.put("amount", amount.replace(",", "")); body.put("occurredOn", normalizedDate); body.put("payee", cell(cells, columns, "payee", "商户", "商家", "对方")); body.put("member", cell(cells, columns, "member", "成员")); body.put("project", cell(cells, columns, "project", "项目")); body.put("note", cell(cells, columns, "note", "备注", "说明")); body.put("source", "import");
                imported.add(createTransaction(body, "import-" + UUID.randomUUID()));
            }
        }
        return imported;
    }

    @Transactional
    public Map<String, Object> importExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择 Excel 文件");
        byte[] content = file.getBytes();
        List<ReadSheet> sheets;
        try (ExcelReader reader = EasyExcel.read(new ByteArrayInputStream(content)).build()) {
            sheets = reader.excelExecutor().sheetList();
        }

        long user = currentUser.id(); List<Map<String, Object>> imported = new ArrayList<>(); int skippedTransfers = 0;
        for (ReadSheet sheet : sheets) {
            List<Map<Integer, String>> rows = EasyExcel.read(new ByteArrayInputStream(content)).headRowNumber(0).sheet(sheet.getSheetNo()).doReadSync();
            if (rows.isEmpty()) continue;
            Map<String, Integer> columns = excelHeaders(rows.get(0));
            for (Map<Integer, String> row : rows.subList(1, rows.size())) {
                String date = excelCell(row, columns, "date", "日期", "交易日期");
                String rawAmount = excelCell(row, columns, "amount", "金额", "收支金额");
                if (date.isBlank() || rawAmount.isBlank()) continue;
                String rawKind = excelCell(row, columns, "kind", "类型", "交易类型", "收支");
                if (isTransfer(rawKind) || columns.containsKey("转出账户") || columns.containsKey("转入账户")) { skippedTransfers++; continue; }
                String normalizedDate = normalizeImportDate(date);
                String kind = rawKind.contains("收") || rawKind.equalsIgnoreCase("income") ? "INCOME" : "EXPENSE";
                long accountId = ensureNamedAccount(defaultText(excelCell(row, columns, "account", "账户", "账户名称", "支出账户", "收入账户"), "现金"), user);
                String primaryCategory = excelCell(row, columns, "一级分类"); String secondaryCategory = excelCell(row, columns, "二级分类"); String genericCategory = excelCell(row, columns, "category", "分类", "类别");
                long categoryId = ensureImportSecondaryCategory(primaryCategory, secondaryCategory, genericCategory, kind, user);
                Map<String, Object> body = new LinkedHashMap<>(); body.put("accountId", accountId); body.put("categoryId", categoryId); body.put("kind", kind); body.put("amount", rawAmount.replace(",", "")); body.put("occurredOn", normalizedDate); body.put("payee", excelCell(row, columns, "payee", "商户", "商家", "对方")); body.put("member", excelCell(row, columns, "member", "成员")); body.put("project", excelCell(row, columns, "project", "项目")); body.put("note", excelCell(row, columns, "note", "备注", "说明")); body.put("source", "import");
                imported.add(createTransaction(body, "excel-" + UUID.randomUUID()));
            }
        }
        return Map.of("transactions", imported, "skippedTransfers", skippedTransfers);
    }

    public byte[] exportCsv(String from, String to) {
        StringBuilder csv = new StringBuilder("交易类型,日期,一级分类,二级分类,收入账户,金额,成员,商家,项目,备注\n");
        for (Map<String, Object> row : transactions(from, to, 5000)) {
            String kind = kindLabel(String.valueOf(row.get("kind")));
            Object parentCategory = row.get("parentCategoryName");
            csv.append(csvCell(kind)).append(',').append(csvCell(row.get("occurredOn"))).append(',').append(csvCell(parentCategory == null ? row.get("categoryName") : parentCategory)).append(',').append(csvCell(parentCategory == null ? "" : row.get("categoryName"))).append(',').append(csvCell(row.get("accountName"))).append(',').append(csvCell(row.get("amount"))).append(",,").append(csvCell(row.get("payee"))).append(",,").append(csvCell(row.get("note"))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public Map<String, Object> aiPreview(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("请输入记账内容");
        Matcher money = MONEY.matcher(text); if (!money.find()) throw new IllegalArgumentException("未识别到金额");
        String kind = text.contains("收入") || text.contains("工资") || text.contains("到账") ? "INCOME" : "EXPENSE";
        String category = text.contains("午饭") || text.contains("早餐") || text.contains("晚饭") || text.contains("餐") ? "餐饮" : text.contains("打车") || text.contains("地铁") ? "交通" : "其他";
        Map<String, Object> preview = new LinkedHashMap<>(); preview.put("kind", kind); preview.put("amount", new BigDecimal(money.group(1))); preview.put("category", category); preview.put("occurredOn", LocalDate.now().toString()); preview.put("note", text.trim()); preview.put("requiresConfirmation", true); return preview;
    }

    public Map<String, Object> aiConfirm(Map<String, Object> preview) {
        Map<String, Object> body = new LinkedHashMap<>(preview == null ? Map.of() : preview); long user = currentUser.id();
        String accountName = String.valueOf(body.getOrDefault("account", "现金")); body.put("accountId", ensureNamedAccount(accountName, user));
        String categoryName = String.valueOf(body.getOrDefault("category", "其他")); body.put("categoryId", ensureImportSecondaryCategory(categoryName, "其他", "", String.valueOf(body.getOrDefault("kind", "EXPENSE")), user));
        body.remove("requiresConfirmation"); return createTransaction(body, "ai-" + UUID.randomUUID());
    }

    public Map<String, Object> syncPush(List<Map<String, Object>> operations) {
        if (operations == null) return Map.of("accepted", 0);
        int accepted = 0; long user = currentUser.id();
        for (Map<String, Object> operation : operations) {
            String opId = String.valueOf(operation.getOrDefault("opId", UUID.randomUUID()));
            if (!jdbc.queryForList("SELECT id FROM ledger_sync_oplog WHERE user_id=? AND op_id=?", user, opId).isEmpty()) continue;
            String entity = String.valueOf(operation.getOrDefault("entityType", "transaction")); String action = String.valueOf(operation.getOrDefault("operation", "UPSERT"));
            Map<String, Object> payload = operation.get("payload") instanceof Map<?, ?> p ? cast(p) : mapper.convertValue(operation.getOrDefault("payload", Map.of()), new TypeReference<>() {});
            if ("transaction".equals(entity)) { if ("DELETE".equals(action)) deleteTransaction(number(payload.get("id")), null); else createTransaction(payload, opId); }
            jdbc.update("INSERT IGNORE INTO ledger_sync_oplog (user_id, op_id, entity_type, entity_id, operation, payload_json) VALUES (?, ?, ?, ?, ?, ?)", user, opId, entity, String.valueOf(payload.getOrDefault("id", "")), action, json(payload)); accepted++;
        }
        return Map.of("accepted", accepted);
    }

    public Map<String, Object> syncPull(long cursor, int limit) {
        long user = currentUser.id(); List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, op_id, entity_type, entity_id, operation, payload_json, created_at FROM ledger_sync_oplog WHERE user_id=? AND id>? ORDER BY id LIMIT ?", user, Math.max(0, cursor), Math.min(Math.max(1, limit), 500));
        List<Map<String, Object>> operations = rows.stream().map(row -> { Map<String, Object> op = new LinkedHashMap<>(); op.put("cursor", number(row.get("id"))); op.put("opId", row.get("op_id")); op.put("entityType", row.get("entity_type")); op.put("entityId", row.get("entity_id")); op.put("operation", row.get("operation")); try { op.put("payload", mapper.readValue(String.valueOf(row.get("payload_json")), new TypeReference<Map<String, Object>>() {})); } catch (Exception ignored) { op.put("payload", Map.of()); } return op; }).toList();
        long next = operations.isEmpty() ? cursor : number(operations.get(operations.size() - 1).get("cursor")); return Map.of("cursor", next, "operations", operations);
    }

    @Transactional
    public int materializeMonthEnd(String month) {
        return materializeForUser(month, currentUser.id());
    }

    @Transactional
    public int materializeAllUsers(String month) {
        int count = 0;
        for (Map<String, Object> row : jdbc.queryForList("SELECT id FROM app_user WHERE status = 'ACTIVE'")) count += materializeForUser(month, number(row.get("id")));
        return count;
    }

    private int materializeForUser(String month, long user) {
        YearMonth target = YearMonth.parse(month); LocalDate end = target.atEndOfMonth(); int count = 0;
        for (Map<String, Object> account : jdbc.queryForList("SELECT a.id, a.opening_balance FROM ledger_account a WHERE a.user_id=? AND a.deleted=FALSE", user)) {
            long id = number(account.get("id")); BigDecimal balance = new BigDecimal(String.valueOf(account.get("opening_balance")));
            Map<String, Object> row = jdbc.queryForMap("SELECT COALESCE(SUM(" + BALANCE_DELTA + "),0) delta FROM ledger_transaction t WHERE user_id=? AND account_id=? AND deleted=FALSE AND occurred_on<=?", user, id, end);
            balance = balance.add(new BigDecimal(String.valueOf(row.get("delta"))));
            jdbc.update("INSERT INTO ledger_balance_snapshot (user_id, account_id, month_key, balance) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE balance=VALUES(balance), calculated_at=CURRENT_TIMESTAMP", user, id, month, balance); count++;
        }
        return count;
    }

    private Map<String, Object> account(long id, long user) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.id, a.name, a.account_type, a.currency, a.opening_balance, a.revision, a.created_at, a.opening_balance + COALESCE((SELECT SUM(" + BALANCE_DELTA + ") FROM ledger_transaction t WHERE t.account_id=a.id AND t.user_id=a.user_id AND t.deleted=FALSE),0) balance FROM ledger_account a WHERE a.id=? AND a.user_id=? AND a.deleted=FALSE", id, user); if (rows.isEmpty()) throw new IllegalArgumentException("账户不存在"); return accountView(rows.get(0)); }
    private Map<String, Object> accountView(Map<String, Object> row) { Map<String, Object> v = new LinkedHashMap<>(); v.put("id", number(row.get("id"))); v.put("name", row.get("name")); v.put("accountType", row.get("account_type")); v.put("currency", row.get("currency")); v.put("openingBalance", row.get("opening_balance")); v.put("balance", row.get("balance")); v.put("revision", number(row.get("revision"))); v.put("createdAt", row.get("created_at")); return v; }
    private Map<String, Object> category(long id, long user) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, name, kind, parent_id, color, revision FROM ledger_category WHERE id=? AND user_id=? AND deleted=FALSE", id, user); if (rows.isEmpty()) throw new IllegalArgumentException("分类不存在"); Map<String,Object> row=rows.get(0); Map<String,Object> v=new LinkedHashMap<>(); v.put("id",number(row.get("id"))); v.put("name",row.get("name")); v.put("kind",row.get("kind")); v.put("parentId",row.get("parent_id")); v.put("color",row.get("color")); v.put("revision",number(row.get("revision"))); return v; }
    private Map<String, Object> transaction(long id, long user) { List<Map<String, Object>> rows=jdbc.queryForList("SELECT t.id,t.account_id,a.name account_name,t.counterparty_account_id,counterparty.name counterparty_account_name,t.transfer_group_id,t.category_id,c.name category_name,parent.name parent_category_name,t.kind,t.amount,t.currency,t.occurred_on,t.payee,t.member_name,t.project_name,t.note,t.source,t.recurring_id,t.client_op_id,t.revision,t.created_at FROM ledger_transaction t JOIN ledger_account a ON a.id=t.account_id LEFT JOIN ledger_account counterparty ON counterparty.id=t.counterparty_account_id LEFT JOIN ledger_category c ON c.id=t.category_id LEFT JOIN ledger_category parent ON parent.id=c.parent_id WHERE t.id=? AND t.user_id=?",id,user); if(rows.isEmpty()) throw new IllegalArgumentException("交易不存在"); return transactionView(rows.get(0)); }
    private Map<String,Object> transactionView(Map<String,Object> row){Map<String,Object> v=new LinkedHashMap<>();String storedKind=String.valueOf(row.get("kind"));v.put("id",number(row.get("id")));v.put("accountId",number(row.get("account_id")));v.put("accountName",row.get("account_name"));v.put("targetAccountId",row.get("counterparty_account_id"));v.put("targetAccountName",row.get("counterparty_account_name"));v.put("transferGroupId",row.get("transfer_group_id"));v.put("categoryId",row.get("category_id"));v.put("categoryName",row.get("category_name"));v.put("parentCategoryName",row.get("parent_category_name"));v.put("kind",storedKind.startsWith("TRANSFER_")?"TRANSFER":storedKind);v.put("amount",row.get("amount"));v.put("currency",row.get("currency"));v.put("occurredOn",String.valueOf(row.get("occurred_on")));v.put("payee",row.get("payee"));v.put("member",row.get("member_name"));v.put("project",row.get("project_name"));v.put("note",row.get("note"));v.put("source",row.get("source"));v.put("recurringId",row.get("recurring_id"));v.put("clientOpId",row.get("client_op_id"));v.put("revision",number(row.get("revision")));v.put("createdAt",row.get("created_at"));return v;}
    private void ensureAccount(long id,long user){if(jdbc.queryForList("SELECT id FROM ledger_account WHERE id=? AND user_id=? AND deleted=FALSE",id,user).isEmpty())throw new IllegalArgumentException("账户不存在");}
    private void ensureCategory(long id,long user){if(jdbc.queryForList("SELECT id FROM ledger_category WHERE id=? AND user_id=? AND deleted=FALSE",id,user).isEmpty())throw new IllegalArgumentException("分类不存在");}
    private long requiredSecondaryCategory(Map<String,Object> value,long user,String transactionKind){Long id=longValue(value.get("categoryId"));if(id==null)throw new IllegalArgumentException("categoryId 必填");List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,kind,parent_id FROM ledger_category WHERE id=? AND user_id=? AND deleted=FALSE",id,user);if(rows.isEmpty())throw new IllegalArgumentException("分类不存在");Map<String,Object> category=rows.get(0);if(category.get("parent_id")==null)throw new IllegalArgumentException("分类必须选择到二级");String expected=categoryKindForTransaction(transactionKind);if(expected!=null&&!expected.equals(normalizeCategoryKind(category.get("kind"))))throw new IllegalArgumentException("分类类型与流水类型不匹配");return id;}
    private long ensureNamedAccount(String name,long user){List<Map<String,Object>> r=jdbc.queryForList("SELECT id FROM ledger_account WHERE user_id=? AND name=? AND deleted=FALSE",user,name);if(!r.isEmpty())return number(r.get(0).get("id"));jdbc.update("INSERT INTO ledger_account(user_id,name) VALUES(?,?)",user,name);return jdbc.queryForObject("SELECT id FROM ledger_account WHERE user_id=? ORDER BY id DESC LIMIT 1",Long.class,user);}
    private long ensureNamedCategory(String name,String kind,long user){return ensureNamedCategory(name,kind,user,null);}
    private long ensureNamedCategory(String name,String kind,long user,Long parentId){List<Map<String,Object>> r=jdbc.queryForList("SELECT id FROM ledger_category WHERE user_id=? AND name=? AND kind=? AND parent_id <=> ? AND deleted=FALSE",user,name,normalizeCategoryKind(kind),parentId);if(!r.isEmpty())return number(r.get(0).get("id"));jdbc.update("INSERT INTO ledger_category(user_id,name,kind,parent_id) VALUES(?,?,?,?)",user,name,normalizeCategoryKind(kind),parentId);return jdbc.queryForObject("SELECT id FROM ledger_category WHERE user_id=? ORDER BY id DESC LIMIT 1",Long.class,user);}
    private long ensureImportSecondaryCategory(String primaryName,String secondaryName,String genericName,String kind,long user){String primary=text(primaryName);String secondary=text(secondaryName);String generic=text(genericName);if(primary.isBlank()){primary=generic.isBlank()?"其他":generic;}if(secondary.isBlank())secondary="其他";long parentId=ensureNamedCategory(primary,kind,user);return ensureNamedCategory(secondary,kind,user,parentId);}
    private void appendHistory(long id,long user,String action,Map<String,Object> payload){List<Map<String,Object>> r=jdbc.queryForList("SELECT revision FROM ledger_transaction WHERE id=? AND user_id=?",id,user);long rev=r.isEmpty()?0:number(r.get(0).get("revision"));jdbc.update("INSERT INTO ledger_transaction_history(transaction_id,user_id,operation,payload_json,revision) VALUES(?,?,?,?,?)",id,user,action,json(payload),rev);}
    private void appendSync(long user,String opId,String entity,long entityId,String action,Map<String,Object> payload){jdbc.update("INSERT IGNORE INTO ledger_sync_oplog(user_id,op_id,entity_type,entity_id,operation,payload_json) VALUES(?,?,?,?,?,?)",user,opId,entity,String.valueOf(entityId),action,json(payload));}
    private String required(Map<String,Object> value,String key){Object raw=value==null?null:value.get(key);if(raw==null||String.valueOf(raw).isBlank())throw new IllegalArgumentException(key+" 必填");return String.valueOf(raw).trim();}
    private long requiredLong(Map<String,Object> value,String key){Long result=longValue(value.get(key));if(result==null)throw new IllegalArgumentException(key+" 必填");return result;}
    private BigDecimal amount(Object value){try{return new BigDecimal(String.valueOf(value)).abs().setScale(2,java.math.RoundingMode.HALF_UP);}catch(Exception e){throw new IllegalArgumentException("amount 格式不正确");}}
    private BigDecimal decimal(Object value){try{return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));}catch(Exception e){return BigDecimal.ZERO;}}
    static String normalizeKind(Object value){String kind=String.valueOf(value==null?"":value).trim().toUpperCase(Locale.ROOT);return switch(kind){case "INCOME","收入"->"INCOME";case "TRANSFER","TRANSFER_OUT","TRANSFER_IN","转账"->"TRANSFER";case "BORROW_IN","借入"->"BORROW_IN";case "LEND_OUT","借出"->"LEND_OUT";case "COLLECT_DEBT","收债"->"COLLECT_DEBT";case "REPAY_DEBT","还债"->"REPAY_DEBT";default->"EXPENSE";};}
    static String categoryKindForTransaction(Object value){return switch(normalizeKind(value)){case "INCOME","BORROW_IN","COLLECT_DEBT"->"INCOME";case "EXPENSE","LEND_OUT","REPAY_DEBT"->"EXPENSE";default->null;};}
    private String normalizeCategoryKind(Object value){return "INCOME".equalsIgnoreCase(String.valueOf(value))||"收入".equals(String.valueOf(value))?"INCOME":"EXPENSE";}
    private String kindLabel(String kind){return switch(normalizeKind(kind)){case "INCOME"->"收入";case "TRANSFER"->"转账";case "BORROW_IN"->"借入";case "LEND_OUT"->"借出";case "COLLECT_DEBT"->"收债";case "REPAY_DEBT"->"还债";default->"支出";};}
    private String normalizeType(Object value,String fallback){String text=String.valueOf(value==null?fallback:value).toLowerCase(Locale.ROOT);return List.of("cash","bank","card","wallet","other").contains(text)?text:fallback;}
    private Long longValue(Object value){try{return value==null||String.valueOf(value).isBlank()?null:Long.parseLong(String.valueOf(value));}catch(Exception e){return null;}}
    private String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private long number(Object value){return value instanceof Number n?n.longValue():Long.parseLong(String.valueOf(value));}
    private void checkRevision(String ifMatch,long revision){if(ifMatch==null||ifMatch.isBlank())return;try{long expected=Long.parseLong(ifMatch.replace("W/","").replace("\"",""));if(expected!=revision)throw new ConflictException("资源版本已变化",revision);}catch(NumberFormatException e){throw new IllegalArgumentException("If-Match 必须是 revision");}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){return "{}";}}
    @SuppressWarnings("unchecked") private Map<String,Object> cast(Map<?,?> value){return (Map<String,Object>)value;}
    private String[] splitCsv(String line){List<String> cells=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;for(char ch:line.toCharArray()){if(ch=='\"'){quoted=!quoted;}else if(ch==','&&!quoted){cells.add(cell.toString().trim());cell.setLength(0);}else cell.append(ch);}cells.add(cell.toString().trim());return cells.toArray(String[]::new);}
    private Map<String,Integer> headerMap(String[] headers){Map<String,Integer> result=new LinkedHashMap<>();for(int i=0;i<headers.length;i++)result.put(normalizeHeader(headers[i]),i);return result;}
    private String cell(String[] cells,Map<String,Integer> columns,String... aliases){for(String alias:aliases){Integer index=columns.get(alias.toLowerCase(Locale.ROOT));if(index!=null&&index<cells.length)return cells[index].trim();}return "";}
    private String excelCell(Map<Integer, String> row, Map<String, Integer> columns, String... aliases) { for (String alias : aliases) { Integer index = columns.get(alias.toLowerCase(Locale.ROOT)); if (index != null) return normalizeImportCell(row.get(index)); } return ""; }
    private Map<String, Integer> excelHeaders(Map<Integer, String> headers) { Map<String, Integer> columns = new LinkedHashMap<>(); headers.forEach((key, value) -> columns.put(normalizeHeader(value), key)); return columns; }
    private String normalizeHeader(Object value) { return String.valueOf(value == null ? "" : value).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT); }
    static String normalizeImportCell(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    static String normalizeImportDate(String value) { Matcher matcher = DATE.matcher(String.valueOf(value).trim()); if (!matcher.find()) throw new IllegalArgumentException("日期格式不正确: " + value); String[] parts = matcher.group(1).replace('/', '-').split("-"); return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])).toString(); }
    private boolean isTransfer(String kind) { return kind != null && kind.contains("转账"); }
    private String defaultText(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String csvCell(Object raw){String value=raw==null?"":String.valueOf(raw);return value.contains(",")||value.contains("\"")?"\""+value.replace("\"","\"\"")+"\"":value;}
}
