package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Book;
import com.salarytracker.ledger.LedgerModels.Budget;
import com.salarytracker.ledger.LedgerModels.Overview;
import com.salarytracker.ledger.LedgerModels.TransactionPage;
import com.salarytracker.ledger.LedgerModels.TransactionSummary;
import com.salarytracker.ledger.LedgerTransactionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerQueryToolsTest {
    private final ObjectMapper mapper = new ObjectMapper();

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
    void reportSummaryUsesDomainOverview() {
        LedgerTransactionService service = mock(LedgerTransactionService.class);
        Overview overview = new Overview("2026-09-01", "2026-09-30", BigDecimal.TEN,
                BigDecimal.ONE, new BigDecimal("9"), List.of(), List.of(), List.of());
        when(service.overview("book-1", null, null)).thenReturn(overview);

        var result = new LedgerReportsSummaryTool(service, mapper).execute(
                mapper.createObjectNode().put("bookId", "book-1"));

        assertEquals(new BigDecimal("9"), result.structuredContent().path("balance").decimalValue());
    }
}
