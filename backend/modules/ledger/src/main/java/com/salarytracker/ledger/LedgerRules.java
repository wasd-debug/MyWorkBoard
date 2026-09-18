package com.salarytracker.ledger;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LedgerRules {
    private static final Pattern DATE = Pattern.compile("(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})");

    private LedgerRules() {
    }

    static String normalizeKind(Object value) {
        String kind = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
        return switch (kind) {
            case "INCOME", "收入" -> "INCOME";
            case "TRANSFER", "TRANSFER_OUT", "TRANSFER_IN", "转账" -> "TRANSFER";
            case "BORROW_IN", "借入" -> "BORROW_IN";
            case "LEND_OUT", "借出" -> "LEND_OUT";
            case "COLLECT_DEBT", "收债" -> "COLLECT_DEBT";
            case "REPAY_DEBT", "还债" -> "REPAY_DEBT";
            default -> "EXPENSE";
        };
    }

    static String categoryKindForTransaction(Object value) {
        return switch (normalizeKind(value)) {
            case "INCOME", "BORROW_IN", "COLLECT_DEBT" -> "INCOME";
            case "EXPENSE", "LEND_OUT", "REPAY_DEBT" -> "EXPENSE";
            default -> null;
        };
    }

    static String normalizeImportCell(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static String normalizeImportDate(String value) {
        Matcher matcher = DATE.matcher(String.valueOf(value).trim());
        if (!matcher.find()) throw new IllegalArgumentException("日期格式不正确: " + value);
        String[] parts = matcher.group(1).replace('/', '-').split("-");
        return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])).toString();
    }
}
