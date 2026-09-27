package com.salarytracker.ai.tool.ledger;

enum LedgerManagementToolMode {
    BOOK_CREATE("ledger.book.create", "账本", "新增账本"),
    BOOK_UPDATE("ledger.book.update", "账本", "修改账本"),
    BOOK_DELETE("ledger.book.delete", "账本", "删除账本"),
    ACCOUNT_CREATE("ledger.account.create", "账户", "新增账户"),
    ACCOUNT_UPDATE("ledger.account.update", "账户", "修改账户"),
    ACCOUNT_DELETE("ledger.account.delete", "账户", "删除账户"),
    CATEGORY_CREATE("ledger.category.create", "分类", "新增分类"),
    CATEGORY_UPDATE("ledger.category.update", "分类", "修改分类"),
    CATEGORY_DELETE("ledger.category.delete", "分类", "删除分类");

    private final String actionType;
    private final String resourceLabel;
    private final String actionLabel;

    LedgerManagementToolMode(String actionType, String resourceLabel, String actionLabel) {
        this.actionType = actionType;
        this.resourceLabel = resourceLabel;
        this.actionLabel = actionLabel;
    }

    String actionType() { return actionType; }
    String resourceLabel() { return resourceLabel; }
    String actionLabel() { return actionLabel; }
    boolean create() { return name().endsWith("_CREATE"); }
    boolean update() { return name().endsWith("_UPDATE"); }
    boolean delete() { return name().endsWith("_DELETE"); }
    boolean book() { return name().startsWith("BOOK_"); }
    boolean account() { return name().startsWith("ACCOUNT_"); }
    boolean category() { return name().startsWith("CATEGORY_"); }
}
