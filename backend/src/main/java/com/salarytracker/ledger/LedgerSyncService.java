package com.salarytracker.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.ForbiddenException;
import com.salarytracker.platform.SyncResetRequiredException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LedgerSyncService {
    private static final Set<String> SENSITIVE_TRANSACTION_FIELDS = Set.of(
            "kind", "amount", "occurredOn", "accountId", "targetAccountId", "categoryId",
            "merchantId", "memberId", "projectId", "note");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerBookAccess access;
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;

    public LedgerSyncService(JdbcTemplate jdbc,
                             ObjectMapper mapper,
                             LedgerBookAccess access,
                             LedgerBookService books,
                             LedgerTransactionService transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.access = access;
        this.books = books;
        this.transactions = transactions;
    }

    public Map<String, Object> push(String bookPublicId, List<Map<String, Object>> operations) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> operation : operations == null ? List.<Map<String, Object>>of() : operations) {
            results.add(apply(context, operation));
        }
        long applied = results.stream().filter(result -> "APPLIED".equals(result.get("status"))).count();
        long duplicates = results.stream().filter(result -> "DUPLICATE".equals(result.get("status"))).count();
        return Map.of("results", results, "applied", applied, "duplicates", duplicates);
    }

    public Map<String, Object> pull(String bookPublicId, long cursor, int limit) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        long max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id),0) FROM ledger_sync_oplog WHERE book_id=?",
                Long.class, context.bookId());
        long min = jdbc.queryForObject(
                "SELECT COALESCE(MIN(id),0) FROM ledger_sync_oplog WHERE book_id=?",
                Long.class, context.bookId());
        if (cursor < 0 || cursor > max || cursor > 0 && min > 0 && cursor < min - 1) {
            throw new SyncResetRequiredException("同步游标已失效，请重新下载当前账本");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<Map<String, Object>> operations = jdbc.queryForList(
                "SELECT id,op_id,entity_type,entity_id,operation,payload_json,created_at " +
                        "FROM ledger_sync_oplog WHERE book_id=? AND id>? ORDER BY id LIMIT ?",
                context.bookId(), cursor, safeLimit).stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cursor", number(row.get("id")));
            result.put("opId", row.get("op_id"));
            result.put("entityType", row.get("entity_type"));
            result.put("entityId", row.get("entity_id"));
            result.put("operation", row.get("operation"));
            result.put("payload", parse(row.get("payload_json")));
            result.put("createdAt", row.get("created_at"));
            return result;
        }).toList();
        long nextCursor = operations.isEmpty() ? cursor : number(operations.get(operations.size() - 1).get("cursor"));
        return Map.of(
                "operations", operations,
                "cursor", nextCursor,
                "hasMore", nextCursor < max,
                "serverMaxCursor", max);
    }

    @Transactional
    protected Map<String, Object> apply(LedgerBookAccess.Context context, Map<String, Object> operation) {
        String opId = text(operation.get("opId"));
        if (opId.isBlank()) opId = UUID.randomUUID().toString();
        String entityType = normalizeType(operation.get("entityType"));
        String entityId = textOr(operation.get("entityId"), text(payload(operation).get("id")));
        String action = textOr(operation.get("operation"), "UPSERT").toUpperCase();
        Map<String, Object> result = baseResult(opId, entityType, entityId);
        if (jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_sync_oplog WHERE user_id=? AND op_id=?",
                Long.class, context.userId(), opId) > 0) {
            result.put("status", "DUPLICATE");
            return result;
        }
        try {
            Map<String, Object> server = applyOperation(
                    context, entityType, entityId, action, payload(operation), operation, opId);
            result.put("status", "APPLIED");
            result.put("entity", publicEntity(server));
        } catch (ConflictException exception) {
            Map<String, Object> server = current(context, entityType, entityId);
            result.put("status", "CONFLICT");
            result.put("serverRevision", exception.getServerRevision());
            result.put("serverEntity", publicEntity(server));
            result.put("conflictFields", conflictFields(payload(operation), server, entityType));
            result.put("message", exception.getMessage());
        } catch (ForbiddenException exception) {
            result.put("status", "FORBIDDEN");
            result.put("message", exception.getMessage());
            result.put("exportRequired", true);
        } catch (IllegalArgumentException exception) {
            result.put("status", "CONFLICT");
            result.put("message", exception.getMessage());
            result.put("serverEntity", publicEntity(current(context, entityType, entityId)));
            result.put("conflictFields", List.of());
        }
        return result;
    }

    private Map<String, Object> applyOperation(LedgerBookAccess.Context context,
                                               String entityType,
                                               String entityId,
                                               String action,
                                               Map<String, Object> payload,
                                               Map<String, Object> operation,
                                               String opId) {
        Map<String, Object> existing = current(context, entityType, entityId);
        String revision = text(operation.get("baseRevision"));
        if (revision.isBlank() && existing != null) revision = text(payload.get("revision"));
        if ("DELETE".equals(action)) {
            if (existing == null) return Map.of("id", entityId, "deleted", true);
            return delete(context, entityType, entityId, revision, opId);
        }
        if (existing == null) {
            payload.putIfAbsent("id", entityId);
            return create(context, entityType, payload, opId);
        }
        return update(context, entityType, entityId, payload, revision, opId);
    }

    private Map<String, Object> create(LedgerBookAccess.Context context,
                                       String type,
                                       Map<String, Object> payload,
                                       String opId) {
        return switch (type) {
            case "book" -> books.createBook(payload);
            case "account" -> books.createAccount(context.bookPublicId(), payload, opId);
            case "category" -> books.createCategory(context.bookPublicId(), payload, opId);
            case "merchant", "project" -> books.createNamedResource(context.bookPublicId(), type, payload, opId);
            case "member" -> books.addMember(context.bookPublicId(), payload, opId);
            case "role" -> books.createRole(context.bookPublicId(), payload, opId);
            case "budget" -> books.upsertBudget(context.bookPublicId(), payload, opId);
            case "transaction" -> transactions.create(context.bookPublicId(), payload, opId);
            default -> throw new IllegalArgumentException("不支持同步实体：" + type);
        };
    }

    private Map<String, Object> update(LedgerBookAccess.Context context,
                                       String type,
                                       String id,
                                       Map<String, Object> payload,
                                       String revision,
                                       String opId) {
        return switch (type) {
            case "book" -> books.updateBook(context.bookPublicId(), payload, revision);
            case "account" -> books.updateAccount(context.bookPublicId(), id, payload, revision, opId);
            case "category" -> books.updateCategory(context.bookPublicId(), id, payload, revision, opId);
            case "merchant", "project" ->
                    books.updateNamedResource(context.bookPublicId(), type, id, payload, revision, opId);
            case "member" -> books.updateMember(context.bookPublicId(), id, payload, revision, opId);
            case "role" -> books.updateRole(context.bookPublicId(), id, payload, revision, opId);
            case "budget" -> books.upsertBudget(context.bookPublicId(), payload, opId);
            case "transaction" ->
                    transactions.update(context.bookPublicId(), id, payload, revision, opId);
            default -> throw new IllegalArgumentException("不支持同步实体：" + type);
        };
    }

    private Map<String, Object> delete(LedgerBookAccess.Context context,
                                       String type,
                                       String id,
                                       String revision,
                                       String opId) {
        return switch (type) {
            case "book" -> books.deleteBook(context.bookPublicId(), revision);
            case "account" -> books.deleteAccount(context.bookPublicId(), id, revision, opId);
            case "category" -> books.deleteCategory(context.bookPublicId(), id, revision, opId);
            case "merchant", "project" ->
                    books.deleteNamedResource(context.bookPublicId(), type, id, revision, opId);
            case "member" -> books.deleteMember(context.bookPublicId(), id, revision, opId);
            case "role" -> books.deleteRole(context.bookPublicId(), id, revision, opId);
            case "budget" -> books.deleteBudget(context.bookPublicId(), id, revision, opId);
            case "transaction" -> transactions.delete(context.bookPublicId(), id, revision, opId);
            default -> throw new IllegalArgumentException("不支持同步实体：" + type);
        };
    }

    private Map<String, Object> current(LedgerBookAccess.Context context, String type, String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return switch (type) {
                case "book" -> books.books().stream()
                        .filter(item -> id.equals(item.get("id"))).findFirst().orElse(null);
                case "account" -> books.accounts(context.bookPublicId(), true).stream()
                        .filter(item -> id.equals(item.get("id"))).findFirst().orElse(null);
                case "category" -> books.categories(context.bookPublicId(), true).stream()
                        .filter(item -> id.equals(item.get("id"))).findFirst().orElse(null);
                case "merchant" -> books.merchants(context.bookPublicId(), true).stream()
                        .filter(item -> id.equals(item.get("id"))).findFirst().orElse(null);
                case "project" -> books.projects(context.bookPublicId(), true).stream()
                        .filter(item -> id.equals(item.get("id"))).findFirst().orElse(null);
                case "member" -> books.members(context.bookPublicId()).stream()
                        .filter(item -> id.equals(item.get("id"))).findFirst().orElse(null);
                case "role" -> books.roles(context.bookPublicId()).stream()
                        .filter(item -> id.equals(item.get("id"))).findFirst().orElse(null);
                case "budget" -> currentBudget(context, id);
                case "transaction" -> transactions.get(context, id, false);
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> currentBudget(LedgerBookAccess.Context context, String publicId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT b.public_id,b.month_key,b.amount,b.revision,b.deleted,b.created_by," +
                        "c.public_id category_id,c.name category " +
                        "FROM ledger_budget b JOIN ledger_category c ON c.id=b.category_id " +
                        "WHERE b.public_id=? AND b.book_id=?",
                publicId, context.bookId());
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("public_id"));
        result.put("monthKey", row.get("month_key"));
        result.put("budget", row.get("amount"));
        result.put("revision", number(row.get("revision")));
        result.put("categoryId", row.get("category_id"));
        result.put("category", row.get("category"));
        result.put("deleted", row.get("deleted"));
        result.put("createdBy", row.get("created_by"));
        return result;
    }

    private List<String> conflictFields(Map<String, Object> local,
                                        Map<String, Object> server,
                                        String type) {
        if (server == null) return List.of();
        Set<String> candidates = "transaction".equals(type)
                ? SENSITIVE_TRANSACTION_FIELDS : local.keySet();
        Set<String> differences = new LinkedHashSet<>();
        for (String field : candidates) {
            if (!local.containsKey(field)) continue;
            if (!String.valueOf(local.get(field)).equals(String.valueOf(server.get(field)))) differences.add(field);
        }
        return List.copyOf(differences);
    }

    private Map<String, Object> baseResult(String opId, String entityType, String entityId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("opId", opId);
        result.put("entityType", entityType);
        result.put("entityId", entityId);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> operation) {
        Object value = operation.get("payload");
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    private String normalizeType(Object value) {
        String type = text(value).toLowerCase().replace("ledger-", "");
        if (type.endsWith("ies")) type = type.substring(0, type.length() - 3) + "y";
        else if (type.endsWith("s")) type = type.substring(0, type.length() - 1);
        return type;
    }

    private Object parse(Object value) {
        try {
            return mapper.readValue(String.valueOf(value), new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Map<String, Object> publicEntity(Map<String, Object> entity) {
        if (entity == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>(entity);
        result.remove("internalId");
        return result;
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
}
