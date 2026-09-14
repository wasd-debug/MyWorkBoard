package com.salarytracker.ledger;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerImportDateTest {
    @Test
    void normalizesTemplateDateTimeToLedgerDate() {
        assertEquals("2012-09-14", LedgerService.normalizeImportDate("2012-09-14 03:00:09"));
        assertEquals("2026-09-01", LedgerService.normalizeImportDate("2026/9/1"));
        assertEquals("2026-09-08", LedgerImportService.normalizeImportDate("2026-09-08 11:00:45"));
        assertEquals("2026-09-08", LedgerImportService.normalizeImportDate("2026/9/8 11:00:45"));
        assertEquals("2019-08-01", LedgerImportService.normalizeImportDate("2019-08-01 09:12:00"));
    }

    @Test
    void rejectsInvalidImportDate() {
        assertThrows(IllegalArgumentException.class, () -> LedgerService.normalizeImportDate("not-a-date"));
    }

    @Test
    void treatsNullExcelCellAsBlank() {
        assertEquals("", LedgerService.normalizeImportCell(null));
        assertEquals("支付宝", LedgerService.normalizeImportCell("  支付宝  "));
    }

    @Test
    void onlyIncomeAndExpenseRequireImportCategory() {
        assertTrue(LedgerImportService.requiresCategory("INCOME"));
        assertTrue(LedgerImportService.requiresCategory("EXPENSE"));
        assertFalse(LedgerImportService.requiresCategory("TRANSFER"));
        assertFalse(LedgerImportService.requiresCategory("BORROW_IN"));
        assertFalse(LedgerImportService.requiresCategory("LEND_OUT"));
        assertFalse(LedgerImportService.requiresCategory("COLLECT_DEBT"));
        assertFalse(LedgerImportService.requiresCategory("REPAY_DEBT"));
    }

    @Test
    void recognizesTransactionKindFromSuishoujiSheetName() {
        assertEquals("TRANSFER", LedgerImportService.kindFromSheet("转账"));
        assertEquals("EXPENSE", LedgerImportService.kindFromSheet("支出"));
        assertEquals("INCOME", LedgerImportService.kindFromSheet("收入"));
        assertEquals("BORROW_IN", LedgerImportService.kindFromSheet("借入"));
        assertEquals("LEND_OUT", LedgerImportService.kindFromSheet("借出"));
        assertEquals("COLLECT_DEBT", LedgerImportService.kindFromSheet("收债"));
        assertEquals("REPAY_DEBT", LedgerImportService.kindFromSheet("还债"));
    }

    @Test
    void duplicateDetectionKeepsAdditionalIdenticalSourceOccurrences() {
        assertTrue(LedgerImportService.isExistingOccurrence(1, 2));
        assertTrue(LedgerImportService.isExistingOccurrence(2, 2));
        assertFalse(LedgerImportService.isExistingOccurrence(3, 2));
        assertFalse(LedgerImportService.isExistingOccurrence(1, 0));
    }

    @Test
    void fingerprintCanonicalizesMissingPrimaryCategoryToOther() {
        Map<String, Object> source = Map.of(
                "kind", "EXPENSE",
                "occurredOn", "2026-09-08",
                "amount", "12.00",
                "account", "现金",
                "category", "早餐",
                "source", "import-suishouji");
        Map<String, Object> stored = Map.of(
                "kind", "EXPENSE",
                "occurredOn", "2026-09-08",
                "amount", "12.00",
                "account", "现金",
                "parentCategory", "其他",
                "category", "早餐",
                "source", "import-suishouji");
        assertEquals(LedgerImportService.fingerprint(source), LedgerImportService.fingerprint(stored));
    }

    @Test
    void categoryPaletteKeepsChildrenInParentHueFamily() {
        String parent = LedgerBookService.primaryCategoryColor("EXPENSE", "食品酒水", 0);
        String childA = LedgerBookService.childCategoryColor(parent, "食品酒水", 0);
        String childB = LedgerBookService.childCategoryColor(parent, "食品酒水", 1);
        assertTrue(parent.startsWith("hsl("));
        assertTrue(childA.startsWith("hsl("));
        assertTrue(childB.startsWith("hsl("));
        assertTrue(childA.split(" ")[0].equals(parent.split(" ")[0]));
        assertTrue(childB.split(" ")[0].equals(parent.split(" ")[0]));
        assertFalse(childA.equals(childB));
    }

    @Test
    void categoryPaletteAssignsDistinctFamiliesByPrimarySequence() {
        String first = LedgerBookService.primaryCategoryColor("EXPENSE", "食品酒水", 0);
        String second = LedgerBookService.primaryCategoryColor("INCOME", "工资收入", 1);
        assertNotEquals(first.split(" ")[0], second.split(" ")[0]);
    }
}
