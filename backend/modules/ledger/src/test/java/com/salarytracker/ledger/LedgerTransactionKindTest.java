package com.salarytracker.ledger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerTransactionKindTest {
    @Test
    void recognizesAllSupportedTransactionKinds() {
        assertEquals("EXPENSE", LedgerRules.normalizeKind("支出"));
        assertEquals("INCOME", LedgerRules.normalizeKind("收入"));
        assertEquals("TRANSFER", LedgerRules.normalizeKind("转账"));
        assertEquals("TRANSFER", LedgerRules.normalizeKind("TRANSFER_OUT"));
        assertEquals("BORROW_IN", LedgerRules.normalizeKind("借入"));
        assertEquals("LEND_OUT", LedgerRules.normalizeKind("借出"));
        assertEquals("COLLECT_DEBT", LedgerRules.normalizeKind("收债"));
        assertEquals("REPAY_DEBT", LedgerRules.normalizeKind("还债"));
    }

    @Test
    void mapsTransactionKindsToCategoryDirections() {
        assertEquals("INCOME", LedgerRules.categoryKindForTransaction("收入"));
        assertEquals("INCOME", LedgerRules.categoryKindForTransaction("借入"));
        assertEquals("INCOME", LedgerRules.categoryKindForTransaction("收债"));
        assertEquals("EXPENSE", LedgerRules.categoryKindForTransaction("支出"));
        assertEquals("EXPENSE", LedgerRules.categoryKindForTransaction("借出"));
        assertEquals("EXPENSE", LedgerRules.categoryKindForTransaction("还债"));
        assertEquals(null, LedgerRules.categoryKindForTransaction("转账"));
    }
}
