package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.ForbiddenException;
import com.salarytracker.platform.SyncResetRequiredException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.salarytracker.ledger.LedgerModels.*;

@Service
public class LedgerSyncService {
    private static final Set<String> SENSITIVE_TRANSACTION_FIELDS = Set.of(
            "kind", "amount", "occurredOn", "accountId", "targetAccountId", "categoryId",
            "merchantId", "memberId", "projectId", "note");
    private static final List<String> ALL_PAYLOAD_FIELDS = List.of(
            "id", "name", "code", "username", "roleId", "icon", "color", "note", "currency",
            "accountType", "openingBalance", "hidden", "archived", "kind", "parentId", "categoryId",
            "scope", "monthKey", "budget", "permissions", "accountId", "targetAccountId", "merchantId",
            "memberId", "projectId", "amount", "occurredOn", "payee", "member", "project", "source",
            "clientOpId", "revision");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerBookAccess access;
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;

    public LedgerSyncService(JdbcTemplate jdbc, ObjectMapper mapper, LedgerBookAccess access,
                             LedgerBookService books, LedgerTransactionService transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.access = access;
        this.books = books;
        this.transactions = transactions;
    }

    public SyncPushResponse push(String bookPublicId, List<SyncOperationRequest> operations) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        List<SyncOperationResult> results = new ArrayList<>();
        for (SyncOperationRequest operation : operations == null ? List.<SyncOperationRequest>of() : operations) {
            results.add(apply(context, operation));
        }
        long applied = results.stream().filter(result -> result.status() == SyncStatus.APPLIED).count();
        long duplicates = results.stream().filter(result -> result.status() == SyncStatus.DUPLICATE).count();
        return new SyncPushResponse(results, applied, duplicates);
    }

    public SyncPullResponse pull(String bookPublicId, long cursor, int limit) {
        LedgerBookAccess.Context context = access.resolve(bookPublicId);
        long max = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM ledger_sync_oplog WHERE book_id=?",
                Long.class, context.bookId());
        long min = jdbc.queryForObject("SELECT COALESCE(MIN(id),0) FROM ledger_sync_oplog WHERE book_id=?",
                Long.class, context.bookId());
        if (cursor < 0 || cursor > max || cursor > 0 && min > 0 && cursor < min - 1) {
            throw new SyncResetRequiredException("同步游标已失效，请重新下载当前账本");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<SyncChange> operations = DbRow.query(jdbc,
                "SELECT id,op_id,entity_type,entity_id,operation,payload_json,created_at " +
                        "FROM ledger_sync_oplog WHERE book_id=? AND id>? ORDER BY id LIMIT ?",
                context.bookId(), cursor, safeLimit).stream().map(row -> {
                    ResourceType type = ResourceType.valueOf(text(row.get("entity_type")));
                    SyncAction action = SyncAction.valueOf(text(row.get("operation")));
                    return new SyncChange(number(row.get("id")), text(row.get("op_id")), type,
                            text(row.get("entity_id")), action,
                            readEntity(row.get("payload_json"), type, action), text(row.get("created_at")));
                }).toList();
        long nextCursor = operations.isEmpty() ? cursor : operations.get(operations.size() - 1).cursor();
        return new SyncPullResponse(operations, nextCursor, nextCursor < max, max);
    }

    @Transactional
    protected SyncOperationResult apply(LedgerBookAccess.Context context, SyncOperationRequest operation) {
        String opId = operation == null || text(operation.opId()).isBlank()
                ? UUID.randomUUID().toString() : operation.opId();
        ResourceType type = operation == null || operation.entityType() == null
                ? null : operation.entityType();
        SyncPayload payload = operation == null || operation.payload() == null ? emptyPayload() : operation.payload();
        String entityId = textOr(operation == null ? null : operation.entityId(), text(payload.id()));
        if (type == null) return result(opId, null, entityId, SyncStatus.REJECTED, null, null,
                null, List.of(), "entityType 必填", null);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ledger_sync_oplog WHERE user_id=? AND op_id=?",
                Long.class, context.userId(), opId) > 0) {
            return result(opId, type, entityId, SyncStatus.DUPLICATE, null, null,
                    null, List.of(), null, null);
        }
        try {
            SyncEntity server = applyOperation(context, type, entityId,
                    operation.operation() == null ? SyncAction.UPSERT : operation.operation(),
                    payload, operation.baseRevision(), opId);
            return result(opId, type, entityId, SyncStatus.APPLIED, toEntity(server), null,
                    null, List.of(), null, null);
        } catch (ConflictException exception) {
            SyncEntity server = current(context, type, entityId);
            return result(opId, type, entityId, SyncStatus.CONFLICT, null,
                    exception.getServerRevision(), toEntity(server), conflictFields(payload, server, type),
                    exception.getMessage(), null);
        } catch (ForbiddenException exception) {
            return result(opId, type, entityId, SyncStatus.FORBIDDEN, null, null,
                    null, List.of(), exception.getMessage(), true);
        } catch (IllegalArgumentException exception) {
            return result(opId, type, entityId, SyncStatus.REJECTED, null, null,
                    null, List.of(), exception.getMessage(), null);
        }
    }

    private SyncEntity applyOperation(LedgerBookAccess.Context context, ResourceType type, String entityId,
                                  SyncAction action, SyncPayload payload, Long baseRevision, String opId) {
        SyncEntity existing = current(context, type, entityId);
        String revision = baseRevision == null ? text(payload.revision()) : String.valueOf(baseRevision);
        if (action == SyncAction.DELETE) {
            if (existing == null) return new DeletedResource(entityId, baseRevision == null ? 0 : baseRevision,
                    true, null, null, null, null);
            return delete(context, type, entityId, revision, opId);
        }
        return existing == null ? create(context, type, entityId, payload, opId)
                : update(context, type, entityId, payload, revision, opId);
    }

    private SyncEntity create(LedgerBookAccess.Context context, ResourceType type, String id,
                          SyncPayload payload, String opId) {
        return switch (type) {
            case book -> books.createBook(bookCommand(id, payload));
            case account -> books.createAccount(context.bookPublicId(), accountCommand(id, payload), opId);
            case category -> books.createCategory(context.bookPublicId(), categoryCommand(id, payload), opId);
            case merchant, project -> books.createNamedResource(context.bookPublicId(), type.name(), namedCommand(id, payload), opId);
            case member -> books.addMember(context.bookPublicId(), memberCommand(id, payload), opId);
            case role -> books.createRole(context.bookPublicId(), roleCommand(id, payload), opId);
            case budget -> books.upsertBudget(context.bookPublicId(), budgetCommand(id, payload), opId);
            case transaction -> transactions.create(context.bookPublicId(), transactionCommand(id, payload), opId);
        };
    }

    private SyncEntity update(LedgerBookAccess.Context context, ResourceType type, String id,
                          SyncPayload payload, String revision, String opId) {
        return switch (type) {
            case book -> books.updateBook(context.bookPublicId(), bookCommand(id, payload), revision);
            case account -> books.updateAccount(context.bookPublicId(), id, accountCommand(id, payload), revision, opId);
            case category -> books.updateCategory(context.bookPublicId(), id, categoryCommand(id, payload), revision, opId);
            case merchant, project -> books.updateNamedResource(context.bookPublicId(), type.name(), id,
                    namedCommand(id, payload), revision, opId);
            case member -> books.updateMember(context.bookPublicId(), id, memberCommand(id, payload), revision, opId);
            case role -> books.updateRole(context.bookPublicId(), id, roleCommand(id, payload), revision, opId);
            case budget -> books.upsertBudget(context.bookPublicId(), budgetCommand(id, payload), opId);
            case transaction -> transactions.update(context.bookPublicId(), id, transactionCommand(id, payload), revision, opId);
        };
    }

    private SyncEntity delete(LedgerBookAccess.Context context, ResourceType type, String id,
                          String revision, String opId) {
        return switch (type) {
            case book -> books.deleteBook(context.bookPublicId(), revision);
            case account -> books.deleteAccount(context.bookPublicId(), id, revision, opId);
            case category -> books.deleteCategory(context.bookPublicId(), id, revision, opId);
            case merchant, project -> books.deleteNamedResource(context.bookPublicId(), type.name(), id, revision, opId);
            case member -> books.deleteMember(context.bookPublicId(), id, revision, opId);
            case role -> books.deleteRole(context.bookPublicId(), id, revision, opId);
            case budget -> books.deleteBudget(context.bookPublicId(), id, revision, opId);
            case transaction -> transactions.delete(context.bookPublicId(), id, revision, opId);
        };
    }

    private SyncEntity current(LedgerBookAccess.Context context, ResourceType type, String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return switch (type) {
                case book -> books.books().stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
                case account -> books.accounts(context.bookPublicId(), true).stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
                case category -> books.categories(context.bookPublicId(), true).stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
                case merchant -> books.merchants(context.bookPublicId(), true).stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
                case project -> books.projects(context.bookPublicId(), true).stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
                case member -> books.members(context.bookPublicId()).stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
                case role -> books.roles(context.bookPublicId()).stream().filter(item -> id.equals(item.id())).findFirst().orElse(null);
                case budget -> currentBudget(context, id);
                case transaction -> transactions.get(context, id, false);
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private Budget currentBudget(LedgerBookAccess.Context context, String publicId) {
        List<DbRow> rows = DbRow.query(jdbc,
                "SELECT b.public_id,b.month_key,b.amount budget,b.revision,b.category_id," +
                        "c.public_id category_public_id,c.name category,0 spent FROM ledger_budget b " +
                        "LEFT JOIN ledger_category c ON c.id=b.category_id WHERE b.public_id=? AND b.book_id=?",
                publicId, context.bookId());
        if (rows.isEmpty()) return null;
        DbRow row = rows.get(0);
        return new Budget(text(row.get("public_id")), nullableText(row.get("category_public_id")),
                row.get("category") == null ? "月度总预算" : text(row.get("category")),
                row.get("category_id") == null ? "TOTAL" : "CATEGORY", text(row.get("month_key")),
                decimal(row.get("budget")), BigDecimal.ZERO, number(row.get("revision")));
    }

    private List<String> conflictFields(SyncPayload local, SyncEntity server, ResourceType type) {
        if (server == null) return List.of();
        Set<String> candidates = type == ResourceType.transaction ? SENSITIVE_TRANSACTION_FIELDS : Set.copyOf(ALL_PAYLOAD_FIELDS);
        Set<String> differences = new LinkedHashSet<>();
        for (String field : candidates) {
            Object localValue = payloadField(local, field);
            if (localValue == null) continue;
            Object serverValue = entityField(server, field);
            if (!String.valueOf(localValue).equals(String.valueOf(serverValue))) differences.add(field);
        }
        return List.copyOf(differences);
    }

    private Object payloadField(SyncPayload value, String field) {
        return switch (field) {
            case "id" -> value.id();
            case "name" -> value.name();
            case "code" -> value.code();
            case "username" -> value.username();
            case "roleId" -> value.roleId();
            case "icon" -> value.icon();
            case "color" -> value.color();
            case "note" -> value.note();
            case "currency" -> value.currency();
            case "accountType" -> value.accountType();
            case "openingBalance" -> value.openingBalance();
            case "hidden" -> value.hidden();
            case "archived" -> value.archived();
            case "kind" -> value.kind();
            case "parentId" -> value.parentId();
            case "categoryId" -> value.categoryId();
            case "scope" -> value.scope();
            case "monthKey" -> value.monthKey();
            case "budget" -> value.budget();
            case "permissions" -> value.permissions();
            case "accountId" -> value.accountId();
            case "targetAccountId" -> value.targetAccountId();
            case "merchantId" -> value.merchantId();
            case "memberId" -> value.memberId();
            case "projectId" -> value.projectId();
            case "amount" -> value.amount();
            case "occurredOn" -> value.occurredOn();
            case "payee" -> value.payee();
            case "member" -> value.member();
            case "project" -> value.project();
            case "source" -> value.source();
            case "clientOpId" -> value.clientOpId();
            case "revision" -> value.revision();
            default -> null;
        };
    }

    private Object entityField(SyncEntity value, String field) {
        if (value == null) return null;
        return switch (field) {
            case "id" -> entityId(value);
            case "name" -> value instanceof Book item ? item.name()
                    : value instanceof Account item ? item.name()
                    : value instanceof Category item ? item.name()
                    : value instanceof NamedResource item ? item.name()
                    : value instanceof Role item ? item.name() : null;
            case "code" -> value instanceof Role item ? item.code() : null;
            case "username" -> value instanceof Member item ? item.username() : null;
            case "roleId" -> value instanceof Member item ? item.roleId() : null;
            case "icon" -> value instanceof Account item ? item.icon()
                    : value instanceof Category item ? item.icon()
                    : value instanceof NamedResource item ? item.icon()
                    : value instanceof Member item ? item.icon() : null;
            case "color" -> value instanceof Category item ? item.color()
                    : value instanceof NamedResource item ? item.color() : null;
            case "note" -> value instanceof NamedResource item ? item.note()
                    : value instanceof Transaction item ? item.note() : null;
            case "currency" -> value instanceof Book item ? item.currency()
                    : value instanceof Account item ? item.currency()
                    : value instanceof Transaction item ? item.currency() : null;
            case "accountType" -> value instanceof Account item ? item.accountType() : null;
            case "openingBalance" -> value instanceof Account item ? item.openingBalance() : null;
            case "hidden" -> value instanceof Account item ? item.hidden()
                    : value instanceof Category item ? item.hidden()
                    : value instanceof NamedResource item ? item.hidden() : null;
            case "archived" -> value instanceof Book item ? item.archived() : null;
            case "kind" -> value instanceof Category item ? item.kind()
                    : value instanceof Transaction item ? item.kind() : null;
            case "parentId" -> value instanceof Category item ? item.parentId() : null;
            case "categoryId" -> value instanceof Budget item ? item.categoryId()
                    : value instanceof Transaction item ? item.categoryId() : null;
            case "scope" -> value instanceof Budget item ? item.scope() : null;
            case "monthKey" -> value instanceof Budget item ? item.monthKey() : null;
            case "budget" -> value instanceof Budget item ? item.budget() : null;
            case "permissions" -> value instanceof Book item ? item.permissions()
                    : value instanceof Role item ? item.permissions() : null;
            case "accountId" -> value instanceof Transaction item ? item.accountId() : null;
            case "targetAccountId" -> value instanceof Transaction item ? item.targetAccountId() : null;
            case "merchantId" -> value instanceof Transaction item ? item.merchantId() : null;
            case "memberId" -> value instanceof Transaction item ? item.memberId() : null;
            case "projectId" -> value instanceof Transaction item ? item.projectId() : null;
            case "amount" -> value instanceof Transaction item ? item.amount() : null;
            case "occurredOn" -> value instanceof Transaction item ? item.occurredOn() : null;
            case "payee" -> value instanceof Transaction item ? item.payee() : null;
            case "member" -> value instanceof Transaction item ? item.member() : null;
            case "project" -> value instanceof Transaction item ? item.project() : null;
            case "source" -> value instanceof Transaction item ? item.source() : null;
            case "clientOpId" -> value instanceof Transaction item ? item.clientOpId() : null;
            case "revision" -> entityRevision(value);
            default -> null;
        };
    }

    private String entityId(SyncEntity value) {
        if (value instanceof Book item) return item.id();
        if (value instanceof Account item) return item.id();
        if (value instanceof Category item) return item.id();
        if (value instanceof NamedResource item) return item.id();
        if (value instanceof Member item) return item.id();
        if (value instanceof Role item) return item.id();
        if (value instanceof Budget item) return item.id();
        if (value instanceof Transaction item) return item.id();
        if (value instanceof DeletedResource item) return item.id();
        return null;
    }

    private long entityRevision(SyncEntity value) {
        if (value instanceof Book item) return item.revision();
        if (value instanceof Account item) return item.revision();
        if (value instanceof Category item) return item.revision();
        if (value instanceof NamedResource item) return item.revision();
        if (value instanceof Member item) return item.revision();
        if (value instanceof Role item) return item.revision();
        if (value instanceof Budget item) return item.revision();
        if (value instanceof Transaction item) return item.revision();
        if (value instanceof DeletedResource item) return item.revision();
        return 0;
    }

    private SyncOperationResult result(String opId, ResourceType type, String entityId, SyncStatus status,
                                       SyncEntity entity, Long serverRevision, SyncEntity serverEntity,
                                       List<String> conflictFields, String message, Boolean exportRequired) {
        return new SyncOperationResult(opId, type, entityId, status, entity, serverRevision,
                serverEntity, conflictFields, message, exportRequired);
    }

    private SyncEntity toEntity(SyncEntity value) {
        return value;
    }

    private BookCommand bookCommand(String id, SyncPayload p) {
        return new BookCommand(idOrPayload(id, p), p.name(), p.currency(), null, null, p.archived());
    }
    private AccountCommand accountCommand(String id, SyncPayload p) {
        return new AccountCommand(idOrPayload(id, p), p.name(), p.icon(), p.accountType(), p.currency(), p.openingBalance(), p.hidden());
    }
    private CategoryCommand categoryCommand(String id, SyncPayload p) {
        return new CategoryCommand(idOrPayload(id, p), p.name(), p.icon(), enumValue(CategoryKind.class, p.kind()), p.parentId(), p.color(), p.hidden());
    }
    private NamedResourceCommand namedCommand(String id, SyncPayload p) {
        return new NamedResourceCommand(idOrPayload(id, p), p.name(), p.icon(), p.color(), p.note(), p.hidden());
    }
    private MemberCommand memberCommand(String id, SyncPayload p) {
        return new MemberCommand(idOrPayload(id, p), p.username(), p.roleId(), p.icon());
    }
    private RoleCommand roleCommand(String id, SyncPayload p) {
        return new RoleCommand(idOrPayload(id, p), p.code(), p.name(), p.permissions());
    }
    private BudgetCommand budgetCommand(String id, SyncPayload p) {
        return new BudgetCommand(idOrPayload(id, p), p.categoryId(), p.scope(), p.monthKey(), p.budget());
    }
    private TransactionCommand transactionCommand(String id, SyncPayload p) {
        return new TransactionCommand(idOrPayload(id, p), p.accountId(), p.targetAccountId(), p.categoryId(),
                p.merchantId(), p.memberId(), p.projectId(), enumValue(TransactionKind.class, p.kind()), p.amount(),
                p.currency(), p.occurredOn(), p.payee(), p.member(), p.project(), p.note(), p.source(),
                p.clientOpId(), null, p.revision());
    }

    private SyncEntity readEntity(Object value, ResourceType type, SyncAction action) {
        try {
            if (action == SyncAction.DELETE) {
                return mapper.readValue(String.valueOf(value), DeletedResource.class);
            }
            Class<? extends SyncEntity> entityClass = switch (type) {
                case book -> Book.class;
                case account -> Account.class;
                case category -> Category.class;
                case merchant, project -> NamedResource.class;
                case member -> Member.class;
                case role -> Role.class;
                case budget -> Budget.class;
                case transaction -> Transaction.class;
            };
            return mapper.readValue(String.valueOf(value), entityClass);
        } catch (Exception exception) {
            throw new IllegalStateException("同步日志数据损坏", exception);
        }
    }

    private SyncPayload emptyPayload() {
        return new SyncPayload(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private String idOrPayload(String id, SyncPayload payload) {
        return text(id).isBlank() ? payload.id() : id;
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null || value.isBlank() ? null : Enum.valueOf(type, value);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return null;
        return list.stream().map(String::valueOf).toList();
    }

    private String textOr(Object value, String fallback) {
        String result = text(value);
        return result.isBlank() ? fallback : result;
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String textOrNull(Object value) { return value == null ? null : String.valueOf(value); }
    private String nullableText(Object value) { return value == null ? null : String.valueOf(value); }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); }
    private Long longOrNull(Object value) { return value == null ? null : number(value); }
    private BigDecimal decimal(Object value) { return new BigDecimal(String.valueOf(value)); }
    private BigDecimal decimalOrNull(Object value) { return value == null ? null : decimal(value); }
    private Boolean boolOrNull(Object value) {
        if (value == null) return null;
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0;
    }
    private LocalDate dateOrNull(Object value) {
        if (value == null) return null;
        return value instanceof LocalDate date ? date : LocalDate.parse(String.valueOf(value));
    }
}
