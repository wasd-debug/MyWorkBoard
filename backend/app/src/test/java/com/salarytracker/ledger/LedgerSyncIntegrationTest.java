package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.salarytracker.ledger.LedgerModels.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerSyncIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pushesPullsAndIdempotentlyReplaysAllOfflineResourceTypes() {
        Fixture fixture = fixture("sync-all");
        String accountId = uuid();
        String primaryCategoryId = uuid();
        String categoryId = uuid();
        String merchantId = uuid();
        String projectId = uuid();
        String budgetId = uuid();
        String transactionId = uuid();
        List<SyncOperationRequest> operations = List.of(
                op("op-account", "account", accountId, Map.of(
                        "id", accountId, "name", "现金", "accountType", "cash", "currency", "CNY")),
                op("op-category-primary", "category", primaryCategoryId, Map.of(
                        "id", primaryCategoryId, "name", "餐饮", "kind", "EXPENSE")),
                op("op-category-child", "category", categoryId, Map.of(
                        "id", categoryId, "name", "午餐", "kind", "EXPENSE", "parentId", primaryCategoryId)),
                op("op-merchant", "merchant", merchantId, Map.of(
                        "id", merchantId, "name", "街角餐厅")),
                op("op-project", "project", projectId, Map.of(
                        "id", projectId, "name", "日常", "color", "#0f5132")),
                op("op-budget", "budget", budgetId, Map.of(
                        "id", budgetId, "monthKey", "2026-09", "budget", "1200.00", "categoryId", primaryCategoryId)),
                op("op-transaction", "transaction", transactionId, Map.ofEntries(
                        Map.entry("id", transactionId), Map.entry("kind", "EXPENSE"),
                        Map.entry("amount", "28.50"), Map.entry("occurredOn", "2026-09-17"),
                        Map.entry("accountId", accountId), Map.entry("categoryId", categoryId),
                        Map.entry("merchantId", merchantId), Map.entry("projectId", projectId))));

        SyncPushResponse first = fixture.sync.push(fixture.bookId, operations);
        assertEquals(7L, first.applied());
        assertTrue(first.results().stream().allMatch(item -> item.status() == SyncStatus.APPLIED));

        SyncPullResponse pulled = fixture.sync.pull(fixture.bookId, 0, 500);
        Set<ResourceType> types = pulled.operations().stream()
                .map(SyncChange::entityType).collect(java.util.stream.Collectors.toSet());
        assertTrue(types.containsAll(Set.of(ResourceType.account, ResourceType.category,
                ResourceType.merchant, ResourceType.project, ResourceType.budget, ResourceType.transaction)));

        SyncPushResponse replay = fixture.sync.push(fixture.bookId, operations);
        assertEquals(7L, replay.duplicates());
        assertTrue(replay.results().stream().allMatch(item -> item.status() == SyncStatus.DUPLICATE));
        assertEquals(1L, count("ledger_account", accountId));
        assertEquals(1L, count("ledger_transaction", transactionId));
    }

    @Test
    void separatesRevisionConflictsFromValidationRejectionsAndKeepsTheOpId() {
        Fixture fixture = fixture("sync-errors");
        String accountId = uuid();
        fixture.sync.push(fixture.bookId, List.of(op("create-account", "account", accountId,
                Map.of("id", accountId, "name", "现金", "accountType", "cash"))));

        SyncPushResponse response = fixture.sync.push(fixture.bookId, List.of(
                op("stale-account", "account", accountId, "0", Map.of("id", accountId, "name", "旧名称")),
                op("invalid-budget", "budget", uuid(), Map.of(
                        "id", uuid(), "monthKey", "2026-09", "budget", 0))));

        List<SyncOperationResult> results = response.results();
        assertEquals("stale-account", results.get(0).opId());
        assertEquals(SyncStatus.CONFLICT, results.get(0).status());
        assertEquals(1L, results.get(0).serverRevision());
        assertEquals("invalid-budget", results.get(1).opId());
        assertEquals(SyncStatus.REJECTED, results.get(1).status());
    }

    private Fixture fixture(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', ?)", username, prefix);
        long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(userId, username, prefix, Set.of("ledger:read", "ledger:write")), null, List.of()));
        CurrentUserResolver currentUser = new CurrentUserResolver();
        LedgerBookAccess access = new LedgerBookAccess(jdbc, currentUser);
        String bookId = access.ensureDefaultBook();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LedgerAuditService audit = new LedgerAuditService(jdbc, mapper, access);
        LedgerBookService books = new LedgerBookService(jdbc, mapper, access, audit);
        LedgerTransactionService transactions = new LedgerTransactionService(jdbc, mapper, access, books, audit);
        return new Fixture(bookId, new LedgerSyncService(jdbc, mapper, access, books, transactions));
    }

    private SyncOperationRequest op(String opId, String type, String entityId, Map<String, Object> payload) {
        return op(opId, type, entityId, null, payload);
    }

    private SyncOperationRequest op(String opId, String type, String entityId,
                                    String baseRevision, Map<String, Object> payload) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new SyncOperationRequest(opId, ResourceType.valueOf(type), entityId, SyncAction.UPSERT,
                baseRevision == null ? null : Long.valueOf(baseRevision), mapper.convertValue(payload, SyncPayload.class));
    }

    private long count(String table, String publicId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE public_id=?", Long.class, publicId);
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private record Fixture(String bookId, LedgerSyncService sync) {
    }
}
