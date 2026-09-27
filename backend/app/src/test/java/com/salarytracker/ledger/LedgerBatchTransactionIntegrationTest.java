package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.salarytracker.ledger.LedgerModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerBatchTransactionIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsMixedBatchAtomicallyAndReplaysIdempotently() {
        Fixture fixture = fixture("batch-create");
        List<TransactionCommand> commands = List.of(
                command(fixture.cashId, null, fixture.expenseCategoryId, TransactionKind.EXPENSE, "32.50"),
                command(fixture.cashId, fixture.bankId, null, TransactionKind.TRANSFER, "500"),
                command(fixture.bankId, null, null, TransactionKind.BORROW_IN, "1200"));

        List<Transaction> first = fixture.inTransaction(() -> fixture.transactions.createBatch(
                fixture.bookId, commands, "batch-create-op"));
        List<Transaction> replay = fixture.inTransaction(() -> fixture.transactions.createBatch(
                fixture.bookId, commands, "batch-create-op"));

        assertEquals(List.of(TransactionKind.EXPENSE, TransactionKind.TRANSFER, TransactionKind.BORROW_IN),
                first.stream().map(Transaction::kind).toList());
        assertEquals(first.stream().map(Transaction::id).toList(), replay.stream().map(Transaction::id).toList());
        assertEquals(4L, fixture.physicalTransactionCount());
        assertEquals(4L, fixture.syncCount("batch-create-op:%"));
    }

    @Test
    void rollsBackEarlierItemsWhenLaterCreateFails() {
        Fixture fixture = fixture("batch-rollback");
        List<TransactionCommand> commands = List.of(
                command(fixture.cashId, null, fixture.expenseCategoryId, TransactionKind.EXPENSE, "18"),
                command("missing-account", null, null, TransactionKind.BORROW_IN, "100"));

        assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() ->
                fixture.transactions.createBatch(fixture.bookId, commands, "batch-fail-op")));

        assertEquals(0L, fixture.physicalTransactionCount());
        assertEquals(0L, fixture.syncCount("batch-fail-op:%"));
    }

    @Test
    void rollsBackBatchDeleteWhenOneRevisionConflicts() {
        Fixture fixture = fixture("batch-delete-conflict");
        List<Transaction> created = fixture.inTransaction(() -> fixture.transactions.createBatch(fixture.bookId, List.of(
                command(fixture.cashId, null, fixture.expenseCategoryId, TransactionKind.EXPENSE, "20"),
                command(fixture.bankId, null, null, TransactionKind.LEND_OUT, "300")), "batch-delete-source"));
        Transaction changed = fixture.inTransaction(() -> fixture.transactions.update(fixture.bookId, created.get(1).id(),
                command(fixture.bankId, null, null, TransactionKind.LEND_OUT, "350"),
                String.valueOf(created.get(1).revision()), "change-before-delete"));

        assertEquals(2L, changed.revision());
        assertThrows(ConflictException.class, () -> fixture.inTransaction(() -> fixture.transactions.deleteBatch(
                fixture.bookId, List.of(
                        new TransactionDeleteCommand(created.get(0).id(), created.get(0).revision()),
                        new TransactionDeleteCommand(created.get(1).id(), created.get(1).revision())),
                "batch-delete-conflict-op")));

        assertFalse(fixture.transactions.transaction(fixture.bookId, created.get(0).id()).deleted());
        assertFalse(fixture.transactions.transaction(fixture.bookId, created.get(1).id()).deleted());
        assertEquals(0L, fixture.syncCount("batch-delete-conflict-op:%"));
    }

    @Test
    void deletesBothTransferSidesAndRejectsAnotherUser() {
        Fixture fixture = fixture("batch-delete-transfer");
        Transaction transfer = fixture.inTransaction(() -> fixture.transactions.createBatch(fixture.bookId,
                List.of(command(fixture.cashId, fixture.bankId, null, TransactionKind.TRANSFER, "88")),
                "batch-transfer-source")).get(0);

        fixture.inTransaction(() -> fixture.transactions.deleteBatch(fixture.bookId,
                List.of(new TransactionDeleteCommand(transfer.id(), transfer.revision())), "batch-transfer-delete"));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE transfer_group_id=? AND deleted=TRUE",
                Long.class, transfer.transferGroupId()));

        long outsider = createUser("batch-outsider");
        authenticate(outsider, "batch-outsider");
        assertThrows(ForbiddenException.class, () -> fixture.inTransaction(() -> fixture.transactions.createBatch(
                fixture.bookId, List.of(command(fixture.cashId, null, null, TransactionKind.BORROW_IN, "1")),
                "batch-outsider-op")));
    }

    private Fixture fixture(String prefix) {
        long userId = createUser(prefix);
        authenticate(userId, prefix);
        CurrentUserResolver currentUser = new CurrentUserResolver();
        LedgerBookAccess access = new LedgerBookAccess(jdbc, currentUser);
        String bookId = access.ensureDefaultBook();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LedgerAuditService audit = new LedgerAuditService(jdbc, mapper, access);
        LedgerBookService books = new LedgerBookService(jdbc, mapper, access, audit);
        LedgerTransactionService transactions = new LedgerTransactionService(jdbc, mapper, access, books, audit);
        Account cash = books.createAccount(bookId, new AccountCommand(null, "现金", "wallet", "cash",
                "CNY", BigDecimal.ZERO, false), prefix + "-cash");
        Account bank = books.createAccount(bookId, new AccountCommand(null, "银行卡", "bank-card", "bank",
                "CNY", BigDecimal.ZERO, false), prefix + "-bank");
        Category primary = books.createCategory(bookId, new CategoryCommand(null, "餐饮", "food",
                CategoryKind.EXPENSE, null, "#e18b41", false), prefix + "-category-primary");
        Category secondary = books.createCategory(bookId, new CategoryCommand(null, "午餐", "food",
                CategoryKind.EXPENSE, primary.id(), "#e18b41", false), prefix + "-category-secondary");
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        return new Fixture(bookId, cash.id(), bank.id(), secondary.id(), transactions, tx);
    }

    private TransactionCommand command(String accountId, String targetAccountId, String categoryId,
                                       TransactionKind kind, String amount) {
        return new TransactionCommand(null, accountId, targetAccountId, categoryId, null, null, null,
                kind, new BigDecimal(amount), "CNY", LocalDate.of(2026, 9, 27), null, null, null,
                kind.name().toLowerCase(), "agent", null, null, null);
    }

    private long createUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', ?)", username, prefix);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private void authenticate(long userId, String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(userId, username, username, Set.of("ledger:read", "ledger:write")), null, List.of()));
    }

    private record Fixture(String bookId, String cashId, String bankId, String expenseCategoryId,
                           LedgerTransactionService transactions, TransactionTemplate tx) {
        <T> T inTransaction(java.util.function.Supplier<T> work) {
            return tx.execute(status -> work.get());
        }

        long physicalTransactionCount() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transaction WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?)",
                    Long.class, bookId);
        }

        long syncCount(String pattern) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ledger_sync_oplog WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?) AND op_id LIKE ?",
                    Long.class, bookId, pattern);
        }
    }
}
