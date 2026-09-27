package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.salarytracker.ledger.LedgerModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerDataLifecycleAgentIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recycleRestoreRequiresCurrentRevisionAndKeepsUserIsolation() {
        Fixture fixture = fixture("agent-recycle");
        Transaction created = fixture.transactions.create(fixture.bookId, transaction(fixture, "午餐"), "create-lunch");
        DeletedResource deleted = fixture.transactions.delete(
                fixture.bookId, created.id(), String.valueOf(created.revision()), "delete-lunch");
        RecycleItem item = fixture.books.recycle(fixture.bookId, 1, 20).items().stream()
                .filter(value -> value.id().equals(created.id())).findFirst().orElseThrow();

        assertEquals(deleted.revision(), item.revision());
        assertThrows(ConflictException.class, () -> fixture.transactions.restore(
                fixture.bookId, created.id(), String.valueOf(item.revision() - 1), "stale-restore"));
        Transaction restored = fixture.transactions.restore(
                fixture.bookId, created.id(), String.valueOf(item.revision()), "restore-lunch");
        assertEquals(false, restored.deleted());

        long otherUser = createUser("agent-recycle-other");
        authenticate(otherUser, "agent-recycle-other");
        assertThrows(ForbiddenException.class, () -> fixture.books.recycle(fixture.bookId, 1, 20));
    }

    @Test
    void importPreviewIsTemporaryUserScopedAndDoesNotCreateTransactions() throws Exception {
        Fixture fixture = fixture("agent-import");
        String csv = "交易类型,日期,一级分类,二级分类,收入/支出账户,金额,成员,商家,项目,备注\n" +
                "支出,2026-09-27,住房,房租,现金,29.90,,,,午餐\n";
        MockMultipartFile file = new MockMultipartFile("file", "ledger.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportPreview preview = fixture.imports.preview(fixture.bookId, file, "AUTO");
        ImportPreview restored = fixture.imports.getPreview(fixture.bookId, preview.batchId());

        assertEquals(preview.validCount(), restored.validCount());
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?)",
                Long.class, fixture.bookId));
        long otherUser = createUser("agent-import-other");
        authenticate(otherUser, "agent-import-other");
        assertThrows(ForbiddenException.class, () -> fixture.imports.getPreview(fixture.bookId, preview.batchId()));
    }

    @Test
    void csvExportNeutralizesSpreadsheetFormulaText() {
        Fixture fixture = fixture("agent-export");
        fixture.transactions.create(fixture.bookId,
                transaction(fixture, "=HYPERLINK(\"https://example.invalid\")"), "formula-note");

        String exported = new String(fixture.imports.export(
                fixture.bookId, "csv", "2026-09-01", "2026-09-30"), StandardCharsets.UTF_8);

        assertTrue(exported.contains("'=HYPERLINK"));
    }

    private TransactionCommand transaction(Fixture fixture, String note) {
        return new TransactionCommand(null, fixture.accountId, null, fixture.categoryId, null,
                fixture.memberId, null, TransactionKind.EXPENSE, new BigDecimal("29.90"), "CNY",
                LocalDate.of(2026, 9, 27), null, null, null, note, "manual", null, null, null);
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
        LedgerImportService imports = new LedgerImportService(jdbc, mapper, access, transactions);
        Account account = books.createAccount(bookId, new AccountCommand(null, "现金", "wallet", "cash",
                "CNY", BigDecimal.ZERO, false), prefix + "-account");
        Category primary = books.createCategory(bookId, new CategoryCommand(null, "住房", "home",
                CategoryKind.EXPENSE, null, "#e18b41", false), prefix + "-primary");
        Category secondary = books.createCategory(bookId, new CategoryCommand(null, "房租", "home",
                CategoryKind.EXPENSE, primary.id(), "#e18b41", false), prefix + "-secondary");
        Member member = books.members(bookId).stream().filter(value -> value.userId() == userId).findFirst().orElseThrow();
        return new Fixture(bookId, account.id(), secondary.id(), member.id(), books, transactions, imports);
    }

    private long createUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', ?)", username, prefix);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private void authenticate(long userId, String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(userId, username, username, Set.of("ledger:read", "ledger:write", "ledger:import")),
                null, List.of()));
    }

    private record Fixture(String bookId, String accountId, String categoryId, String memberId,
                           LedgerBookService books, LedgerTransactionService transactions,
                           LedgerImportService imports) { }
}
