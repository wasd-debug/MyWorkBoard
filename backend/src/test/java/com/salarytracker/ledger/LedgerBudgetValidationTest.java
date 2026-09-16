package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerBudgetValidationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LedgerBookService books = new LedgerBookService(jdbc, new ObjectMapper(),
            mock(LedgerBookAccess.class), mock(LedgerAuditService.class));
    private final LedgerBookAccess.Context context = new LedgerBookAccess.Context(
            12L, "book-id", 3L, 3L, 4L, 5L, "OWNER", Set.of("RESOURCE_MANAGE"));

    @Test
    void acceptsMissingBlankAndZeroCategoryAsTotalBudget() {
        assertNull(books.budgetCategoryId(context, null));
        assertNull(books.budgetCategoryId(context, ""));
        assertNull(books.budgetCategoryId(context, 0));
        assertNull(books.budgetCategoryId(context, " 0 "));
    }

    @Test
    void acceptsVisibleExpenseCategoryAtEitherLevel() {
        when(jdbc.queryForList(
                "SELECT id,kind,hidden FROM ledger_category WHERE public_id=? AND book_id=? AND deleted=FALSE",
                "expense-category", 12L))
                .thenReturn(List.of(Map.of("id", 31L, "kind", "EXPENSE", "hidden", false)));

        assertEquals(31L, books.budgetCategoryId(context, "expense-category"));
    }

    @Test
    void rejectsIncomeHiddenAndMissingCategories() {
        when(jdbc.queryForList(
                "SELECT id,kind,hidden FROM ledger_category WHERE public_id=? AND book_id=? AND deleted=FALSE",
                "income-category", 12L))
                .thenReturn(List.of(Map.of("id", 32L, "kind", "INCOME", "hidden", false)));
        when(jdbc.queryForList(
                "SELECT id,kind,hidden FROM ledger_category WHERE public_id=? AND book_id=? AND deleted=FALSE",
                "hidden-category", 12L))
                .thenReturn(List.of(Map.of("id", 33L, "kind", "EXPENSE", "hidden", true)));
        when(jdbc.queryForList(
                "SELECT id,kind,hidden FROM ledger_category WHERE public_id=? AND book_id=? AND deleted=FALSE",
                "missing-category", 12L)).thenReturn(List.of());

        assertEquals("预算只能关联支出分类", assertThrows(IllegalArgumentException.class,
                () -> books.budgetCategoryId(context, "income-category")).getMessage());
        assertEquals("隐藏分类不能设置预算", assertThrows(IllegalArgumentException.class,
                () -> books.budgetCategoryId(context, "hidden-category")).getMessage());
        assertEquals("分类不存在", assertThrows(IllegalArgumentException.class,
                () -> books.budgetCategoryId(context, "missing-category")).getMessage());
    }

    @Test
    void requiresValidMonthAndPositiveAmount() {
        assertEquals("2026-09", books.budgetMonth(Map.of("monthKey", "2026-09")));
        assertEquals(new BigDecimal("1200.50"), books.positiveBudgetAmount("1200.5"));
        assertEquals("monthKey 必填", assertThrows(IllegalArgumentException.class,
                () -> books.budgetMonth(Map.of())).getMessage());
        assertEquals("monthKey 格式不正确，应为 YYYY-MM", assertThrows(IllegalArgumentException.class,
                () -> books.budgetMonth(Map.of("monthKey", "2026-13"))).getMessage());
        assertEquals("amount 必填", assertThrows(IllegalArgumentException.class,
                () -> books.positiveBudgetAmount(null)).getMessage());
        assertEquals("预算金额必须大于 0", assertThrows(IllegalArgumentException.class,
                () -> books.positiveBudgetAmount(0)).getMessage());
        assertEquals("预算金额必须大于 0", assertThrows(IllegalArgumentException.class,
                () -> books.positiveBudgetAmount(-1)).getMessage());
    }
}
