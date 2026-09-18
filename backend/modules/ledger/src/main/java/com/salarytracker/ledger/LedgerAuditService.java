package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.salarytracker.ledger.LedgerModels.*;

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

    public void record(LedgerBookAccess.Context context, String action, String targetType,
                       String targetPublicId, Object before, Object after) {
        jdbc.update(
                "INSERT INTO ledger_audit_log(book_id,actor_user_id,action,target_type,target_public_id,before_json,after_json) " +
                        "VALUES(?,?,?,?,?,?,?)",
                context.bookId(), context.userId(), action, targetType,
                targetPublicId == null ? "" : targetPublicId, jsonOrNull(before), jsonOrNull(after));
    }

    public AuditPage list(String bookPublicId, int page, int pageSize) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        boolean all = context.isAdmin() || context.permissions().contains("AUDIT_ALL_READ");
        if (!all) access.require(context, "AUDIT_SELF_READ");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        String scope = all ? "" : " AND l.actor_user_id=?";
        List<Object> args = new ArrayList<>();
        args.add(context.bookId());
        if (!all) args.add(context.userId());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_audit_log l WHERE l.book_id=?" + scope,
                Long.class, args.toArray());
        args.add((safePage - 1) * safeSize);
        args.add(safeSize);
        List<AuditLog> items = DbRow.query(jdbc,
                "SELECT l.id,l.action,l.target_type,l.target_public_id,l.before_json,l.after_json,l.created_at," +
                        "u.id actor_user_id,u.username,u.nickname FROM ledger_audit_log l " +
                        "JOIN app_user u ON u.id=l.actor_user_id WHERE l.book_id=?" + scope +
                        " ORDER BY l.id DESC LIMIT ?,?", args.toArray()).stream().map(this::view).toList();
        return new AuditPage(items, safePage, safeSize, total,
                Math.max(1, (total + safeSize - 1) / safeSize));
    }

    @Transactional
    public DeleteCount clear(String bookPublicId, List<Long> ids) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        if (!context.isAdmin()) access.require(context, "AUDIT_ALL_CLEAR");
        int deleted;
        if (ids == null || ids.isEmpty()) {
            deleted = jdbc.update("DELETE FROM ledger_audit_log WHERE book_id=?", context.bookId());
        } else {
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            List<Object> args = new ArrayList<>();
            args.add(context.bookId());
            args.addAll(ids);
            deleted = jdbc.update("DELETE FROM ledger_audit_log WHERE book_id=? AND id IN (" + placeholders + ")",
                    args.toArray());
        }
        return new DeleteCount(deleted);
    }

    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "ledgerAuditRetention", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void purgeExpired() {
        jdbc.update("DELETE FROM ledger_audit_log WHERE created_at < CURRENT_TIMESTAMP - INTERVAL 30 DAY");
        jdbc.update("DELETE FROM ledger_import_batch WHERE expires_at < CURRENT_TIMESTAMP");
    }

    private AuditLog view(DbRow row) {
        ResourceType type = ResourceType.valueOf(text(row.get("target_type")));
        EntitySnapshot before = snapshot(type, parse(row.get("before_json")));
        EntitySnapshot after = snapshot(type, parse(row.get("after_json")));
        return new AuditLog(number(row.get("id")), text(row.get("action")), type,
                text(row.get("target_public_id")), targetName(type, after, before), before, after,
                new Actor(number(row.get("actor_user_id")), text(row.get("username")), text(row.get("nickname"))),
                text(row.get("created_at")));
    }

    private EntitySnapshot snapshot(ResourceType type, JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) return null;
        return new EntitySnapshot(type, string(node, "id"), string(node, "name"), string(node, "code"),
                string(node, "username"), string(node, "displayName"), string(node, "category"),
                string(node, "categoryId"), string(node, "monthKey"), string(node, "accountId"),
                string(node, "occurredOn"), string(node, "payee"), decimal(node.get("amount")),
                longValue(node.get("revision")), booleanValue(node.get("deleted")));
    }

    private String targetName(ResourceType type, EntitySnapshot after, EntitySnapshot before) {
        for (EntitySnapshot source : new EntitySnapshot[] {after, before}) {
            if (source == null) continue;
            for (String value : new String[] {
                    source.name(), source.displayName(), source.category(), source.username()
            }) {
                if (value != null && !value.isBlank()) return value;
            }
        }
        EntitySnapshot source = after != null ? after : before;
        if (type == ResourceType.transaction && source != null) {
            String amount = source.amount() == null ? "" : " ¥" + source.amount();
            return String.join(" ", blank(source.occurredOn()), blank(source.payee())).trim() + amount;
        }
        if (type == ResourceType.budget && source != null && source.monthKey() != null) {
            return source.monthKey() + " 预算";
        }
        return switch (type) {
            case account -> "已删除账户";
            case category -> "已删除分类";
            case merchant -> "已删除商家";
            case member -> "已删除成员";
            case project -> "已删除项目";
            case role -> "已删除角色";
            case book -> "已删除账本";
            case transaction -> "已删除流水";
            case budget -> "已删除预算";
        };
    }

    private JsonNode parse(Object value) {
        if (value == null) return null;
        try {
            return mapper.readTree(String.valueOf(value));
        } catch (Exception exception) {
            return null;
        }
    }

    private String jsonOrNull(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法记录账本审计日志", exception);
        }
    }

    private String string(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private BigDecimal decimal(JsonNode value) {
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private Long longValue(JsonNode value) {
        return value == null || value.isNull() ? null : value.asLong();
    }

    private Boolean booleanValue(JsonNode value) {
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
