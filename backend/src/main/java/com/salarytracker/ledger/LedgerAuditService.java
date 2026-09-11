package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LedgerAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerBookAccess access;

    public LedgerAuditService(JdbcTemplate jdbc, ObjectMapper mapper, LedgerBookAccess access) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.access = access;
    }

    public void record(LedgerBookAccess.Context context,
                       String action,
                       String targetType,
                       String targetPublicId,
                       Object before,
                       Object after) {
        jdbc.update(
                "INSERT INTO ledger_audit_log(book_id,actor_user_id,action,target_type,target_public_id,before_json,after_json) " +
                        "VALUES(?,?,?,?,?,?,?)",
                context.bookId(), context.userId(), action, targetType,
                targetPublicId == null ? "" : targetPublicId,
                jsonOrNull(before), jsonOrNull(after));
    }

    public Map<String, Object> list(String bookPublicId, int page, int pageSize) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        boolean all = context.isAdmin() || context.permissions().contains("AUDIT_ALL_READ");
        if (!all) access.require(context, "AUDIT_SELF_READ");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        String scope = all ? "" : " AND l.actor_user_id=?";
        List<Object> args = new java.util.ArrayList<>();
        args.add(context.bookId());
        if (!all) args.add(context.userId());
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_audit_log l WHERE l.book_id=?" + scope,
                Long.class, args.toArray());
        args.add((safePage - 1) * safeSize);
        args.add(safeSize);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT l.id,l.action,l.target_type,l.target_public_id,l.before_json,l.after_json,l.created_at," +
                        "u.id actor_user_id,u.username,u.nickname " +
                        "FROM ledger_audit_log l JOIN app_user u ON u.id=l.actor_user_id " +
                        "WHERE l.book_id=?" + scope + " ORDER BY l.id DESC LIMIT ?,?",
                args.toArray()).stream().map(this::view).toList();
        return page(items, total, safePage, safeSize);
    }

    @Transactional
    public Map<String, Object> clear(String bookPublicId, List<Long> ids) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        if (!context.isAdmin()) access.require(context, "AUDIT_ALL_CLEAR");
        int deleted;
        if (ids == null || ids.isEmpty()) {
            deleted = jdbc.update("DELETE FROM ledger_audit_log WHERE book_id=?", context.bookId());
        } else {
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            List<Object> args = new java.util.ArrayList<>();
            args.add(context.bookId());
            args.addAll(ids);
            deleted = jdbc.update(
                    "DELETE FROM ledger_audit_log WHERE book_id=? AND id IN (" + placeholders + ")",
                    args.toArray());
        }
        return Map.of("deleted", deleted);
    }

    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "ledgerAuditRetention", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void purgeExpired() {
        jdbc.update("DELETE FROM ledger_audit_log WHERE created_at < CURRENT_TIMESTAMP - INTERVAL 30 DAY");
        jdbc.update("DELETE FROM ledger_import_batch WHERE expires_at < CURRENT_TIMESTAMP");
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", number(row.get("id")));
        result.put("action", row.get("action"));
        result.put("targetType", row.get("target_type"));
        result.put("targetId", row.get("target_public_id"));
        result.put("before", parse(row.get("before_json")));
        result.put("after", parse(row.get("after_json")));
        result.put("actor", Map.of(
                "id", number(row.get("actor_user_id")),
                "username", String.valueOf(row.get("username")),
                "nickname", String.valueOf(row.get("nickname"))));
        result.put("createdAt", row.get("created_at"));
        return result;
    }

    private Map<String, Object> page(List<Map<String, Object>> items, long total, int page, int pageSize) {
        return Map.of(
                "items", items,
                "page", page,
                "pageSize", pageSize,
                "total", total,
                "totalPages", Math.max(1, (total + pageSize - 1) / pageSize));
    }

    private String jsonOrNull(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private Object parse(Object value) {
        if (value == null) return null;
        try {
            return mapper.readValue(String.valueOf(value), Object.class);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
