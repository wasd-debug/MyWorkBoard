package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.salarytracker.ledger.LedgerModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerScheduleAgentIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void manualRunCreatesOneTransactionAndRejectsDuplicateDueDate() {
        Fixture fixture = fixture("agent-schedule-run");
        ScheduledTask task = fixture.schedules.create(fixture.bookId, command(fixture, "每月房租"));

        ScheduledTaskRun first = fixture.schedules.run(fixture.bookId, task.id(), String.valueOf(task.revision()));
        assertEquals("APPLIED", first.status());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?) AND source='scheduled-task' AND deleted=FALSE",
                Long.class, fixture.bookId));

        ScheduledTask duplicateTask = fixture.schedules.create(fixture.bookId, command(fixture, "重复保护测试"));
        Long internalId = jdbc.queryForObject("SELECT id FROM ledger_scheduled_task WHERE public_id=?", Long.class, duplicateTask.id());
        jdbc.update("INSERT INTO ledger_scheduled_task_run(task_id,due_on,status) VALUES(?,?,'APPLIED')",
                internalId, duplicateTask.nextRunOn());
        ScheduledTaskRun duplicate = fixture.schedules.run(
                fixture.bookId, duplicateTask.id(), String.valueOf(duplicateTask.revision()));

        assertEquals("DUPLICATE", duplicate.status());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?) AND source='scheduled-task' AND deleted=FALSE",
                Long.class, fixture.bookId));
    }

    @Test
    void updateAndDeleteRejectStaleRevisionAndKeepGeneratedTransactions() {
        Fixture fixture = fixture("agent-schedule-revision");
        ScheduledTask created = fixture.schedules.create(fixture.bookId, command(fixture, "每月房租"));
        fixture.schedules.run(fixture.bookId, created.id(), String.valueOf(created.revision()));
        ScheduledTask afterRun = fixture.schedules.list(fixture.bookId, false).get(0);
        ScheduledTask updated = fixture.schedules.update(fixture.bookId, created.id(),
                new ScheduledTaskCommand(null, null, "调整后房租", false, null, null, null,
                        null, null, null, null, null), String.valueOf(afterRun.revision()));

        assertThrows(ConflictException.class, () -> fixture.schedules.update(fixture.bookId, created.id(),
                new ScheduledTaskCommand(null, null, "陈旧修改", null, null, null, null,
                        null, null, null, null, null), String.valueOf(afterRun.revision())));
        assertThrows(ConflictException.class, () -> fixture.schedules.delete(
                fixture.bookId, created.id(), String.valueOf(afterRun.revision())));
        fixture.schedules.delete(fixture.bookId, created.id(), String.valueOf(updated.revision()));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?) AND deleted=FALSE",
                Long.class, fixture.bookId));
    }

    @Test
    void anotherUserCannotListOrRunBookSchedule() {
        Fixture fixture = fixture("agent-schedule-owner");
        ScheduledTask task = fixture.schedules.create(fixture.bookId, command(fixture, "每月房租"));
        long otherUser = createUser("agent-schedule-other");
        authenticate(otherUser, "agent-schedule-other");

        assertThrows(ForbiddenException.class, () -> fixture.schedules.list(fixture.bookId, false));
        assertThrows(ForbiddenException.class, () -> fixture.schedules.run(
                fixture.bookId, task.id(), String.valueOf(task.revision())));
    }

    private ScheduledTaskCommand command(Fixture fixture, String name) {
        return new ScheduledTaskCommand(null, "RECURRING_TRANSACTION", name, true,
                "CALENDAR", "MONTHLY", 1, new CalendarRule("DAY_OF_MONTH", null, null, 1, null),
                LocalDate.of(2026, 10, 1), null, null,
                new TransactionCommand(null, fixture.accountId, null, fixture.categoryId, null,
                        fixture.memberId, null, TransactionKind.EXPENSE, new BigDecimal("3500"), "CNY",
                        null, null, null, null, "房租", "scheduled-task", null, null, null));
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
        LedgerScheduledTaskService schedules = new LedgerScheduledTaskService(jdbc, mapper, access, transactions);
        Account account = books.createAccount(bookId, new AccountCommand(null, "现金", "wallet", "cash",
                "CNY", BigDecimal.ZERO, false), prefix + "-account");
        Category primary = books.createCategory(bookId, new CategoryCommand(null, "住房", "home",
                CategoryKind.EXPENSE, null, "#e18b41", false), prefix + "-primary");
        Category secondary = books.createCategory(bookId, new CategoryCommand(null, "房租", "home",
                CategoryKind.EXPENSE, primary.id(), "#e18b41", false), prefix + "-secondary");
        Member member = books.members(bookId).stream().filter(value -> value.userId() == userId).findFirst().orElseThrow();
        return new Fixture(bookId, account.id(), secondary.id(), member.id(), schedules);
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

    private record Fixture(String bookId, String accountId, String categoryId, String memberId,
                           LedgerScheduledTaskService schedules) { }
}
