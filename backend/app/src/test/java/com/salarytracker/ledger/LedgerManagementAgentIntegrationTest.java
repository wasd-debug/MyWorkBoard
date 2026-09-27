package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerManagementAgentIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void countsNamedResourceUsageAndKeepsHistoricalTransactionAfterSoftDelete() {
        Fixture fixture = fixture("agent-resource");
        NamedResource merchant = fixture.inTransaction(() -> fixture.books.createNamedResource(fixture.bookId,
                "merchant", new NamedResourceCommand(null, "京东", "shop", null, "电商", false), "merchant-create"));
        NamedResource project = fixture.inTransaction(() -> fixture.books.createNamedResource(fixture.bookId,
                "project", new NamedResourceCommand(null, "国庆旅行", "folder", "#123456", null, false), "project-create"));
        fixture.inTransaction(() -> fixture.transactions.create(fixture.bookId,
                new TransactionCommand(null, fixture.accountId, null, fixture.categoryId,
                        merchant.id(), null, project.id(), TransactionKind.EXPENSE, new BigDecimal("199"),
                        "CNY", LocalDate.of(2026, 9, 27), null, null, null, "旅行用品", "agent",
                        null, null, null), "transaction-create"));

        assertEquals(1L, fixture.books.namedResourceUsageCount(fixture.bookId, "merchant", merchant.id()));
        assertEquals(1L, fixture.books.namedResourceUsageCount(fixture.bookId, "project", project.id()));
        fixture.inTransaction(() -> fixture.books.deleteNamedResource(fixture.bookId, "merchant", merchant.id(),
                String.valueOf(merchant.revision()), "merchant-delete"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?) AND deleted=FALSE",
                Long.class, fixture.bookId));
    }

    @Test
    void budgetUpdateChecksRevisionAndReportsCurrentSpend() {
        Fixture fixture = fixture("agent-budget");
        fixture.inTransaction(() -> fixture.transactions.create(fixture.bookId,
                new TransactionCommand(null, fixture.accountId, null, fixture.categoryId, null, null, null,
                        TransactionKind.EXPENSE, new BigDecimal("300"), "CNY", LocalDate.of(2026, 10, 5),
                        null, null, null, "餐费", "agent", null, null, null), "budget-spend"));
        Budget created = fixture.inTransaction(() -> fixture.books.upsertBudget(fixture.bookId,
                new BudgetCommand(null, fixture.categoryId, "CATEGORY", "2026-10", new BigDecimal("1500")),
                "0", "budget-create"));

        assertEquals(new BigDecimal("300.00"), fixture.books.budgetSpent(
                fixture.bookId, "2026-10", fixture.categoryId).setScale(2));
        assertThrows(ConflictException.class, () -> fixture.inTransaction(() -> fixture.books.upsertBudget(
                fixture.bookId,
                new BudgetCommand(null, fixture.categoryId, "CATEGORY", "2026-10", new BigDecimal("1600")),
                "0", "budget-created-concurrently")));
        Budget updated = fixture.inTransaction(() -> fixture.books.upsertBudget(fixture.bookId,
                new BudgetCommand(null, fixture.categoryId, "CATEGORY", "2026-10", new BigDecimal("1800")),
                String.valueOf(created.revision()), "budget-update"));
        assertEquals(new BigDecimal("1800.00"), updated.budget().setScale(2));
        assertThrows(ConflictException.class, () -> fixture.inTransaction(() -> fixture.books.upsertBudget(
                fixture.bookId,
                new BudgetCommand(null, fixture.categoryId, "CATEGORY", "2026-10", new BigDecimal("2000")),
                String.valueOf(created.revision()), "budget-stale")));
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
        Account account = books.createAccount(bookId, new AccountCommand(null, "现金", "wallet", "cash",
                "CNY", BigDecimal.ZERO, false), prefix + "-account");
        Category primary = books.createCategory(bookId, new CategoryCommand(null, "餐饮", "food",
                CategoryKind.EXPENSE, null, "#e18b41", false), prefix + "-primary");
        Category secondary = books.createCategory(bookId, new CategoryCommand(null, "午餐", "food",
                CategoryKind.EXPENSE, primary.id(), "#e18b41", false), prefix + "-secondary");
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        return new Fixture(bookId, account.id(), secondary.id(), books, transactions, tx);
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

    private record Fixture(String bookId, String accountId, String categoryId, LedgerBookService books,
                           LedgerTransactionService transactions, TransactionTemplate transaction) {
        <T> T inTransaction(java.util.function.Supplier<T> supplier) {
            return transaction.execute(status -> supplier.get());
        }
    }
}
