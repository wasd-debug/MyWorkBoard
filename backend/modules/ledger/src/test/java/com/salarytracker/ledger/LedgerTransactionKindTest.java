package com.salarytracker.ledger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerTransactionKindTest {
    @Test
    void recognizesAllSupportedTransactionKinds() {
        assertEquals("EXPENSE", LedgerService.normalizeKind("支出"));
        assertEquals("INCOME", LedgerService.normalizeKind("收入"));
        assertEquals("TRANSFER", LedgerService.normalizeKind("转账"));
        assertEquals("TRANSFER", LedgerService.normalizeKind("TRANSFER_OUT"));
        assertEquals("BORROW_IN", LedgerService.normalizeKind("借入"));
        assertEquals("LEND_OUT", LedgerService.normalizeKind("借出"));
        assertEquals("COLLECT_DEBT", LedgerService.normalizeKind("收债"));
        assertEquals("REPAY_DEBT", LedgerService.normalizeKind("还债"));
    }

    @Test
    void mapsTransactionKindsToCategoryDirections() {
        assertEquals("INCOME", LedgerService.categoryKindForTransaction("收入"));
        assertEquals("INCOME", LedgerService.categoryKindForTransaction("借入"));
        assertEquals("INCOME", LedgerService.categoryKindForTransaction("收债"));
        assertEquals("EXPENSE", LedgerService.categoryKindForTransaction("支出"));
        assertEquals("EXPENSE", LedgerService.categoryKindForTransaction("借出"));
        assertEquals("EXPENSE", LedgerService.categoryKindForTransaction("还债"));
        assertEquals(null, LedgerService.categoryKindForTransaction("转账"));
    }
}
