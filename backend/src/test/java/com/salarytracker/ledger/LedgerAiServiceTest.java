package com.salarytracker.ledger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class LedgerAiServiceTest {
    @Test
    void acceptsCategoryPathReturnedByModel() {
        assertArrayEquals(new String[]{"餐饮", "早餐"},
                LedgerAiService.categoryReference("餐饮 / 早餐", ""));
        assertArrayEquals(new String[]{"餐饮", "早餐"},
                LedgerAiService.categoryReference("早餐", "餐饮"));
    }

    @Test
    void keepsUnqualifiedCategoryNameForAmbiguityResolution() {
        assertArrayEquals(new String[]{"", "交通"},
                LedgerAiService.categoryReference("交通", ""));
    }
}
