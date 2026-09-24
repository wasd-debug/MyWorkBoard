package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionRepository;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Account;
import com.salarytracker.ledger.LedgerModels.Category;
import com.salarytracker.ledger.LedgerModels.CategoryKind;
import com.salarytracker.ledger.LedgerModels.Member;
import com.salarytracker.ledger.LedgerModels.NamedResource;
import com.salarytracker.ledger.LedgerModels.Transaction;
import com.salarytracker.ledger.LedgerModels.TransactionCommand;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.platform.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerWriteToolsTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LedgerBookService books = mock(LedgerBookService.class);
    private final LedgerTransactionService transactions = mock(LedgerTransactionService.class);
    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    private final MemoryRepository repository = new MemoryRepository();
    private final PendingActionService actions = new PendingActionService(repository, mapper, new InteractionPolicy());

    LedgerWriteToolsTest() {
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.required()).thenReturn(new CurrentUser(7L, "alice", "Alice",
                Set.of("ledger:read", "ledger:write")));
        when(books.accounts("book-1", false)).thenReturn(List.of(
                new Account("account-1", "现金", "wallet", "CASH", "CNY", BigDecimal.ZERO,
                        BigDecimal.ZERO, false, 1, "2026-09-01"),
                new Account("account-2", "中行卡", "bank-boc", "BANK", "CNY", BigDecimal.ZERO,
                        BigDecimal.ZERO, false, 1, "2026-09-01")));
        when(books.categories("book-1", false)).thenReturn(List.of(
                new Category("parent-1", "餐饮", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1, "2026-09-01"),
                new Category("category-1", "午餐", "tag", CategoryKind.EXPENSE, "parent-1",
                        "#fff", false, 1, "2026-09-01"),
                new Category("parent-2", "学习进修", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1, "2026-09-01"),
                new Category("category-2", "软件", "tag", CategoryKind.EXPENSE, "parent-2",
                        "#fff", false, 1, "2026-09-01")));
        when(books.merchants("book-1", false)).thenReturn(List.of(
                new NamedResource("merchant-1", "中转站", "shop", null, null, false, 1, "2026-09-01")));
        when(books.projects("book-1", false)).thenReturn(List.of(
                new NamedResource("project-1", "个人成长", "folder", null, "#fff", false, 1, "2026-09-01")));
        when(books.members("book-1")).thenReturn(List.of(
                new Member("member-1", 7L, 7L, "alice", "Alice", "Alice", "role-1",
                        "OWNER", "所有者", "user", 1, "2026-09-01")));
    }

    @Test
    void prepareReturnsWhitelistedFormWithoutWriting() {
        var tool = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);

        var result = tool.execute(mapper.createObjectNode().put("bookId", "book-1").put("kind", "EXPENSE"));

        assertEquals(ToolStatus.NEEDS_INPUT, result.status());
        assertEquals("amount", result.structuredContent().path("missingFields").path(0).asText());
        assertEquals(9, result.structuredContent().path("fields").size());
        assertEquals("kind", result.structuredContent().path("fields").path(0).path("name").asText());
        assertEquals("money", result.structuredContent().path("fields").path(1).path("type").asText());
        assertEquals("account-1", result.structuredContent().path("input").path("accountId").asText());
        assertEquals("category-1", result.structuredContent().path("input").path("categoryId").asText());
        assertEquals("member-1", result.structuredContent().path("input").path("memberId").asText());
        assertTrue(result.structuredContent().path("suggestedFields").toString().contains("\"memberId\""));
        verify(transactions, never()).create(any(), any(), any());
    }

    @Test
    void prepareIncludesAllEditableFieldsAndCommitKeepsSelectedResources() {
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);
        var commit = new LedgerTransactionCreateCommitTool(transactions, actions, currentUser, mapper);
        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("kind", "EXPENSE").put("amount", 50).put("accountId", "account-1")
                .put("categoryId", "category-1").put("merchantId", "merchant-1")
                .put("memberId", "member-1").put("projectId", "project-1")
                .put("occurredOn", "2026-09-24").put("note", "中转站"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("餐饮 / 午餐", prepared.structuredContent().path("preview").path("categoryPath").asText());
        assertEquals("中转站", prepared.structuredContent().path("preview").path("merchantName").asText());
        assertEquals("Alice", prepared.structuredContent().path("preview").path("memberName").asText());
        assertEquals("个人成长", prepared.structuredContent().path("preview").path("projectName").asText());

        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(transactions).create(eq("book-1"), org.mockito.ArgumentMatchers.argThat(command ->
                "merchant-1".equals(command.merchantId()) && "member-1".equals(command.memberId())
                        && "project-1".equals(command.projectId())), eq(prepared.actionId()));
    }

    @Test
    void prepareMatchesHumanReadableAccountAndCategoryPath() {
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("kind", "EXPENSE").put("amount", 29.9)
                .put("accountName", "中行卡").put("categoryName", "学习进修软件")
                .put("merchantName", "中转站").put("occurredOn", "2026-09-24"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("account-2", prepared.structuredContent().path("input").path("accountId").asText());
        assertEquals("category-2", prepared.structuredContent().path("input").path("categoryId").asText());
        assertEquals("merchant-1", prepared.structuredContent().path("input").path("merchantId").asText());
        assertEquals("member-1", prepared.structuredContent().path("input").path("memberId").asText());
        assertEquals("Alice", prepared.structuredContent().path("preview").path("memberName").asText());
        assertEquals("学习进修 / 软件", prepared.structuredContent().path("preview").path("categoryPath").asText());
    }

    @Test
    void prepareKeepsExplicitMemberInsteadOfReplacingItWithCurrentUser() {
        when(books.members("book-1")).thenReturn(List.of(
                new Member("member-1", 7L, 7L, "alice", "Alice", "Alice", "role-1",
                        "OWNER", "所有者", "user", 1, "2026-09-01"),
                new Member("member-2", 8L, 7L, "bob", "Bob", "Bob", "role-2",
                        "MEMBER", "成员", "user", 1, "2026-09-01")));
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("kind", "EXPENSE").put("amount", 29.9)
                .put("accountId", "account-1").put("categoryId", "category-1")
                .put("memberName", "Bob").put("occurredOn", "2026-09-24"));

        assertEquals("member-2", prepared.structuredContent().path("input").path("memberId").asText());
        assertEquals("Bob", prepared.structuredContent().path("preview").path("memberName").asText());
    }

    @Test
    void prepareRejectsPrimaryOrMismatchedCategory() {
        var tool = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);
        var base = mapper.createObjectNode().put("bookId", "book-1").put("kind", "EXPENSE")
                .put("amount", 28.5).put("accountId", "account-1");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(base.deepCopy().put("categoryId", "parent-1")));
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(base.deepCopy().put("kind", "INCOME").put("categoryId", "category-1")));
    }

    @Test
    void updatePrepareKeepsExistingResourcesAndCommitUsesOriginalRevision() {
        Transaction original = transaction(4L, new BigDecimal("29.90"), "normal");
        when(transactions.transaction("book-1", "transaction-1")).thenReturn(original);
        when(transactions.update(eq("book-1"), eq("transaction-1"), any(), eq("4"), any()))
                .thenReturn(transaction(5L, new BigDecimal("35.00"), "release"));
        var prepare = new LedgerTransactionUpdatePrepareTool(transactions, books, actions, currentUser, mapper);
        var commit = new LedgerTransactionUpdateCommitTool(transactions, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("transactionId", "transaction-1").put("amount", 35).put("note", "release"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("account-1", prepared.structuredContent().path("input").path("accountId").asText());
        assertEquals("member-1", prepared.structuredContent().path("input").path("memberId").asText());
        assertEquals(2, prepared.structuredContent().path("diff").size());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(transactions).update(eq("book-1"), eq("transaction-1"),
                org.mockito.ArgumentMatchers.argThat((TransactionCommand value) ->
                        value.amount().compareTo(new BigDecimal("35")) == 0 && "member-1".equals(value.memberId())),
                eq("4"), eq(prepared.actionId()));
    }

    @Test
    void updatePrepareRejectsUnsupportedTransactionKind() {
        when(transactions.transaction("book-1", "transaction-1")).thenReturn(new Transaction(
                1L, "transaction-1", "account-1", "现金", "wallet", "account-2", "中行卡", "bank", "group-1",
                null, null, null, null, null, null, null, null, null, null, null, null,
                "member-1", "user", "alice", "Alice", null, null, null, null, null,
                "TRANSFER", TransactionKind.TRANSFER, new BigDecimal("100"), "CNY", java.time.LocalDate.of(2026, 9, 24),
                "", "manual", "op-1", 2, false, null, 7L, "2026-09-24", "2026-09-24"));
        var prepare = new LedgerTransactionUpdatePrepareTool(transactions, books, actions, currentUser, mapper);

        assertThrows(IllegalArgumentException.class, () -> prepare.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("transactionId", "transaction-1").put("amount", 120)));
    }

    @Test
    void updateCommitReturnsConflictAndCannotOverwriteNewerTransaction() {
        when(transactions.transaction("book-1", "transaction-1"))
                .thenReturn(transaction(4L, new BigDecimal("29.90"), "normal"));
        when(transactions.update(eq("book-1"), eq("transaction-1"), any(), eq("4"), any()))
                .thenThrow(new ConflictException("资源版本已变化", 5));
        var prepare = new LedgerTransactionUpdatePrepareTool(transactions, books, actions, currentUser, mapper);
        var commit = new LedgerTransactionUpdateCommitTool(transactions, actions, currentUser, mapper);
        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("transactionId", "transaction-1").put("amount", 35));

        actions.approve(prepared.actionId(), 7L);
        var result = commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId()));

        assertEquals(ToolStatus.CONFLICT, result.status());
        assertEquals(5L, result.structuredContent().path("latestRevision").asLong());
        assertThrows(IllegalStateException.class,
                () -> commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())));
    }

    @Test
    void commitRequiresApprovalAndUsesActionIdAsIdempotencyKey() {
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);
        var commit = new LedgerTransactionCreateCommitTool(transactions, actions, currentUser, mapper);
        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("kind", "EXPENSE").put("amount", 28.5).put("accountId", "account-1")
                .put("categoryId", "category-1").put("occurredOn", "2026-09-23").put("note", "午餐"));
        var input = mapper.createObjectNode().put("actionId", prepared.actionId());

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertThrows(IllegalStateException.class, () -> commit.execute(input));
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED, commit.execute(input).status());
        assertThrows(IllegalStateException.class, () -> commit.execute(input));
        verify(transactions).create(eq("book-1"), any(), eq(prepared.actionId()));
    }

    private Transaction transaction(long revision, BigDecimal amount, String note) {
        return new Transaction(1L, "transaction-1", "account-1", "现金", "wallet",
                null, null, null, null, "category-1", "午餐", "tag", "#fff",
                "parent-1", "餐饮", "tag", "#fff", "merchant-1", "中转站", "shop", "中转站",
                "member-1", "user", "alice", "Alice", "project-1", "folder", "#fff", "个人成长", "个人成长",
                "EXPENSE", TransactionKind.EXPENSE, amount, "CNY", java.time.LocalDate.of(2026, 9, 24), note,
                "manual", "op-1", revision, false, null, 7L, "2026-09-24", "2026-09-24");
    }

    private static final class MemoryRepository implements PendingActionRepository {
        private final Map<String, PendingAction> actions = new ConcurrentHashMap<>();
        @Override public void insert(PendingAction action) { actions.put(action.id(), action); }
        @Override public Optional<PendingAction> find(String id, long userId) {
            return Optional.ofNullable(actions.get(id)).filter(action -> action.userId() == userId);
        }
        @Override public boolean transition(String id, long userId, ActionStatus expected,
                                            ActionStatus next, Instant updatedAt) {
            final boolean[] changed = {false};
            actions.computeIfPresent(id, (key, current) -> {
                if (current.userId() != userId || current.status() != expected) return current;
                changed[0] = true;
                return current.transition(next, updatedAt);
            });
            return changed[0];
        }
    }
}
