package com.salarytracker.ledger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerImportDateTest {
    @Test
    void normalizesTemplateDateTimeToLedgerDate() {
        assertEquals("2012-09-14", LedgerService.normalizeImportDate("2012-09-14 03:00:09"));
        assertEquals("2026-09-01", LedgerService.normalizeImportDate("2026/9/1"));
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
}
