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
import com.salarytracker.ledger.LedgerTransactionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerWriteToolsTest {
    private final ObjectMapper mapper = new ObjectMapper();
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
                        BigDecimal.ZERO, false, 1, "2026-09-01")));
        when(books.categories("book-1", false)).thenReturn(List.of(
                new Category("parent-1", "餐饮", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1, "2026-09-01"),
                new Category("category-1", "午餐", "tag", CategoryKind.EXPENSE, "parent-1",
                        "#fff", false, 1, "2026-09-01")));
    }

    @Test
    void prepareReturnsWhitelistedFormWithoutWriting() {
        var tool = new LedgerTransactionCreatePrepareTool(books, actions, currentUser, mapper);

        var result = tool.execute(mapper.createObjectNode().put("bookId", "book-1").put("kind", "EXPENSE"));

        assertEquals(ToolStatus.NEEDS_INPUT, result.status());
        assertEquals("amount", result.structuredContent().path("missingFields").path(0).asText());
        assertEquals("money", result.structuredContent().path("fields").path(0).path("type").asText());
        verify(transactions, never()).create(any(), any(), any());
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
