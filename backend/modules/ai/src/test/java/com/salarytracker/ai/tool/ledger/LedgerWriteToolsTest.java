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
import com.salarytracker.ledger.LedgerModels.Book;
import com.salarytracker.ledger.LedgerModels.Budget;
import com.salarytracker.ledger.LedgerModels.Category;
import com.salarytracker.ledger.LedgerModels.CategoryKind;
import com.salarytracker.ledger.LedgerModels.Member;
import com.salarytracker.ledger.LedgerModels.NamedResource;
import com.salarytracker.ledger.LedgerModels.Transaction;
import com.salarytracker.ledger.LedgerModels.TransactionCommand;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.ledger.LedgerImportService;
import com.salarytracker.ledger.LedgerScheduledTaskService;
import com.salarytracker.ledger.LedgerModels.CalendarRule;
import com.salarytracker.ledger.LedgerModels.ScheduledTask;
import com.salarytracker.ledger.LedgerModels.ScheduledTaskRun;
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
    private final LedgerScheduledTaskService schedules = mock(LedgerScheduledTaskService.class);
    private final LedgerImportService imports = mock(LedgerImportService.class);
    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    private final MemoryRepository repository = new MemoryRepository();
    private final PendingActionService actions = new PendingActionService(repository, mapper, new InteractionPolicy());

    LedgerWriteToolsTest() {
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.required()).thenReturn(new CurrentUser(7L, "alice", "Alice",
                Set.of("ledger:read", "ledger:write")));
        when(books.books()).thenReturn(List.of(
                new Book("book-1", "日常账本", "CNY", 7L, 1, false, "OWNER", "所有者",
                        List.of("TRANSACTION_ANY_WRITE"), 1, 0, "2026-09-01")));
        when(books.accounts("book-1", false)).thenReturn(List.of(
                new Account("account-1", "现金", "wallet", "CASH", "CNY", BigDecimal.ZERO,
                        BigDecimal.ZERO, false, 1, "2026-09-01"),
                new Account("account-2", "中行卡", "bank-boc", "BANK", "CNY", BigDecimal.ZERO,
                        BigDecimal.ZERO, false, 1, "2026-09-01")));
        when(books.accounts("book-1", true)).thenAnswer(invocation -> books.accounts("book-1", false));
        when(books.categories("book-1", false)).thenReturn(List.of(
                new Category("parent-1", "餐饮", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1, "2026-09-01"),
                new Category("category-1", "午餐", "tag", CategoryKind.EXPENSE, "parent-1",
                        "#fff", false, 1, "2026-09-01"),
                new Category("parent-2", "学习进修", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1, "2026-09-01"),
                new Category("category-2", "软件", "tag", CategoryKind.EXPENSE, "parent-2",
                        "#fff", false, 1, "2026-09-01")));
        when(books.categories("book-1", true)).thenAnswer(invocation -> books.categories("book-1", false));
        when(books.merchants("book-1", false)).thenReturn(List.of(
                new NamedResource("merchant-1", "中转站", "shop", null, null, false, 1, "2026-09-01")));
        when(books.merchants("book-1", true)).thenAnswer(invocation -> books.merchants("book-1", false));
        when(books.projects("book-1", false)).thenReturn(List.of(
                new NamedResource("project-1", "个人成长", "folder", null, "#fff", false, 1, "2026-09-01")));
        when(books.projects("book-1", true)).thenAnswer(invocation -> books.projects("book-1", false));
        when(books.budgets("book-1", "2026-10")).thenReturn(List.of(
                new Budget("budget-1", "parent-1", "餐饮", "CATEGORY", "2026-10",
                        new BigDecimal("1500"), new BigDecimal("300"), 4)));
        when(books.budgetSpent("book-1", "2026-10", "parent-1")).thenReturn(new BigDecimal("300"));
        when(books.members("book-1")).thenReturn(List.of(
                new Member("member-1", 7L, 7L, "alice", "Alice", "Alice", "role-1",
                        "OWNER", "所有者", "user", 1, "2026-09-01")));
        when(schedules.previewFirstRun(any())).thenReturn(java.time.LocalDate.of(2026, 10, 1));
        when(schedules.list("book-1", false)).thenReturn(List.of(schedule()));
        when(books.recycle("book-1", 1, 100)).thenReturn(new com.salarytracker.ledger.LedgerModels.RecyclePage(
                List.of(new com.salarytracker.ledger.LedgerModels.RecycleItem(
                        com.salarytracker.ledger.LedgerModels.ResourceType.transaction, "transaction-1", "午餐",
                        "2026-09-27T10:00:00Z", 3, 7, "2026-09-26", new BigDecimal("29.90"))),
                1, 100, 1, 1));
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
        assertTrue(result.structuredContent().path("input").path("accountId").isNull());
        assertTrue(result.structuredContent().path("input").path("categoryId").isNull());
        assertEquals(List.of("accountId", "categoryId"), mapper.convertValue(
                result.structuredContent().path("ambiguousFields"),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        assertEquals("member-1", result.structuredContent().path("input").path("memberId").asText());
        assertTrue(result.structuredContent().path("suggestedFields").toString().contains("\"memberId\""));
        verify(transactions, never()).create(any(), any(), any());
    }

    @Test
    void prepareRequiresSelectionForAmbiguousAccountAndCategoryInsteadOfPickingFirst() {
        when(books.accounts("book-1", false)).thenReturn(List.of(
                new Account("account-boc-credit", "中行信用卡", "bank-boc", "CREDIT_CARD", "CNY",
                        BigDecimal.ZERO, BigDecimal.ZERO, false, 1, "2026-09-01"),
                new Account("account-boc-debit", "中行储蓄卡", "bank-boc", "BANK", "CNY",
                        BigDecimal.ZERO, BigDecimal.ZERO, false, 1, "2026-09-01")));
        when(books.categories("book-1", false)).thenReturn(List.of(
                new Category("parent-study", "学习进修", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1, "2026-09-01"),
                new Category("category-study-software", "软件", "tag", CategoryKind.EXPENSE, "parent-study",
                        "#fff", false, 1, "2026-09-01"),
                new Category("parent-work", "工作支出", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1, "2026-09-01"),
                new Category("category-work-software", "软件", "tag", CategoryKind.EXPENSE, "parent-work",
                        "#fff", false, 1, "2026-09-01")));
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);

        var result = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("kind", "EXPENSE").put("amount", 29.9)
                .put("accountName", "中行").put("categoryName", "软件")
                .put("occurredOn", "2026-09-24"));

        assertEquals(ToolStatus.NEEDS_INPUT, result.status());
        assertTrue(result.structuredContent().path("input").path("accountId").isNull());
        assertTrue(result.structuredContent().path("input").path("categoryId").isNull());
        assertEquals("ambiguous", result.structuredContent().path("entityMatches").path("accountId").path("status").asText());
        assertEquals(2, result.structuredContent().path("entityMatches").path("accountId").path("candidates").size());
        assertEquals("ambiguous", result.structuredContent().path("entityMatches").path("categoryId").path("status").asText());
        assertEquals(List.of("accountId", "categoryId"), mapper.convertValue(
                result.structuredContent().path("ambiguousFields"),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        verify(transactions, never()).create(any(), any(), any());
    }

    @Test
    void prepareUsesOnlyAvailableAccountAndCategoryButKeepsThemEditable() {
        when(books.accounts("book-1", false)).thenReturn(List.of(
                new Account("only-account", "现金", "wallet", "CASH", "CNY", BigDecimal.ZERO,
                        BigDecimal.ZERO, false, 1, "2026-09-01")));
        when(books.categories("book-1", false)).thenReturn(List.of(
                new Category("parent-food", "餐饮", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1, "2026-09-01"),
                new Category("only-category", "午餐", "tag", CategoryKind.EXPENSE, "parent-food",
                        "#fff", false, 1, "2026-09-01")));
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);

        var result = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("kind", "EXPENSE").put("amount", 20).put("occurredOn", "2026-09-24"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, result.status());
        assertEquals("only-account", result.structuredContent().path("input").path("accountId").asText());
        assertEquals("only-category", result.structuredContent().path("input").path("categoryId").asText());
        assertEquals("suggested", result.structuredContent().path("entityMatches").path("accountId").path("status").asText());
        assertEquals("suggested", result.structuredContent().path("entityMatches").path("categoryId").path("status").asText());
    }

    @Test
    void prepareRequiresBookSelectionWhenUserCanAccessMultipleBooks() {
        when(books.books()).thenReturn(List.of(
                new Book("book-1", "日常账本", "CNY", 7L, 1, false, "OWNER", "所有者", List.of(), 1, 0, "2026-09-01"),
                new Book("book-2", "工作账本", "CNY", 7L, 1, false, "OWNER", "所有者", List.of(), 1, 0, "2026-09-02")));
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);

        var result = prepare.execute(mapper.createObjectNode().put("kind", "EXPENSE").put("amount", 20));

        assertEquals(ToolStatus.NEEDS_INPUT, result.status());
        assertEquals("ambiguous", result.structuredContent().path("entityMatches").path("bookId").path("status").asText());
        assertEquals(2, result.structuredContent().path("entityMatches").path("bookId").path("candidates").size());
        assertEquals(5, result.structuredContent().path("fields").size());
        assertEquals("bookId", result.structuredContent().path("fields").path(3).path("name").asText());
        assertEquals("entity-picker", result.structuredContent().path("fields").path(3).path("type").asText());
    }

    @Test
    void prepareRequiresSelectionWhenAnOptionalNamedEntityHasAmbiguousMatches() {
        when(books.merchants("book-1", false)).thenReturn(List.of(
                new NamedResource("merchant-1", "中转站北京", "shop", "北京节点", null, false, 1, "2026-09-01"),
                new NamedResource("merchant-2", "中转站上海", "shop", "上海节点", null, false, 1, "2026-09-01")));
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);

        var result = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("kind", "EXPENSE").put("amount", 29.9)
                .put("accountId", "account-1").put("categoryId", "category-1")
                .put("merchantName", "中转站").put("occurredOn", "2026-09-24"));

        assertEquals(ToolStatus.NEEDS_INPUT, result.status());
        assertTrue(result.structuredContent().path("input").path("merchantId").isNull());
        assertEquals("ambiguous", result.structuredContent().path("entityMatches").path("merchantId").path("status").asText());
        assertTrue(result.structuredContent().path("missingFields").toString().contains("merchantId"));
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
    void prepareAndCommitSupportTransferWithoutCategory() {
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);
        var commit = new LedgerTransactionCreateCommitTool(transactions, actions, currentUser, mapper);
        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("kind", "TRANSFER").put("amount", 1000)
                .put("accountId", "account-1").put("targetAccountId", "account-2")
                .put("occurredOn", "2026-09-27").put("note", "资金归集"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("中行卡", prepared.structuredContent().path("preview").path("targetAccountName").asText());
        assertTrue(prepared.structuredContent().path("input").path("categoryId").isNull());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(transactions).create(eq("book-1"), org.mockito.ArgumentMatchers.argThat(command ->
                command.kind() == TransactionKind.TRANSFER
                        && "account-1".equals(command.accountId())
                        && "account-2".equals(command.targetAccountId())
                        && command.categoryId() == null), eq(prepared.actionId()));
    }

    @Test
    void prepareSupportsDebtKindsWithoutInventingCategory() {
        var prepare = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);
        for (String kind : List.of("BORROW_IN", "LEND_OUT", "COLLECT_DEBT", "REPAY_DEBT")) {
            var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                    .put("kind", kind).put("amount", 500).put("accountId", "account-1")
                    .put("merchantId", "merchant-1").put("occurredOn", "2026-09-27"));
            assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
            assertEquals(kind, prepared.structuredContent().path("preview").path("kind").asText());
            assertTrue(prepared.structuredContent().path("input").path("categoryId").isNull());
        }
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
    void updatePrepareSupportsTransferAndKeepsPairedAccounts() {
        Transaction transfer = new Transaction(
                1L, "transaction-1", "account-1", "现金", "wallet", "account-2", "中行卡", "bank", "group-1",
                null, null, null, null, null, null, null, null, null, null, null, null,
                "member-1", "user", "alice", "Alice", null, null, null, null, null,
                "TRANSFER", TransactionKind.TRANSFER, new BigDecimal("100"), "CNY", java.time.LocalDate.of(2026, 9, 24),
                "", "manual", "op-1", 2, false, null, 7L, "2026-09-24", "2026-09-24");
        when(transactions.transaction("book-1", "transaction-1")).thenReturn(transfer);
        var prepare = new LedgerTransactionUpdatePrepareTool(transactions, books, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("transactionId", "transaction-1").put("amount", 120));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("account-2", prepared.structuredContent().path("preview").path("targetAccountId").asText());
        assertEquals("中行卡", prepared.structuredContent().path("preview").path("targetAccountName").asText());
        assertThrows(IllegalArgumentException.class, () -> prepare.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("transactionId", "transaction-1").put("kind", "EXPENSE")));
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
    void deletePrepareShowsCompleteTransactionAndCommitUsesOriginalRevision() {
        when(transactions.transaction("book-1", "transaction-1"))
                .thenReturn(transaction(4L, new BigDecimal("29.90"), "午餐"));
        when(transactions.delete("book-1", "transaction-1", "4", "action-ignored"))
                .thenReturn(null);
        var prepare = new LedgerTransactionDeletePrepareTool(transactions, actions, currentUser, mapper);
        var commit = new LedgerTransactionDeleteCommitTool(transactions, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("transactionId", "transaction-1"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("ledger.transaction.delete", prepared.structuredContent().path("actionType").asText());
        assertEquals("餐饮 / 午餐", prepared.structuredContent().path("preview").path("categoryPath").asText());
        assertEquals("Alice", prepared.structuredContent().path("preview").path("memberName").asText());
        assertEquals(3, prepared.structuredContent().path("effects").size());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(transactions).delete("book-1", "transaction-1", "4", prepared.actionId());
        assertThrows(IllegalStateException.class,
                () -> commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())));
    }

    @Test
    void deletePrepareSupportsTransferAndCommitReportsRevisionConflict() {
        Transaction transfer = new Transaction(
                1L, "transaction-1", "account-1", "现金", "wallet", "account-2", "中行卡", "bank", "group-1",
                null, null, null, null, null, null, null, null, null, null, null, null,
                "member-1", "user", "alice", "Alice", null, null, null, null, null,
                "TRANSFER", TransactionKind.TRANSFER, new BigDecimal("100"), "CNY", java.time.LocalDate.of(2026, 9, 24),
                "", "manual", "op-1", 2, false, null, 7L, "2026-09-24", "2026-09-24");
        when(transactions.transaction("book-1", "transaction-1")).thenReturn(transfer);
        var prepare = new LedgerTransactionDeletePrepareTool(transactions, actions, currentUser, mapper);
        var transferPrepared = prepare.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("transactionId", "transaction-1"));
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, transferPrepared.status());
        assertEquals("中行卡", transferPrepared.structuredContent().path("preview").path("targetAccountName").asText());
        assertEquals(4, transferPrepared.structuredContent().path("effects").size());

        when(transactions.transaction("book-1", "transaction-1"))
                .thenReturn(transaction(4L, new BigDecimal("29.90"), "午餐"));
        var prepared = prepare.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("transactionId", "transaction-1"));
        when(transactions.delete("book-1", "transaction-1", "4", prepared.actionId()))
                .thenThrow(new ConflictException("资源版本已变化", 5));
        var commit = new LedgerTransactionDeleteCommitTool(transactions, actions, currentUser, mapper);
        actions.approve(prepared.actionId(), 7L);
        var result = commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.CONFLICT, result.status());
        assertEquals(5L, result.structuredContent().path("latestRevision").asLong());
    }

    @Test
    void batchCreateUsesOneActionAndOneTransactionalServiceCall() {
        var single = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);
        var prepare = new LedgerTransactionsBatchCreatePrepareTool(single, actions, currentUser, mapper);
        var commit = new LedgerTransactionsBatchCreateCommitTool(transactions, actions, currentUser, mapper);
        var items = mapper.createArrayNode();
        items.addObject().put("kind", "EXPENSE").put("amount", 28)
                .put("accountId", "account-1").put("categoryId", "category-1").put("occurredOn", "2026-09-27");
        items.addObject().put("kind", "TRANSFER").put("amount", 1000)
                .put("accountId", "account-1").put("targetAccountId", "account-2").put("occurredOn", "2026-09-27");
        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1").set("items", items));
        when(transactions.createBatch(eq("book-1"), any(), eq(prepared.actionId())))
                .thenReturn(List.of(transaction(1, new BigDecimal("28"), "午餐"), transaction(1, new BigDecimal("1000"), "转账")));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(2, prepared.structuredContent().path("totals").path("count").asInt());
        assertEquals(1000, prepared.structuredContent().path("totals").path("transfer").decimalValue().intValue());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(transactions).createBatch(eq("book-1"), org.mockito.ArgumentMatchers.argThat(commands ->
                commands.size() == 2 && commands.get(1).kind() == TransactionKind.TRANSFER), eq(prepared.actionId()));
    }

    @Test
    void batchDeleteFreezesIdsAndRevisionsBeforeCommit() {
        Transaction first = transaction(4L, new BigDecimal("29.90"), "午餐");
        Transaction second = new Transaction(2L, "transaction-2", "account-2", "中行卡", "bank",
                null, null, null, null,
                null, null, null, null, null, null, null, null,
                "merchant-1", "中转站", "shop", "张三",
                "member-1", "user", "alice", "Alice",
                null, null, null, null, null,
                "BORROW_IN", TransactionKind.BORROW_IN,
                new BigDecimal("500"), "CNY", java.time.LocalDate.of(2026, 9, 27), "借入",
                "manual", "op-2", 6, false, null, 7L, "2026-09-27", "2026-09-27");
        when(transactions.transaction("book-1", "transaction-1")).thenReturn(first);
        when(transactions.transaction("book-1", "transaction-2")).thenReturn(second);
        var prepare = new LedgerTransactionsBatchDeletePrepareTool(transactions, actions, currentUser, mapper);
        var commit = new LedgerTransactionsBatchDeleteCommitTool(transactions, actions, currentUser, mapper);
        var ids = mapper.createArrayNode().add("transaction-1").add("transaction-2");
        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1").set("transactionIds", ids));
        when(transactions.deleteBatch(eq("book-1"), any(), eq(prepared.actionId()))).thenReturn(List.of());

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(4, prepared.structuredContent().path("input").path("items").path(0).path("revision").asLong());
        assertEquals(6, prepared.structuredContent().path("input").path("items").path(1).path("revision").asLong());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(transactions).deleteBatch(eq("book-1"), org.mockito.ArgumentMatchers.argThat(commands ->
                commands.size() == 2 && commands.get(0).revision() == 4 && commands.get(1).revision() == 6),
                eq(prepared.actionId()));
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

    @Test
    void accountCreatePrepareUsesBookDefaultsAndCommitCallsExistingDomainService() {
        var prepare = new LedgerManagementPrepareTool(LedgerManagementToolMode.ACCOUNT_CREATE,
                books, actions, currentUser, mapper);
        var commit = new LedgerManagementCommitTool(LedgerManagementToolMode.ACCOUNT_CREATE,
                books, actions, currentUser, mapper);
        when(books.createAccount(eq("book-1"), any(), any())).thenReturn(
                new Account("account-new", "招商银行卡", "bank-card", "bank", "CNY",
                        BigDecimal.ZERO, BigDecimal.ZERO, false, 1, "2026-09-27"));

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("name", "招商银行卡").put("accountType", "bank"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("CNY", prepared.structuredContent().path("input").path("currency").asText());
        assertEquals(6, prepared.structuredContent().path("fields").size());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(books).createAccount(eq("book-1"), org.mockito.ArgumentMatchers.argThat(command ->
                "招商银行卡".equals(command.name()) && "bank".equals(command.accountType())), eq(prepared.actionId()));
    }

    @Test
    void categoryUpdateFreezesRevisionAndDeleteShowsSoftDeleteEffects() {
        var update = new LedgerManagementPrepareTool(LedgerManagementToolMode.CATEGORY_UPDATE,
                books, actions, currentUser, mapper);
        var prepared = update.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("resourceId", "category-1").put("name", "工作餐"));
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(1L, repository.find(prepared.actionId(), 7L).orElseThrow().expectedRevision());
        assertTrue(prepared.structuredContent().path("diff").toString().contains("工作餐"));

        var delete = new LedgerManagementPrepareTool(LedgerManagementToolMode.CATEGORY_DELETE,
                books, actions, currentUser, mapper);
        var deleting = delete.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("resourceId", "category-1"));
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, deleting.status());
        assertTrue(deleting.structuredContent().path("effects").toString().contains("软删除"));
    }

    @Test
    void merchantCreateAndDeleteReuseNamedResourceDomainService() {
        var prepare = new LedgerManagementPrepareTool(LedgerManagementToolMode.MERCHANT_CREATE,
                books, actions, currentUser, mapper);
        var commit = new LedgerManagementCommitTool(LedgerManagementToolMode.MERCHANT_CREATE,
                books, actions, currentUser, mapper);
        when(books.createNamedResource(eq("book-1"), eq("merchant"), any(), any())).thenReturn(
                new NamedResource("merchant-new", "京东", "shop", "电商平台", null,
                        false, 1, "2026-09-27"));

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("name", "京东").put("note", "电商平台"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("shop", prepared.structuredContent().path("input").path("icon").asText());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(books).createNamedResource(eq("book-1"), eq("merchant"),
                org.mockito.ArgumentMatchers.argThat(command -> "京东".equals(command.name())
                        && "电商平台".equals(command.note())), eq(prepared.actionId()));

        when(books.namedResourceUsageCount("book-1", "merchant", "merchant-1")).thenReturn(12L);
        var deleting = new LedgerManagementPrepareTool(LedgerManagementToolMode.MERCHANT_DELETE,
                books, actions, currentUser, mapper).execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("resourceId", "merchant-1"));
        assertEquals(1L, repository.find(deleting.actionId(), 7L).orElseThrow().expectedRevision());
        assertTrue(deleting.structuredContent().path("effects").toString().contains("12 笔"));
    }

    @Test
    void projectUpdateFreezesRevisionAndKeepsProjectSpecificFields() {
        var prepare = new LedgerManagementPrepareTool(LedgerManagementToolMode.PROJECT_UPDATE,
                books, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("resourceId", "project-1").put("name", "职业成长").put("color", "#123456"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(1L, repository.find(prepared.actionId(), 7L).orElseThrow().expectedRevision());
        assertEquals("#123456", prepared.structuredContent().path("input").path("color").asText());
        assertTrue(prepared.structuredContent().path("diff").toString().contains("职业成长"));
    }

    @Test
    void budgetUpsertShowsUsageAndCommitUsesFrozenRevision() {
        var prepare = new LedgerManagementPrepareTool(LedgerManagementToolMode.BUDGET_UPSERT,
                books, actions, currentUser, mapper);
        var commit = new LedgerManagementCommitTool(LedgerManagementToolMode.BUDGET_UPSERT,
                books, actions, currentUser, mapper);
        when(books.upsertBudget(eq("book-1"), any(), eq("4"), any())).thenReturn(
                new Budget("budget-1", "parent-1", "餐饮", "CATEGORY", "2026-10",
                        new BigDecimal("1800"), new BigDecimal("300"), 5));

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("monthKey", "2026-10").put("categoryId", "parent-1").put("budget", 1800));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(4L, repository.find(prepared.actionId(), 7L).orElseThrow().expectedRevision());
        assertEquals("餐饮", prepared.structuredContent().path("preview").path("categoryName").asText());
        assertEquals("300", prepared.structuredContent().path("preview").path("spent").asText());
        assertEquals("16.67", prepared.structuredContent().path("preview").path("usageRate").asText());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(books).upsertBudget(eq("book-1"), org.mockito.ArgumentMatchers.argThat(command ->
                "2026-10".equals(command.monthKey()) && "parent-1".equals(command.categoryId())
                        && new BigDecimal("1800").compareTo(command.budget()) == 0),
                eq("4"), eq(prepared.actionId()));
    }

    @Test
    void newTotalBudgetFreezesExpectedAbsenceBeforeCommit() {
        when(books.budgets("book-1", "2026-11")).thenReturn(List.of());
        when(books.budgetSpent("book-1", "2026-11", null)).thenReturn(BigDecimal.ZERO);
        when(books.upsertBudget(eq("book-1"), any(), eq("0"), any())).thenReturn(
                new Budget("budget-total", null, "月度总预算", "TOTAL", "2026-11",
                        new BigDecimal("8000"), BigDecimal.ZERO, 1));
        var prepare = new LedgerManagementPrepareTool(LedgerManagementToolMode.BUDGET_UPSERT,
                books, actions, currentUser, mapper);
        var commit = new LedgerManagementCommitTool(LedgerManagementToolMode.BUDGET_UPSERT,
                books, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("monthKey", "2026-11").put("budget", 8000));

        assertEquals(0L, repository.find(prepared.actionId(), 7L).orElseThrow().expectedRevision());
        assertEquals("月度总预算", prepared.structuredContent().path("preview").path("categoryName").asText());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(books).upsertBudget(eq("book-1"), any(), eq("0"), eq(prepared.actionId()));
    }

    @Test
    void budgetDeleteRequiresMonthAndFreezesBudgetRevision() {
        var prepare = new LedgerManagementPrepareTool(LedgerManagementToolMode.BUDGET_DELETE,
                books, actions, currentUser, mapper);

        var incomplete = prepare.execute(mapper.createObjectNode().put("bookId", "book-1"));
        assertEquals(ToolStatus.NEEDS_INPUT, incomplete.status());
        assertTrue(incomplete.structuredContent().path("missingFields").toString().contains("monthKey"));

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("monthKey", "2026-10").put("resourceId", "budget-1"));
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(4L, repository.find(prepared.actionId(), 7L).orElseThrow().expectedRevision());
        assertTrue(prepared.structuredContent().path("effects").toString().contains("当前已使用 300"));
    }

    @Test
    void bookDeleteOnlyBuildsR4PreviewWithoutCommitTool() {
        var prepare = new LedgerManagementPrepareTool(LedgerManagementToolMode.BOOK_DELETE,
                books, actions, currentUser, mapper);
        var result = prepare.execute(mapper.createObjectNode().put("bookId", "book-1"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, result.status());
        assertEquals(com.salarytracker.ai.tool.ToolRisk.R4, prepare.definition().riskLevel());
        assertTrue(result.structuredContent().path("webApprovalRequired").asBoolean());
        assertEquals(false, result.structuredContent().path("commitAvailable").asBoolean());
        verify(books, never()).deleteBook(any(), any());
    }

    @Test
    void scheduleCreateBuildsCompletePreviewAndCommitsThroughDomainService() {
        when(schedules.create(eq("book-1"), any())).thenReturn(schedule());
        var prepare = new LedgerSchedulePrepareTool(LedgerScheduleToolMode.CREATE,
                schedules, books, actions, currentUser, mapper);
        var commit = new LedgerScheduleCommitTool(LedgerScheduleToolMode.CREATE,
                schedules, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("name", "每月房租").put("scheduleMode", "CALENDAR").put("frequency", "MONTHLY")
                .put("dayOfMonth", 1).put("startOn", "2026-10-01").put("kind", "EXPENSE")
                .put("amount", 3500).put("accountName", "现金").put("categoryName", "餐饮 / 午餐"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals("2026-10-01", prepared.structuredContent().path("preview").path("after").path("nextRunOn").asText());
        assertEquals("member-1", prepared.structuredContent().path("input").path("memberId").asText());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(schedules).create(eq("book-1"), org.mockito.ArgumentMatchers.argThat(command ->
                "每月房租".equals(command.name()) && command.payload().amount().compareTo(new BigDecimal("3500")) == 0
                        && "account-1".equals(command.payload().accountId())
                        && "category-1".equals(command.payload().categoryId())));
    }

    @Test
    void scheduleRunFreezesRevisionAndDuplicateResultCompletesWithoutSecondWrite() {
        when(schedules.run("book-1", "schedule-1", "3")).thenReturn(
                new ScheduledTaskRun("DUPLICATE", "schedule-1", null,
                        java.time.LocalDate.of(2026, 10, 1), null));
        var prepare = new LedgerSchedulePrepareTool(LedgerScheduleToolMode.RUN,
                schedules, books, actions, currentUser, mapper);
        var commit = new LedgerScheduleCommitTool(LedgerScheduleToolMode.RUN,
                schedules, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1").put("taskId", "schedule-1"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(3L, repository.find(prepared.actionId(), 7L).orElseThrow().expectedRevision());
        assertTrue(prepared.structuredContent().path("effects").toString().contains("不会再次记账"));
        actions.approve(prepared.actionId(), 7L);
        assertEquals("DUPLICATE", commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId()))
                .structuredContent().path("status").asText());
        verify(schedules).run("book-1", "schedule-1", "3");
    }

    @Test
    void scheduleUpdateReturnsConflictWhenRevisionChanged() {
        when(schedules.update(eq("book-1"), eq("schedule-1"), any(), eq("3")))
                .thenThrow(new ConflictException("定时任务版本已变化", 4));
        var prepare = new LedgerSchedulePrepareTool(LedgerScheduleToolMode.UPDATE,
                schedules, books, actions, currentUser, mapper);
        var commit = new LedgerScheduleCommitTool(LedgerScheduleToolMode.UPDATE,
                schedules, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("taskId", "schedule-1").put("amount", 3800));
        actions.approve(prepared.actionId(), 7L);

        var result = commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.CONFLICT, result.status());
        assertEquals(4, result.structuredContent().path("latestRevision").asInt());
    }

    @Test
    void recycleRestoreFreezesRevisionAndCommitsThroughExistingService() {
        when(transactions.restore(eq("book-1"), eq("transaction-1"), eq("3"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(transaction(4, new BigDecimal("29.90"), "午餐"));
        var prepare = new LedgerRecycleRestorePrepareTool(books, actions, currentUser, mapper);
        var commit = new LedgerRecycleRestoreCommitTool(books, transactions, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("itemId", "transaction-1").put("resourceType", "transaction"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(3L, repository.find(prepared.actionId(), 7L).orElseThrow().expectedRevision());
        assertEquals("午餐", prepared.structuredContent().path("preview").path("name").asText());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(transactions).restore("book-1", "transaction-1", "3", prepared.actionId());
    }

    @Test
    void recyclePurgeOnlyProducesR4WebApprovalPreview() {
        var tool = new LedgerRecyclePurgePrepareTool(books, actions, currentUser, mapper);

        var result = tool.execute(mapper.createObjectNode().put("bookId", "book-1")
                .put("itemId", "transaction-1").put("resourceType", "transaction"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, result.status());
        assertEquals(com.salarytracker.ai.tool.ToolRisk.R4, tool.definition().riskLevel());
        assertTrue(result.structuredContent().path("webApprovalRequired").asBoolean());
        assertEquals(false, result.structuredContent().path("commitAvailable").asBoolean());
        verify(transactions, never()).purge(any(), any());
    }

    @Test
    void exportPrepareAndCommitValidateThenReturnAuthenticatedDownloadMetadata() {
        when(transactions.list(eq("book-1"), any())).thenReturn(
                new com.salarytracker.ledger.LedgerModels.TransactionPage(List.of(), 1, 1, 12, 12,
                        new com.salarytracker.ledger.LedgerModels.TransactionSummary(BigDecimal.ZERO, BigDecimal.ZERO, 0)));
        when(imports.export("book-1", "xlsx", "2026-09-01", "2026-09-27"))
                .thenReturn(new byte[]{1, 2, 3});
        var prepare = new LedgerExportPrepareTool(books, transactions, actions, currentUser, mapper);
        var commit = new LedgerExportCommitTool(imports, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("bookId", "book-1").put("format", "xlsx")
                .put("from", "2026-09-01").put("to", "2026-09-27"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(12, prepared.structuredContent().path("preview").path("estimatedCount").asInt());
        actions.approve(prepared.actionId(), 7L);
        var result = commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId()));
        assertEquals(ToolStatus.COMPLETED, result.status());
        assertEquals(3, result.structuredContent().path("byteSize").asInt());
    }

    @Test
    void importPreviewRequiresFileThenReturnsReadOnlyStructuredPreview() {
        var preview = new com.salarytracker.ledger.LedgerModels.ImportPreview("batch-1", "ledger.xlsx", "STANDARD",
                1, 1, 1, List.of(new com.salarytracker.ledger.LedgerModels.ImportRow("流水", 2, "VALID", List.of(),
                TransactionKind.EXPENSE, java.time.LocalDate.of(2026, 9, 27), "餐饮", "午餐", "现金", null,
                new BigDecimal("29.90"), "Alice", "便利店", null, "午餐", "import")),
                new com.salarytracker.ledger.LedgerModels.ResourceCreates(List.of(), List.of(), List.of(), List.of()));
        when(imports.getPreview("book-1", "batch-1")).thenReturn(preview);
        var tool = new LedgerImportPreviewPrepareTool(books, imports, actions, currentUser, mapper);

        var waiting = tool.execute(mapper.createObjectNode().put("bookId", "book-1"));
        assertEquals(ToolStatus.NEEDS_INPUT, waiting.status());
        assertEquals("file", waiting.structuredContent().path("fields").path(0).path("type").asText());

        var parsed = tool.execute(mapper.createObjectNode().put("bookId", "book-1").put("batchId", "batch-1"));
        assertEquals(ToolStatus.NEEDS_CONFIRMATION, parsed.status());
        assertEquals(1, parsed.structuredContent().path("preview").path("validCount").asInt());
        assertTrue(parsed.structuredContent().path("webApprovalRequired").asBoolean());
        assertEquals(false, parsed.structuredContent().path("commitAvailable").asBoolean());
        verify(imports, never()).confirm(any(), any());
    }

    private ScheduledTask schedule() {
        return new ScheduledTask("schedule-1", "RECURRING_TRANSACTION", "每月房租", true,
                "CALENDAR", "MONTHLY", 1, new CalendarRule("DAY_OF_MONTH", null, null, 1, null),
                java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 1), null, null, 2,
                new TransactionCommand(null, "account-1", null, "category-1", "merchant-1", "member-1",
                        "project-1", TransactionKind.EXPENSE, new BigDecimal("3500"), "CNY", null,
                        null, null, null, "房租", "scheduled-task", null, null, null),
                "2026-09-01T08:00:00Z", "APPLIED", null, 3, false);
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
