package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Book;
import com.salarytracker.ledger.LedgerModels.Budget;
import com.salarytracker.ledger.LedgerModels.Overview;
import com.salarytracker.ledger.LedgerModels.NamedResource;
import com.salarytracker.ledger.LedgerModels.TransactionPage;
import com.salarytracker.ledger.LedgerModels.TransactionSummary;
import com.salarytracker.ledger.LedgerModels.TransactionVersion;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.ledger.LedgerScheduledTaskService;
import com.salarytracker.ledger.LedgerModels.CalendarRule;
import com.salarytracker.ledger.LedgerModels.ScheduledTask;
import com.salarytracker.ledger.LedgerModels.TransactionCommand;
import com.salarytracker.ledger.LedgerModels.TransactionKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerQueryToolsTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void booksToolReturnsOnlyServiceVisibleBooks() {
        LedgerBookService service = mock(LedgerBookService.class);
        Book book = new Book("book-1", "日常账本", "CNY", 7L, 1L, false,
                "OWNER", "账本主人", List.of("TRANSACTION_ANY_WRITE"), 1L, 12L, "2026-09-01");
        when(service.books()).thenReturn(List.of(book));

        var result = new LedgerBooksListTool(service, mapper).execute(mapper.createObjectNode());

        assertEquals(ToolStatus.COMPLETED, result.status());
        assertEquals("book-1", result.structuredContent().path(0).path("id").asText());
    }

    @Test
    void overviewToolRequiresBookAndDelegatesAccessCheckToDomainService() {
        LedgerTransactionService service = mock(LedgerTransactionService.class);
        Overview overview = new Overview("2026-09-01", "2026-09-20",
                new BigDecimal("1000"), new BigDecimal("400"), new BigDecimal("600"),
                List.of(), List.of(), List.of());
        when(service.overview("book-1", "2026-09-01", "2026-09-20")).thenReturn(overview);
        LedgerOverviewTool tool = new LedgerOverviewTool(service, mapper);

        var result = tool.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("from", "2026-09-01").put("to", "2026-09-20"));

        assertEquals(0, new BigDecimal("600").compareTo(
                result.structuredContent().path("balance").decimalValue()));
        verify(service).overview("book-1", "2026-09-01", "2026-09-20");
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(mapper.createObjectNode().put("from", "2026-09-01")));
    }

    @Test
    void transactionsSearchAppliesPageBoundsAndDateValidation() {
        LedgerTransactionService service = mock(LedgerTransactionService.class);
        when(service.list(org.mockito.ArgumentMatchers.eq("book-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TransactionPage(List.of(), 1, 20, 0, 1,
                        new TransactionSummary(BigDecimal.ZERO, BigDecimal.ZERO, 0)));
        LedgerTransactionsSearchTool tool = new LedgerTransactionsSearchTool(service, mapper);

        var result = tool.execute(mapper.createObjectNode().put("bookId", "book-1").put("pageSize", 20));

        assertEquals(ToolStatus.COMPLETED, result.status());
        assertThrows(IllegalArgumentException.class, () -> tool.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("pageSize", 101)));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("from", "2026-09-20").put("to", "2026-09-01")));
    }

    @Test
    void budgetsListValidatesMonth() {
        LedgerBookService service = mock(LedgerBookService.class);
        Budget budget = new Budget("budget-1", null, null, "BOOK", "2026-09",
                new BigDecimal("1000"), new BigDecimal("250"), 1);
        when(service.budgets("book-1", "2026-09")).thenReturn(List.of(budget));
        LedgerBudgetsListTool tool = new LedgerBudgetsListTool(service, mapper);

        var result = tool.execute(mapper.createObjectNode().put("bookId", "book-1").put("month", "2026-09"));

        assertEquals(1, result.structuredContent().path("returned").asInt());
        assertThrows(IllegalArgumentException.class, () -> tool.execute(mapper.createObjectNode()
                .put("bookId", "book-1").put("month", "2026/09")));
    }

    @Test
    void namedResourceListsDelegateBookVisibilityAndHiddenFilter() {
        LedgerBookService service = mock(LedgerBookService.class);
        when(service.merchants("book-1", true)).thenReturn(List.of(
                new NamedResource("merchant-1", "京东", "shop", "电商", null,
                        false, 1, "2026-09-27")));

        var result = new LedgerNamedResourceListTool("merchant", "商家", service, mapper).execute(
                mapper.createObjectNode().put("bookId", "book-1").put("includeHidden", true));

        assertEquals(ToolStatus.COMPLETED, result.status());
        assertEquals("京东", result.structuredContent().path(0).path("name").asText());
        verify(service).merchants("book-1", true);
    }

    @Test
    void scheduleListReturnsRevisionAndExecutionState() {
        LedgerScheduledTaskService service = mock(LedgerScheduledTaskService.class);
        ScheduledTask task = new ScheduledTask("schedule-1", "RECURRING_TRANSACTION", "每月房租", true,
                "CALENDAR", "MONTHLY", 1, new CalendarRule("DAY_OF_MONTH", null, null, 1, null),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), null, null, 2,
                new TransactionCommand(null, "account-1", null, "category-1", null, null, null,
                        TransactionKind.EXPENSE, new BigDecimal("3500"), "CNY", null, null, null,
                        null, "房租", "scheduled-task", null, null, null),
                null, null, null, 3, false);
        when(service.list("book-1", false)).thenReturn(List.of(task));

        var result = new LedgerScheduleListTool(service, mapper).execute(
                mapper.createObjectNode().put("bookId", "book-1"));

        assertEquals(ToolStatus.COMPLETED, result.status());
        assertEquals(3, result.structuredContent().path(0).path("revision").asInt());
        verify(service).list("book-1", false);
    }

    @Test
    void reportSummaryUsesDomainOverview() {
        LedgerTransactionService service = mock(LedgerTransactionService.class);
        Overview overview = new Overview("2026-09-01", "2026-09-30", BigDecimal.TEN,
                BigDecimal.ONE, new BigDecimal("9"), List.of(), List.of(), List.of());
        when(service.overview("book-1", null, null)).thenReturn(overview);

        var result = new LedgerReportsSummaryTool(service, mapper).execute(
                mapper.createObjectNode().put("bookId", "book-1"));

        assertEquals(new BigDecimal("9"), result.structuredContent().path("balance").decimalValue());
    }

    @Test
    void transactionHistoryDelegatesVisibilityAndReturnsVersions() {
        LedgerTransactionService service = mock(LedgerTransactionService.class);
        var transaction = new com.salarytracker.ledger.LedgerModels.Transaction(
                1L, "transaction-1", "account-1", "现金", "wallet",
                null, null, null, null, "category-1", "午餐", "tag", "#fff",
                "parent-1", "餐饮", "tag", "#fff", null, null, null, null,
                "member-1", "user", "alice", "Alice", null, null, null, null, null,
                "EXPENSE", com.salarytracker.ledger.LedgerModels.TransactionKind.EXPENSE,
                new BigDecimal("29.90"), "CNY", LocalDate.of(2026, 9, 24), "午餐",
                "manual", "op-1", 2, false, null, 7L, "2026-09-24", "2026-09-24");
        when(service.history("book-1", "transaction-1")).thenReturn(List.of(
                new TransactionVersion(2, "UPDATE", transaction, "Alice", "2026-09-24T12:00:00+08:00")));

        var result = new LedgerTransactionHistoryTool(service, mapper).execute(
                mapper.createObjectNode().put("bookId", "book-1").put("transactionId", "transaction-1"));

        assertEquals(ToolStatus.COMPLETED, result.status());
        assertEquals(2L, result.structuredContent().path(0).path("revision").asLong());
        verify(service).history("book-1", "transaction-1");
    }
}
